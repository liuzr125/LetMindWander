package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek 官网价格同步。网页结构或价格字段不完整时整次拒绝更新，继续使用最后一个有效价格版本。
 */
@Service
public class AiOfficialPricingService {
    static final String SOURCE_URL = "https://api-docs.deepseek.com/zh-cn/quick_start/pricing/";
    private static final Logger LOGGER = LoggerFactory.getLogger(AiOfficialPricingService.class);
    private static final Pattern ROW = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    private static final Pattern MONEY = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");
    private static final String STATUS_PREFIX = "ai.pricing.deepseek.";

    private final RestTemplate http;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AppParameterService parameters;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    public AiOfficialPricingService(RestTemplate http, JdbcTemplate jdbc, ObjectMapper json,
                                    AppParameterService parameters, AppProperties properties,
                                    PlatformTransactionManager transactionManager) {
        this.http = http;
        this.jdbc = jdbc;
        this.json = json;
        this.parameters = parameters;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.ai.pricing-sync-cron:0 0 22 * * *}", zone = "Asia/Shanghai")
    public void scheduledRefresh() {
        if (!properties.getAi().isPricingSyncEnabled()) return;
        try {
            RefreshResult result = refresh();
            LOGGER.info("DeepSeek official pricing checked: changedModels={}", result.changedModels);
        } catch (RuntimeException exception) {
            recordStatus("failed", exception.getClass().getSimpleName());
            LOGGER.warn("DeepSeek official pricing refresh failed; keeping last verified version", exception);
        }
    }

