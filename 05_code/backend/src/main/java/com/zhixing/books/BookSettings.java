package com.zhixing.books;

import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.AdminAuditMapper;
import com.zhixing.mapper.EnglishBookRepository;
import com.zhixing.service.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Book-only runtime settings; credential values are write-only and never appear in responses. */
@Service
public class BookSettings {
  static final String EMPTY = "__BOOK_EMPTY__";
  private final AppParameterService parameters;
  private final EnglishBookRepository persistence;
  private final AliyunTtsService tts;
  private final MediaService media;
  private final AdminAuditMapper audit;

  @Value("${app.books.worker-enabled:false}")
  private boolean workerDefault;

  @Value("${app.books.catalog-url:https://gutendex.com}")
  private String catalogDefault;

  @Value("${app.books.licensed-catalog-url:}")
  private String licensedDefault;

  @Value("${app.books.text-url-template:}")
  private String textDefault;

  @Value("${app.books.image-endpoint:}")
  private String endpointDefault;

  @Value("${app.books.image-model:}")
  private String modelDefault;

  @Value("${app.books.max-job-cost:30}")
  private String budgetDefault;

  public BookSettings(
      AppParameterService parameters,
      EnglishBookRepository persistence,
      AliyunTtsService tts,
      MediaService media,
      AdminAuditMapper audit) {
    this.parameters = parameters;
    this.persistence = persistence;
    this.tts = tts;
    this.media = media;
    this.audit = audit;
  }

  public static String value(AppParameterService parameters, String key, String fallback) {
    String v = parameters.optionalStored(key, fallback);
    return v == null ? fallback : EMPTY.equals(v) ? "" : v;
  }

