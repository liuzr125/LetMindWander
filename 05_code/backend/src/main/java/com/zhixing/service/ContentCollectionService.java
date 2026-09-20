package com.zhixing.service;

import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.mapper.ContentCollectionMapper;
import com.zhixing.model.CollectionRunView;
import com.zhixing.model.CollectionScheduleView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.w3c.dom.*;

/** Collects only the four fixed, public official feeds defined in the SQL migration.
 * Feed entries stay pending until an administrator reviews them; collection never
 * republishes third-party text automatically. */
@Service
public class ContentCollectionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SCHEDULE_ID = "00000000000000000000000000000090";
    private static final String LICENSE = "仅保存官方 RSS 的标题与受限摘要；原文版权归来源方，需经管理员审核后才能发布。";
    private static final List<FeedSource> SOURCES = Arrays.asList(
            new FeedSource("00000000000000000000000000000081", "https://kubernetes.io/feed.xml", "kubernetes.io", "Kubernetes", "kubernetes"),
            new FeedSource("00000000000000000000000000000082", "https://www.docker.com/feed/", "www.docker.com", "Docker/容器", "docker/容器"),
            new FeedSource("00000000000000000000000000000083", "https://openai.com/news/rss.xml", "openai.com", "Agent/Sandbox", "agent/sandbox"),
            new FeedSource("00000000000000000000000000000084", "https://spring.io/blog.atom", "spring.io", "Java", "java")
    );
    private final ContentCollectionMapper mapper;
    private final AppProperties properties;

    public ContentCollectionService(ContentCollectionMapper mapper, AppProperties properties) { this.mapper = mapper; this.properties = properties; }

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Shanghai")
    public void scheduledCollection() { run(false); }

    public CollectionScheduleView schedule() {
        CollectionScheduleView schedule = mapper.selectSchedule();
        if (schedule != null) schedule.setNextRunAt(nextRun(Instant.now()));
        return schedule;
    }

    public List<CollectionRunView> runs(int limit) {
        CollectionScheduleView schedule = mapper.selectSchedule();
        return schedule == null ? Collections.<CollectionRunView>emptyList() : mapper.selectRuns(schedule.getId(), Math.max(1, Math.min(limit, 50)));
    }

    /** Details are deliberately generated from the implemented job contract, rather than free-form operator notes. */
    public Map<String,Object> runDetail(String runId) {
        CollectionScheduleView schedule = mapper.selectSchedule();
        if (schedule == null) throw new IllegalStateException("未找到采集任务；请先执行 V3.3_content_collection_schedule.sql");
        CollectionRunView run = mapper.selectRun(runId, schedule.getId());
        if (run == null) throw new IllegalArgumentException("未找到该运行记录");
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("run", run);
        result.put("taskCode", "official_technical_content_collection");
        result.put("sqlScript", "backend/sql/V3.3_content_collection_schedule.sql");
        result.put("schedule", mapOf("cronExpression", schedule.getCronExpression(), "timezone", schedule.getTimezone(), "perRunLimit", schedule.getPerRunLimit()));
        result.put("tables", Arrays.asList("content_collection_schedule（任务配置）", "content_collection_run（本次运行账本）", "content_source（官方来源白名单）", "learning_content / content_version（待审核内容）", "learning_topic / content_topic（主题关联）"));
        result.put("sqlOperations", Arrays.asList(
                "INSERT content_collection_run：创建本次运行账本；(schedule_id, trigger_key) 唯一索引防止同日定时任务重复执行。",
                "SELECT COUNT(*) FROM learning_content WHERE dedup_hash=UNHEX(?)：以来源、链接、标题与摘要的 SHA-256 指纹查重。",
                "INSERT learning_content、content_version：仅把新条目写为 pending / 待审核，不自动发布。",
                "INSERT learning_topic、content_topic：补齐主题与内容版本的关联；重复关联由唯一约束保护。"
        ));
        result.put("tools", Arrays.asList("Spring @Scheduled（Asia/Shanghai 的 Cron 调度）", "JDK HttpURLConnection（HTTPS 请求；8 秒连接、12 秒读取超时）", "JDK XML DOM 解析（启用安全处理并禁用 DTD/外部实体）", "MyBatis + MySQL InnoDB（运行账本、唯一键与事务性写入）", "SHA-256（内容去重与链接指纹）"));
        result.put("steps", Arrays.asList("按 Cron 或手动按钮生成 trigger_key，并先写入 running 运行账本。", "只访问 Kubernetes、Docker、OpenAI、Spring 的精确 HTTPS 白名单域名；不跟随重定向。", "解析 RSS/Atom，剔除没有标题或链接、或跳转到非白名单主机的条目；每来源最多处理配置上限。", "为每条合格数据计算 SHA-256 去重指纹；重复项计入“去重跳过”。", "新条目保存标题、受限摘要、来源链接和许可快照，状态固定为 pending，等待管理员审核。", "写回成功/部分完成/失败、各项计数、错误信息和完成时间，并计算下一次执行时间。"));
        result.put("principles", Arrays.asList("幂等：定时任务用 daily:日期 作为触发键，数据库唯一索引阻止同一天重复落库。", "最小采集：不抓取任意 URL，不保存整篇第三方正文；只保存审核所需的受限字段。", "安全：HTTPS + 精确主机校验 + 禁止重定向 + XXE 防护，降低 SSRF 与 XML 实体攻击风险。", "可恢复：单个来源失败不会阻断其他来源；有新增数据时标记为“部分完成”，并记录错误。", "人工发布：采集与发布解耦，任何新数据都必须经管理员审核。"));
        return result;
    }

    public CollectionRunView runManually() { return run(true); }

    private CollectionRunView run(boolean manual) {
        CollectionScheduleView schedule = mapper.selectSchedule();
        if (schedule == null) throw new IllegalStateException("未找到采集任务；请先执行 V3.3_content_collection_schedule.sql");
        if (!"active".equals(schedule.getState())) throw new IllegalStateException("采集任务当前未启用");
        Instant started = Instant.now();
        String triggerKey = manual ? "manual:" + CryptoUtils.randomId() : "daily:" + LocalDate.now(BUSINESS_ZONE);
        String runId = CryptoUtils.randomId();
        try { mapper.insertRun(runId, schedule.getId(), triggerKey, started); }
        catch (DuplicateKeyException duplicate) { return latestByTrigger(schedule.getId(), triggerKey); }
        int fetched = 0, inserted = 0, skipped = 0; String failure = null;
        try {
            for (FeedSource source : SOURCES) {
                List<FeedItem> items;
                try { items = fetch(source, schedule.getPerRunLimit()); }
                catch (Exception exception) { failure = shorten(exception.getMessage()); continue; }
                fetched += items.size();
                for (FeedItem item : items) {
                    String dedup = hex(source.id + "\n" + item.url + "\n" + item.title + "\n" + item.summary);
                    if (mapper.countContentByDedup(dedup) > 0) { skipped++; continue; }
                    try { persist(source, item, dedup); inserted++; }
                    catch (DuplicateKeyException duplicate) { skipped++; }
                }
            }
            String state = failure == null ? "success" : (inserted > 0 ? "partial" : "failed");
            mapper.finishRun(runId, state, fetched, inserted, skipped, failure == null ? null : "FEED_FETCH_FAILED", failure, Instant.now());
        } catch (Exception exception) {
            failure = shorten(exception.getMessage());
            mapper.finishRun(runId, "failed", fetched, inserted, skipped, "COLLECTION_FAILED", failure, Instant.now());
        } finally { mapper.updateScheduleTimes(schedule.getId(), started, nextRun(Instant.now())); }
        return findRun(runId, schedule.getId());
    }

    private void persist(FeedSource source, FeedItem item, String dedup) {
        String contentId = CryptoUtils.randomId(), versionId = CryptoUtils.randomId();
        String title = crop(item.title, 100);
        String summary = crop(item.summary, 500);
        String body = "【采集来源】" + source.url + "\n\n【原始摘要】" + crop(item.summary, 1200) +
                "\n\n【阅读指引】此条目由每日定时任务从官方 RSS 获取，当前仅作为待审核学习素材入库。审核时请核对链接可访问性、发布时间、许可说明与技术准确性；确认无误后再补充中文学习解读并发布。\n\n【建议核验】1. 阅读原文而非只依据摘要；2. 区分公告、教程与观点文章；3. 记录适用版本和前提；4. 对可能影响生产环境的操作先在隔离环境验证。";
        String urlHash = hex(item.url);
        mapper.ensureTopic(topicId(source.normalizedTopic), source.topic, source.normalizedTopic);
        mapper.insertPendingContent(contentId, source.id, dedup, urlHash, versionId);
        mapper.insertPendingVersion(versionId, contentId, title, summary, body, "[\"" + source.topic + "\",\"官方RSS\",\"待审核\"]",
                item.url, source.name, item.publishedAt, LICENSE, hex(body), properties.getAdminPrincipalId());
        mapper.insertContentTopic(CryptoUtils.randomId(), versionId, source.normalizedTopic);
    }

    private List<FeedItem> fetch(FeedSource source, int limit) throws Exception {
        URI uri = URI.create(source.url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !source.host.equalsIgnoreCase(uri.getHost())) throw new IllegalArgumentException("来源不在允许列表");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(8000); connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "LetMindWanderContentCollector/1.0 (+official-feed-only)");
        connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml");
        if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
        try (InputStream input = connection.getInputStream()) {
            List<FeedItem> parsed = parse(input, limit);
            List<FeedItem> trusted = new ArrayList<FeedItem>();
            for (FeedItem item : parsed) if (source.host.equalsIgnoreCase(URI.create(item.url).getHost())) trusted.add(item);
            return trusted;
        }
        finally { connection.disconnect(); }
    }

    private List<FeedItem> parse(InputStream input, int limit) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(input);
        NodeList nodes = document.getElementsByTagName("item");
        boolean atom = nodes.getLength() == 0; if (atom) nodes = document.getElementsByTagNameNS("*", "entry");
        List<FeedItem> result = new ArrayList<FeedItem>();
        for (int i = 0; i < nodes.getLength() && result.size() < limit; i++) {
            Element node = (Element) nodes.item(i);
            String title = child(node, "title");
            String url = atom ? atomLink(node) : first(child(node, "link"), child(node, "guid"));
            String summary = first(child(node, atom ? "summary" : "description"), child(node, atom ? "content" : "encoded"));
            if (blank(title) || blank(url)) continue;
            result.add(new FeedItem(clean(title), clean(summary), canonical(url), published(node, atom)));
        }
        return result;
    }

    private String child(Element parent, String name) {
        NodeList descendants = parent.getElementsByTagNameNS("*", name);
        if (descendants.getLength() == 0) descendants = parent.getElementsByTagName(name);
        return descendants.getLength() == 0 ? "" : descendants.item(0).getTextContent();
    }
    private String atomLink(Element parent) { NodeList links = parent.getElementsByTagNameNS("*", "link"); for (int i=0;i<links.getLength();i++) { Element link=(Element)links.item(i); if (!link.hasAttribute("rel") || "alternate".equals(link.getAttribute("rel"))) return link.getAttribute("href"); } return ""; }
    private Instant published(Element parent, boolean atom) { String raw=first(child(parent, atom?"published":"pubDate"),child(parent, atom?"updated":"date")); try { return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); } catch(Exception ignored) {} try { return OffsetDateTime.parse(raw).toInstant(); } catch(Exception ignored) {} return null; }
    private String canonical(String url) { URI uri=URI.create(url.trim()); if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("条目不是 HTTPS URL"); return uri.normalize().toString(); }
    private String clean(String text) { String value=text==null?"":text.replaceAll("(?is)<[^>]*>"," ").replaceAll("\\s+"," ").trim(); return value.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'"); }
    private String first(String a,String b) { return blank(a)?b:a; } private boolean blank(String value){return value==null||value.trim().isEmpty();}
    private String crop(String value,int max){String safe=blank(value)?"该来源未提供摘要，请打开原文核验。":value.trim();return safe.length()<=max?safe:safe.substring(0,max-1)+"…";}
    private String shorten(String value){return crop(value==null?"未知错误":value,400);}
    private String hex(String value) { try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(64); for(byte b:bytes)out.append(String.format("%02x",b)); return out.toString(); } catch(Exception exception){throw new IllegalStateException(exception);} }
    private String topicId(String topic){return hex("content-collection-topic:"+topic).substring(0,32);}
    private Instant nextRun(Instant from){ZonedDateTime now=from.atZone(BUSINESS_ZONE); ZonedDateTime next=now.withHour(22).withMinute(0).withSecond(0).withNano(0); if(!next.isAfter(now))next=next.plusDays(1);return next.toInstant();}
    private CollectionRunView latestByTrigger(String scheduleId,String trigger){for(CollectionRunView run:mapper.selectRuns(scheduleId,50))if(trigger.equals(run.getTriggerKey()))return run;throw new IllegalStateException("运行记录创建失败");}
    private CollectionRunView findRun(String id,String scheduleId){CollectionRunView run=mapper.selectRun(id,scheduleId);if(run!=null)return run;throw new IllegalStateException("运行记录不存在");}
    private Map<String,Object> mapOf(Object... values){Map<String,Object> result=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)result.put(String.valueOf(values[i]),values[i+1]);return result;}
    private static final class FeedSource { final String id,url,host,topic,normalizedTopic,name; FeedSource(String id,String url,String host,String topic,String normalizedTopic){this.id=id;this.url=url;this.host=host;this.topic=topic;this.normalizedTopic=normalizedTopic;this.name=topic+" 官方RSS";} }
    private static final class FeedItem { final String title,summary,url; final Instant publishedAt; FeedItem(String title,String summary,String url,Instant publishedAt){this.title=title;this.summary=summary;this.url=url;this.publishedAt=publishedAt;} }
}