    /**
     * 笔记本在 22:00 休眠会错过 cron；MySQL 后端每次启动后补做一次核验。
     * H2 仅用于本地演示和自动测试，跳过外网访问以保证测试可重复。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshAfterStartup() {
        if (!properties.getAi().isPricingSyncEnabled() || isH2()) return;
        scheduledRefresh();
    }

    public synchronized RefreshResult refresh() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "LetMindWander-PricingSync/1.0");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        // 官网目前返回 text/html 但不声明 charset。RestTemplate 将 String 默认按
        // ISO-8859-1 解码会把“模型/价格”等中文表头变成乱码，因此必须按原始字节读取 UTF-8。
        ResponseEntity<byte[]> response = http.exchange(SOURCE_URL, HttpMethod.GET,
                new HttpEntity<Void>(headers), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("official pricing page unavailable");
        }
        ParsedPage page = parseOfficialPage(new String(response.getBody(), StandardCharsets.UTF_8));
        Integer changed = transactions.execute(status -> persist(page));
        int changedModels = changed == null ? 0 : changed;
        recordStatus("success", "changed=" + changedModels);
        return new RefreshResult(changedModels, page.models.size());
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", properties.getAi().isPricingSyncEnabled());
        result.put("source", SOURCE_URL);
        result.put("schedule", "每天 22:00");
        result.put("timezone", "Asia/Shanghai");
        result.put("lastCheckedAt", parameters.optional(STATUS_PREFIX + "last_checked_at", null));
        result.put("lastStatus", parameters.optional(STATUS_PREFIX + "last_status", "not_run"));
        result.put("lastMessage", parameters.optional(STATUS_PREFIX + "last_message", null));
        return result;
    }

    private int persist(ParsedPage page) {
        int changed = 0;
        for (OfficialModelPrice price : page.models.values()) {
            List<Map<String, Object>> latest = jdbc.queryForList(
                    "SELECT pricing_json FROM ai_model_price WHERE provider_code='deepseek' AND model_code=? " +
                            "ORDER BY effective_at DESC,version_no DESC LIMIT 1", price.modelCode);
            if (!latest.isEmpty() && price.fingerprint().equals(fingerprint(value(latest.get(0), "pricing_json")))) {
                continue;
            }
            Integer max = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(version_no),0) FROM ai_model_price WHERE provider_code='deepseek' AND model_code=?",
                    Integer.class, price.modelCode);
            int version = (max == null ? 0 : max) + 1;
            jdbc.update("INSERT INTO ai_model_price(id,provider_code,model_code,version_no,currency,input_per_million," +
                            "output_per_million,pricing_json,effective_at) VALUES(?,'deepseek',?,?,'CNY',?,?,?,CURRENT_TIMESTAMP)",
                    CryptoUtils.randomId(), price.modelCode, version, price.peak.inputCacheMiss,
                    price.peak.output, write(price.toJson(Instant.now())));
            changed++;
        }
        return changed;
    }

    private void recordStatus(String status, String message) {
        parameters.saveValue(STATUS_PREFIX + "last_checked_at", Instant.now().toString(), "DeepSeek price sync check time");
        parameters.saveValue(STATUS_PREFIX + "last_status", status, "DeepSeek price sync status");
        parameters.saveValue(STATUS_PREFIX + "last_message", message, "DeepSeek price sync message");
    }

    private boolean isH2() {
        if (jdbc.getDataSource() == null) return false;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            LOGGER.warn("Could not identify database while deciding startup pricing sync", exception);
            return false;
        }
    }

    static ParsedPage parseOfficialPage(String html) {
        if (html == null || html.trim().isEmpty()) throw new IllegalArgumentException("empty pricing page");
        List<List<String>> rows = new ArrayList<List<String>>();
        Matcher rowMatcher = ROW.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<String>();
            Matcher cellMatcher = CELL.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) cells.add(text(cellMatcher.group(1)));
            if (!cells.isEmpty()) rows.add(cells);
        }

        List<String> modelCodes = null;
        List<String> modelVersions = null;
        for (List<String> row : rows) {
            if (!row.isEmpty() && "模型".equals(row.get(0)) && row.size() >= 2) {
                modelCodes = row.subList(1, row.size());
            }
            if (!row.isEmpty() && "模型版本".equals(row.get(0)) && row.size() >= 2) {
                modelVersions = row.subList(1, row.size());
            }
        }
        if (modelCodes == null || modelVersions == null || modelCodes.size() != modelVersions.size()) {
            throw new IllegalArgumentException("model columns missing from official page");
        }

        Map<String, OfficialModelPrice> models = new LinkedHashMap<String, OfficialModelPrice>();
        for (int i = 0; i < modelCodes.size(); i++) {
            String code = modelCodes.get(i).replaceAll("\\([0-9]+\\)$", "").trim();
            if (!code.matches("deepseek-[a-z0-9-]+")) throw new IllegalArgumentException("unexpected model code");
            models.put(code, new OfficialModelPrice(code, modelVersions.get(i)));
        }

        String metric = null;
        for (List<String> row : rows) {
            for (String cell : row) {
                if (cell.contains("缓存命中")) metric = "inputCacheHit";
                else if (cell.contains("缓存未命中")) metric = "inputCacheMiss";
                else if (cell.contains("百万tokens输出")) metric = "output";
            }
            int periodIndex = row.indexOf("空闲时段");
            boolean peak = false;
            if (periodIndex < 0) { periodIndex = row.indexOf("高峰时段"); peak = periodIndex >= 0; }
            if (periodIndex < 0 || metric == null || row.size() < periodIndex + 1 + models.size()) continue;
            int modelIndex = 0;
            for (OfficialModelPrice price : models.values()) {
                BigDecimal amount = money(row.get(periodIndex + 1 + modelIndex));
                price.set(metric, peak, amount);
                modelIndex++;
            }
        }

        String pageText = text(html);
        if (!pageText.contains("周一至周五") || !pageText.contains("9:00 - 12:00") || !pageText.contains("14:00 - 18:00")) {
            throw new IllegalArgumentException("official peak schedule changed");
        }
        for (OfficialModelPrice price : models.values()) price.validate();
        return new ParsedPage(models);
    }

    private static BigDecimal money(String value) {
        Matcher matcher = MONEY.matcher(value);
        if (!matcher.find()) throw new IllegalArgumentException("invalid official price");
        return new BigDecimal(matcher.group(1));
    }

    private static String text(String html) {
        return html.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&amp;", "&").replace("&nbsp;", " ")
                .replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private String fingerprint(Object raw) {
        if (raw == null) return null;
        try {
            Map<String, Object> value = json.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
            Object fingerprint = value.get("fingerprint");
            return fingerprint == null ? null : String.valueOf(fingerprint);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("cannot serialize official pricing", exception); }
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? row.get(key.toUpperCase(Locale.ROOT)) : value;
    }

    static final class ParsedPage {
        final Map<String, OfficialModelPrice> models;
        ParsedPage(Map<String, OfficialModelPrice> models) { this.models = models; }
    }

    static final class OfficialModelPrice {
        final String modelCode;
        final String modelVersion;
        final Rates offPeak = new Rates();
        final Rates peak = new Rates();

        OfficialModelPrice(String modelCode, String modelVersion) {
            this.modelCode = modelCode;
            this.modelVersion = modelVersion;
        }

        void set(String metric, boolean peakPeriod, BigDecimal value) {
            Rates target = peakPeriod ? peak : offPeak;
            if ("inputCacheHit".equals(metric)) target.inputCacheHit = value;
            if ("inputCacheMiss".equals(metric)) target.inputCacheMiss = value;
            if ("output".equals(metric)) target.output = value;
        }

        void validate() {
            offPeak.validate(); peak.validate();
            if (offPeak.inputCacheHit.compareTo(peak.inputCacheHit) > 0 ||
                    offPeak.inputCacheMiss.compareTo(peak.inputCacheMiss) > 0 ||
                    offPeak.output.compareTo(peak.output) > 0) {
                throw new IllegalArgumentException("off-peak price exceeds peak price");
            }
        }

        String fingerprint() {
            return String.join("|", Arrays.asList(modelCode, modelVersion,
                    offPeak.inputCacheHit.toPlainString(), offPeak.inputCacheMiss.toPlainString(), offPeak.output.toPlainString(),
                    peak.inputCacheHit.toPlainString(), peak.inputCacheMiss.toPlainString(), peak.output.toPlainString()));
        }

        Map<String, Object> toJson(Instant fetchedAt) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("schema", "deepseek-official-v1");
            value.put("source", SOURCE_URL);
            value.put("verified", true);
            value.put("currency", "CNY");
            value.put("unit", "per_million_tokens");
            value.put("modelVersion", modelVersion);
            value.put("timezone", "Asia/Shanghai");
            value.put("peakWindows", Arrays.asList(window("MON-FRI", "09:00", "12:00"), window("MON-FRI", "14:00", "18:00")));
            value.put("offPeak", offPeak.toJson());
            value.put("peak", peak.toJson());
            value.put("fingerprint", fingerprint());
            value.put("fetchedAt", fetchedAt.toString());
            return value;
        }

        private Map<String, Object> window(String days, String start, String end) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("days", days); value.put("start", start); value.put("end", end); return value;
        }
    }

    static final class Rates {
        BigDecimal inputCacheHit;
        BigDecimal inputCacheMiss;
        BigDecimal output;
        void validate() {
            if (inputCacheHit == null || inputCacheMiss == null || output == null ||
                    inputCacheHit.signum() < 0 || inputCacheMiss.signum() <= 0 || output.signum() <= 0) {
                throw new IllegalArgumentException("official price matrix incomplete");
            }
        }
        Map<String, Object> toJson() {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("inputCacheHit", inputCacheHit);
            value.put("inputCacheMiss", inputCacheMiss);
            value.put("output", output);
            return value;
        }
    }

    public static final class RefreshResult {
        public final int changedModels;
        public final int checkedModels;
        RefreshResult(int changedModels, int checkedModels) {
            this.changedModels = changedModels;
            this.checkedModels = checkedModels;
        }
    }
}