  public Map<String, Object> view() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("revision", parameters.intValue("books.settings.version", 0, 0, Integer.MAX_VALUE));
    result.put(
        "workerEnabled",
        Boolean.parseBoolean(
            value(parameters, "books.worker.enabled", String.valueOf(workerDefault))));
    result.put("catalogUrl", value(parameters, "books.catalog.url", catalogDefault));
    result.put(
        "licensedCatalogUrl", value(parameters, "books.licensed_catalog.url", licensedDefault));
    result.put("textUrlTemplate", value(parameters, "books.source.text_url_template", textDefault));
    result.put("imageEndpoint", value(parameters, "books.image.endpoint", endpointDefault));
    result.put("imageModel", value(parameters, "books.image.model", modelDefault));
    result.put("maxJobCost", value(parameters, "books.max_job_cost", budgetDefault));
    result.put("imageUnitPrice", value(parameters, "books.image.unit_price", ""));
    result.put("ttsUnitPrice", value(parameters, "books.tts.price_per_thousand_calls", ""));
    result.put("imageKeyConfigured", parameters.configured("BOOK_IMAGE_API_KEY"));
    result.put("sourceKeyConfigured", parameters.configured("BOOK_SOURCE_API_KEY"));
    boolean database = persistence.bookMigrationReady();
    result.put("databaseReady", database);
    result.put("ttsReady", tts.configured());
    result.put("ossReady", media.bookStorageReady());
    result.put(
        "imageReady",
        !String.valueOf(result.get("imageEndpoint")).isEmpty()
            && !String.valueOf(result.get("imageModel")).isEmpty()
            && Boolean.TRUE.equals(result.get("imageKeyConfigured")));
    return result;
  }

  public static class Request {
    public Integer revision;
    public Boolean workerEnabled;
    public String catalogUrl,
        licensedCatalogUrl,
        textUrlTemplate,
        imageEndpoint,
        imageModel,
        maxJobCost,
        imageUnitPrice,
        ttsUnitPrice;
    public String imageApiKey, sourceApiKey;
  }

  @Transactional
  public Map<String, Object> save(Request request, String actor) {
    if (request == null || request.revision == null || request.workerEnabled == null)
      throw BookText.bad("配置版本和任务开关必填");
    Map<String, Object> before = view();
    Map<String, String> values = new LinkedHashMap<>();
    values.put("books.catalog.url", endpoint(request.catalogUrl, false));
    values.put("books.licensed_catalog.url", endpoint(request.licensedCatalogUrl, false));
    values.put("books.source.text_url_template", endpoint(request.textUrlTemplate, true));
    values.put("books.image.endpoint", endpoint(request.imageEndpoint, false));
    String model = clean(request.imageModel);
    if (model.length() > 160 || model.matches(".*[\\r\\n].*")) throw BookText.bad("图像模型名称过长或格式错误");
    values.put("books.image.model", model);
    values.put("books.max_job_cost", decimal(request.maxJobCost, false));
    values.put("books.image.unit_price", decimal(request.imageUnitPrice, true));
    values.put("books.tts.price_per_thousand_calls", decimal(request.ttsUnitPrice, true));
    validateSecret(request.imageApiKey);
    validateSecret(request.sourceApiKey);
    if (Boolean.TRUE.equals(request.workerEnabled)
        && !Boolean.TRUE.equals(before.get("databaseReady")))
      throw BookText.bad("书籍表或关键字段未就绪，请先完成 V3.30 迁移");
    if (Boolean.TRUE.equals(before.get("databaseReady")) && persistence.activeBookJobs() > 0) {
      boolean changed =
          !Objects.equals(before.get("catalogUrl"), values.get("books.catalog.url"))
              || !Objects.equals(
                  before.get("licensedCatalogUrl"), values.get("books.licensed_catalog.url"))
              || !Objects.equals(
                  before.get("textUrlTemplate"), values.get("books.source.text_url_template"))
              || !Objects.equals(before.get("imageEndpoint"), values.get("books.image.endpoint"))
              || !Objects.equals(before.get("imageModel"), values.get("books.image.model"))
              || !clean(request.imageApiKey).isEmpty()
              || !clean(request.sourceApiKey).isEmpty();
      if (changed) throw BookText.bad("存在排队或运行中的任务，请先暂停，再修改书源、图像接口或凭据");
    }
    changedHost(
        (String) before.get("imageEndpoint"),
        values.get("books.image.endpoint"),
        Boolean.TRUE.equals(before.get("imageKeyConfigured")),
        request.imageApiKey);
    changedHost(
        (String) before.get("licensedCatalogUrl"),
        values.get("books.licensed_catalog.url"),
        Boolean.TRUE.equals(before.get("sourceKeyConfigured")),
        request.sourceApiKey);
    // Ensure a durable row exists, then serialize updates across application instances.
    if (parameters.optionalStored("books.settings.version", null) == null)
      parameters.saveValue("books.settings.version", "0", "书籍配置版本");
    persistence.lockBookSettingsVersion();
    int revision = parameters.intValue("books.settings.version", 0, 0, Integer.MAX_VALUE);
    if (revision != request.revision)
      throw new com.zhixing.common.ApiException(
          org.springframework.http.HttpStatus.CONFLICT,
          "BOOK_SETTINGS_CONFLICT",
          "配置已被其他管理员更新，请刷新后重试");
    for (Map.Entry<String, String> entry : values.entrySet())
      parameters.saveValue(
          entry.getKey(), entry.getValue().isEmpty() ? EMPTY : entry.getValue(), "英语书籍运行配置");
    parameters.saveSecret("BOOK_IMAGE_API_KEY", request.imageApiKey);
    parameters.saveSecret("BOOK_SOURCE_API_KEY", request.sourceApiKey);
    parameters.saveValue("books.worker.enabled", request.workerEnabled.toString(), "英语书籍任务开关，实时生效");
    parameters.saveValue("books.settings.version", String.valueOf(revision + 1), "书籍配置版本");
    audit.insertAudit(
        CryptoUtils.randomId(),
        actor,
        "book_settings_update",
        "app_parameter",
        "books.settings",
        "success",
        "{\"workerEnabled\":"
            + request.workerEnabled
            + ",\"imageKeyUpdated\":"
            + !clean(request.imageApiKey).isEmpty()
            + ",\"sourceKeyUpdated\":"
            + !clean(request.sourceApiKey).isEmpty()
            + "}",
        Instant.now().plusSeconds(180L * 86400));
    return view();
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private void validateSecret(String value) {
    if (value != null && (value.length() > 8000 || value.contains("\n") || value.contains("\r")))
      throw BookText.bad("密钥格式错误");
  }

  private String decimal(String raw, boolean optional) {
    String value = clean(raw);
    if (optional && value.isEmpty()) return "";
    try {
      BigDecimal n = new BigDecimal(value);
      if (n.signum() <= 0 || n.compareTo(new BigDecimal("1000000")) > 0 || n.scale() > 8)
        throw new IllegalArgumentException();
      return n.stripTrailingZeros().toPlainString();
    } catch (Exception e) {
      throw BookText.bad("预算和单价需为正数，最多 8 位小数，最高 1000000 元");
    }
  }

  private String endpoint(String raw, boolean template) {
    String value = clean(raw);
    if (value.isEmpty()) return "";
    if (value.length() > 2048 || value.contains("\n") || value.contains("\r"))
      throw BookText.bad("接口地址格式错误");
    if (template && !value.contains("{id}")) throw BookText.bad("全文下载模板必须包含 {id}");
    try {
      URI uri = new URI(template ? value.replace("{id}", "1") : value);
      String host = uri.getHost();
      if (!"https".equals(uri.getScheme())
          || host == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null
          || uri.getQuery() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)
          || host.equalsIgnoreCase("localhost")
          || host.matches(
              "(?i)(127\\..*|10\\..*|192\\.168\\..*|169\\.254\\..*|0\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*)")
          || host.contains(":")) throw new IllegalArgumentException();
      if (template
          && (host.equalsIgnoreCase("gutenberg.org") || host.equalsIgnoreCase("www.gutenberg.org")))
        throw BookText.bad("请配置允许自动下载的镜像或授权渠道，不自动抓取主站");
      return value;
    } catch (com.zhixing.common.ApiException e) {
      throw e;
    } catch (Exception e) {
      throw BookText.bad("接口需使用无凭据、无查询参数的公网 HTTPS 地址");
    }
  }

  private void changedHost(String old, String updated, boolean hasKey, String replacement) {
    if (!hasKey || clean(replacement).length() > 0 || updated.isEmpty()) return;
    try {
      if (!Objects.equals(new URI(old).getHost(), new URI(updated).getHost()))
        throw BookText.bad("更换服务域名时必须同时填写新密钥，避免将已有密钥发送给其他服务");
    } catch (java.net.URISyntaxException e) {
      throw BookText.bad("原配置地址无效，请同时更新密钥");
    }
  }
}
