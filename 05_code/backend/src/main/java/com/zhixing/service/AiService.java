package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.AiAskRequest;
import com.zhixing.dto.AiModelSaveRequest;
import com.zhixing.mapper.AiPersistenceMapper;
import com.zhixing.model.AiGatewayResult;
import com.zhixing.model.AiRuntimeModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiService {
  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AiService.class);
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private static final String GLOBAL_SCOPE = "00000000000000000000000000000000";
  private static final String SYSTEM_SCOPE = "ffffffffffffffffffffffffffffffff";
  private static final Pattern CODE = Pattern.compile("[A-Za-z0-9._:-]{1,120}");
  private final AiPersistenceMapper persistence;
  private final ObjectMapper json;
  private final AppProperties properties;
  private final AppParameterService parameters;
  private final AiGateway gateway;
  private final TransactionTemplate transactions;

  public AiService(
      AiPersistenceMapper persistence,
      ObjectMapper json,
      AppProperties properties,
      AppParameterService parameters,
      AiGateway gateway,
      PlatformTransactionManager transactionManager) {
    this.persistence = persistence;
    this.json = json;
    this.properties = properties;
    this.parameters = parameters;
    this.gateway = gateway;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public Map<String, Object> models(String ownerId) {
    String consent = persistence.selectConsentVersion(ownerId);
    LocalDate month = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
    boolean budget = persistence.countMonthBudgets(month) > 0;
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : persistence.selectDefaultModel()) {
      boolean credential = parameters.configured(string(row, "api_key_param_key"));
      BigDecimal input = decimal(row, "input_per_million"),
          output = decimal(row, "output_per_million");
      boolean price = input.signum() > 0 || output.signum() > 0;
      Map<String, Object> item = modelView(row);
      item.remove("baseUrl");
      item.remove("apiKeyParamKey");
      item.put("available", credential && price && budget);
      item.put("credentialConfigured", credential);
      item.put("priceConfigured", price);
      item.put("budgetConfigured", budget);
      items.add(item);
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("consentGranted", consent != null && !consent.trim().isEmpty());
    result.put("consentVersion", properties.getAi().getConsentVersion());
    result.put("models", items);
    return result;
  }

  public Map<String, Object> ask(String ownerId, AiAskRequest request, String requestKey) {
    String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
    Reservation reservation =
        transactions.execute(status -> reserve(ownerId, null, question, requestKey));
    try {
      AiGatewayResult answer = gateway.ask(reservation.model, reservation.apiKey, question);
      return transactions.execute(status -> settleSuccess(reservation, answer));
    } catch (ApiException exception) {
      transactions.executeWithoutResult(status -> settleFailure(reservation, exception.getCode()));
      throw exception;
    } catch (RuntimeException exception) {
      transactions.executeWithoutResult(status -> settleFailure(reservation, "AI_PROVIDER_ERROR"));
      throw exception;
    }
  }

  /** 系统内容生成不借用某个用户的 AI 同意，但仍必须通过全局配额、并发与月度预算门禁。 */
  public String generateSystemContent(String systemPrompt, String prompt, String requestKey) {
    return generateSystemContent(systemPrompt, prompt, requestKey, null);
  }

  /** sourceId 关联本次定时任务运行；模型仍以实际 ai_attempt 的价格快照为准。 */
  public String generateSystemContent(
      String systemPrompt, String prompt, String requestKey, String sourceId) {
    return generateSystemContent(
        systemPrompt,
        prompt,
        requestKey,
        sourceId,
        "generate_english_articles",
        "article_generation");
  }

  public String generateSystemContent(
      String systemPrompt,
      String prompt,
      String requestKey,
      String sourceId,
      String actionCode,
      String sourceType) {
    Reservation reservation =
        transactions.execute(
            status -> reserveSystem(prompt, requestKey, sourceId, actionCode, sourceType));
    try {
      AiGatewayResult answer =
          gateway.askStructured(reservation.model, reservation.apiKey, systemPrompt, prompt);
      transactions.execute(status -> settleSuccess(reservation, answer));
      return answer.answer;
    } catch (ApiException exception) {
      transactions.executeWithoutResult(status -> settleFailure(reservation, exception.getCode()));
      throw exception;
    } catch (RuntimeException exception) {
      transactions.executeWithoutResult(status -> settleFailure(reservation, "AI_PROVIDER_ERROR"));
      throw exception;
    }
  }

  /**
   * Article help is system content, not a user's Ask question; no personal AI authorization is
   * required.
   */
  public String generateSystemText(
      String systemPrompt,
      String prompt,
      String requestKey,
      String sourceId,
      String actionCode,
      String sourceType) {
    Reservation reservation =
        transactions.execute(
            status -> reserveSystem(prompt, requestKey, sourceId, actionCode, sourceType));
    try {
      AiGatewayResult answer =
          gateway.ask(reservation.model, reservation.apiKey, systemPrompt, prompt);
      transactions.execute(status -> settleSuccess(reservation, answer));
      return answer.answer;
    } catch (ApiException exception) {
      transactions.executeWithoutResult(status -> settleFailure(reservation, exception.getCode()));
      throw exception;
    } catch (RuntimeException exception) {
      transactions.executeWithoutResult(status -> settleFailure(reservation, "AI_PROVIDER_ERROR"));
      throw exception;
    }
  }

  public Map<String, Object> history(String ownerId, int size) {
    int safe = Math.min(Math.max(size, 1), 50);
    List<Map<String, Object>> rows = persistence.selectQuestionHistory(ownerId, safe);
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : rows) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", value(row, "id"));
      item.put("question", value(row, "input_text"));
      item.put("state", value(row, "state"));
      item.put("errorCode", value(row, "error_code"));
      item.put("createdAt", value(row, "created_at"));
      item.put("inputTokens", value(row, "input_tokens"));
      item.put("outputTokens", value(row, "output_tokens"));
      item.put("cost", value(row, "settled_amount"));
      item.put("currency", value(row, "currency"));
      item.put("modelCode", value(row, "model_code"));
      item.put("modelName", value(row, "display_name"));
      item.put("specification", value(row, "specification"));
      String output = string(row, "output_json");
      if (output != null && !output.isEmpty())
        try {
          item.put("answer", json.readTree(output).path("answer").asText());
        } catch (Exception ignored) {
        }
      items.add(item);
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("items", items);
    return result;
  }

  public List<Map<String, Object>> adminModels() {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : persistence.selectAdminModels()) {
      Map<String, Object> item = modelView(row);
      item.put("enabled", number(row, "enabled") == 1);
      item.put("credentialConfigured", parameters.configured(string(row, "api_key_param_key")));
      result.add(item);
    }
    return result;
  }

  @Transactional
  public Map<String, Object> saveModel(String id, AiModelSaveRequest request) {
    validateModel(request);
    boolean create = id == null || id.trim().isEmpty();
    String modelId = create ? CryptoUtils.randomId() : id;
    if (request.isDefaultModel()) persistence.clearDefaultModel();
    if (create) {
      try {
        persistence.insertAiModelConfig(
            modelId,
            request.getProviderCode().trim(),
            request.getModelCode().trim(),
            request.getDisplayName().trim(),
            request.getSpecification().trim().toLowerCase(),
            request.getBaseUrl().trim(),
            request.getApiKeyParamKey().trim(),
            request.isEnabled() ? 1 : 0,
            request.isDefaultModel() ? 1 : 0,
            request.getMaxOutputTokens(),
            request.getTimeoutSeconds());
      } catch (DuplicateKeyException e) {
        throw conflict("AI_MODEL_EXISTS", "该供应商和模型代码已存在");
      }
    } else {
      if (request.getExpectedVersion() == null)
        throw bad("AI_MODEL_VERSION_REQUIRED", "缺少模型版本，请刷新后重试");
      int changed =
          persistence.updateModelAtVersion(
              request.getProviderCode().trim(),
              request.getModelCode().trim(),
              request.getDisplayName().trim(),
              request.getSpecification().trim().toLowerCase(),
              request.getBaseUrl().trim(),
              request.getApiKeyParamKey().trim(),
              request.isEnabled() ? 1 : 0,
              request.isDefaultModel() ? 1 : 0,
              request.getMaxOutputTokens(),
              request.getTimeoutSeconds(),
              modelId,
              request.getExpectedVersion());
      if (changed != 1) throw conflict("AI_MODEL_VERSION_CONFLICT", "模型配置已变化，请刷新后重试");
    }
    if (request.isEnabled() && persistence.countEnabledDefaults() == 0)
      persistence.markModelDefault(modelId);
    if (!request.isEnabled() && persistence.countEnabledDefaults() == 0)
      persistence.chooseEnabledDefault();
    if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty())
      parameters.saveSecret(request.getApiKeyParamKey().trim(), request.getApiKey());
    if (!officialPriceManaged(request.getProviderCode())) {
      int priceVersion =
          number(
                  persistence.selectMaximumPriceVersion(
                      request.getProviderCode().trim(), request.getModelCode().trim()),
                  "version_no")
              + 1;
      persistence.insertAiModelPrice(
          CryptoUtils.randomId(),
          request.getProviderCode().trim(),
          request.getModelCode().trim(),
          priceVersion,
          request.getCurrency().trim().toUpperCase(),
          request.getInputPerMillion(),
          request.getOutputPerMillion(),
          "{\"source\":\"admin\",\"verified\":true}");
    }
    audit(
        "config",
        "ai_model_config",
        modelId,
        "{\"fields\":[\"model\",\"price\",\"credential_status\"]}");
    for (Map<String, Object> item : adminModels()) if (modelId.equals(item.get("id"))) return item;
    throw new IllegalStateException("Saved AI model not found");
  }

  public Map<String, Object> adminUsage(int size) {
    int safe = Math.min(Math.max(size, 1), 200);
    LocalDate month = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
    List<Map<String, Object>> rawLogs = persistence.selectUsageLogs(safe);
    List<Map<String, Object>> logs = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> raw : rawLogs) {
      Map<String, Object> row = new LinkedHashMap<String, Object>(raw);
      Map<String, Object> detail = readJsonMap(string(raw, "usage_json"));
      row.remove("usage_json");
      row.remove("USAGE_JSON");
      if (detail != null) {
        row.put("cache_hit_input_tokens", detail.get("prompt_cache_hit_tokens"));
        row.put("cache_miss_input_tokens", detail.get("prompt_cache_miss_tokens"));
        row.put("pricing_period", detail.get("pricing_period"));
      }
      logs.add(row);
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("logs", logs);
    List<Map<String, Object>> budgets = persistence.selectMonthBudgetSummaries(month);
    result.put("budget", budgets.isEmpty() ? null : budgets.get(0));
    return result;
  }

  /**
   * Administrator-only audit list. Conversation content is fetched only for an explicitly selected
   * record.
   */
  public Map<String, Object> adminQuestionAudit(int page, int size, String rawKeyword) {
    int safePage = Math.max(page, 1),
        safeSize = Math.min(Math.max(size, 1), 100),
        offset = (safePage - 1) * safeSize;
    String keyword = rawKeyword == null ? "" : rawKeyword.trim();
    Integer total = persistence.countQuestionAudit(keyword);
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : persistence.selectQuestionAuditPage(keyword, safeSize, offset))
      items.add(auditSummary(row));
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("items", items);
    result.put("total", total == null ? 0 : total);
    result.put("page", safePage);
    result.put("pageSize", safeSize);
    return result;
  }

  public Map<String, Object> adminQuestionAuditDetail(String jobId) {
    List<Map<String, Object>> rows = persistence.selectQuestionAuditDetail(jobId);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "AI_AUDIT_RECORD_NOT_FOUND", "未找到 AI 调用记录");
    Map<String, Object> row = rows.get(0), item = auditSummary(row);
    item.put("question", value(row, "input_text"));
    item.put("inputTokens", value(row, "input_tokens"));
    item.put("outputTokens", value(row, "output_tokens"));
    item.put("reservedAmount", value(row, "reserved_amount"));
    item.put("settledAmount", value(row, "settled_amount"));
    item.put("currency", value(row, "currency"));
    item.put("providerRequestId", value(row, "provider_request_id"));
    item.put("finishedAt", value(row, "finished_at"));
    String output = string(row, "output_json");
    if (output != null && !output.isEmpty())
      try {
        item.put("answer", json.readTree(output).path("answer").asText());
      } catch (Exception ignored) {
      }
    return item;
  }

  private Map<String, Object> auditSummary(Map<String, Object> row) {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("jobId", value(row, "job_id"));
    item.put("userId", value(row, "owner_id"));
    item.put("nickname", value(row, "nickname"));
    item.put("shortId", value(row, "short_id"));
    item.put(
        "state",
        value(row, "attempt_state") != null
            ? value(row, "attempt_state")
            : value(row, "job_state"));
    item.put("errorCode", value(row, "error_code"));
    item.put("modelName", value(row, "display_name"));
    item.put("modelCode", value(row, "model_code"));
    item.put("specification", value(row, "specification"));
    item.put("createdAt", value(row, "created_at"));
    return item;
  }

  @Transactional
  public Map<String, Object> saveBudget(BigDecimal limit, String currency) {
    if (limit == null || limit.signum() <= 0) throw bad("INVALID_AI_BUDGET", "月预算必须大于 0");
    if (currency == null || !currency.matches("[A-Za-z]{3}"))
      throw bad("INVALID_CURRENCY", "货币代码必须是 3 位字母");
    LocalDate month = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
    List<Map<String, Object>> rows = persistence.lockMonthBudgetAmounts(month);
    if (rows.isEmpty())
      persistence.insertAiMonthBudget(CryptoUtils.randomId(), month, currency.toUpperCase(), limit);
    else {
      BigDecimal committed =
          decimal(rows.get(0), "spent_amount").add(decimal(rows.get(0), "reserved_amount"));
      if (limit.compareTo(committed) < 0)
        throw conflict("AI_BUDGET_BELOW_COMMITTED", "预算不能低于已消费与已预留金额");
      persistence.updateMonthBudgetLimit(currency.toUpperCase(), limit, month);
    }
    audit("budget", "ai_month_budget", null, "{\"month\":\"" + month + "\"}");
    return adminUsage(50);
  }

  @Transactional
  public Map<String, Object> reconcileAttempt(String attemptId, BigDecimal settledAmount) {
    if (settledAmount == null || settledAmount.signum() < 0)
      throw bad("INVALID_AI_SETTLEMENT", "核定费用不能为负数");
    List<Map<String, Object>> rows = persistence.lockAttemptForReconciliation(attemptId);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "AI_ATTEMPT_NOT_FOUND", "AI 调用记录不存在");
    Map<String, Object> attempt = rows.get(0);
    if (!"unknown".equals(string(attempt, "state")))
      throw conflict("AI_ATTEMPT_NOT_RECONCILABLE", "仅未知费用的调用可以人工核对");
    BigDecimal reserved = decimal(attempt, "reserved_amount");
    persistence.settleBudgetReservation(reserved, settledAmount, string(attempt, "budget_id"));
    persistence.reconcileAttempt(settledAmount, attemptId);
    audit("budget", "ai_attempt", attemptId, "{\"reconciled\":true}");
    return adminUsage(100);
  }

  protected Reservation reserve(
      String ownerId, String requestedModel, String question, String requestKey) {
    // Include the non-null user id in the projection: an unset consent must yield 403, not an
    // empty-row mapping.
    Map<String, Object> user = persistence.lockUserConsent(ownerId);
    String consent = string(user, "ai_consent_version");
    if (consent == null || consent.trim().isEmpty())
      throw new ApiException(
          HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "使用 AI 前请先阅读并同意 AI 内容发送说明");
    Map<String, Object> modelRow = modelRow(requestedModel);
    AiRuntimeModel model = runtime(modelRow);
    String apiKey = parameters.required(model.apiKeyParamKey);
    if (model.inputPerMillion.signum() == 0 && model.outputPerMillion.signum() == 0)
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_PRICE_NOT_CONFIGURED", "模型价格尚未核实，请先在管理端配置");
    LocalDate today = LocalDate.now(BUSINESS_ZONE), month = today.withDayOfMonth(1);
    ensureQuota(today, ownerId, personalDailyLimit());
    ensureQuota(today, GLOBAL_SCOPE, globalDailyLimit());
    ensureGuard(ownerId, properties.getAi().getPersonalConcurrency());
    ensureGuard(GLOBAL_SCOPE, properties.getAi().getGlobalConcurrency());
    reserveQuota(today, ownerId);
    reserveQuota(today, GLOBAL_SCOPE);
    reserveGuard(ownerId);
    reserveGuard(GLOBAL_SCOPE);
    List<Map<String, Object>> budgets = persistence.lockBudgetInCurrency(month, model.currency);
    if (budgets.isEmpty())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_BUDGET_NOT_CONFIGURED", "本月 AI 预算尚未配置");
    Map<String, Object> budget = budgets.get(0);
    int estimatedInput = Math.max(1, question.codePointCount(0, question.length()) + 512);
    BigDecimal reserved = cost(model, estimatedInput, model.maxOutputTokens);
    int budgetChanged =
        persistence.reserveBudgetIfAvailable(reserved, string(budget, "id"), reserved);
    if (budgetChanged != 1)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "AI_MONTH_BUDGET_EXCEEDED", "本月 AI 预算已用完");
    String jobId = CryptoUtils.randomId(),
        attemptId = CryptoUtils.randomId(),
        key =
            requestKey == null || requestKey.trim().isEmpty()
                ? CryptoUtils.randomToken(18)
                : requestKey.trim();
    Instant now = Instant.now();
    try {
      persistence.insertQuestionJob(
          jobId,
          ownerId,
          ownerId,
          jobId,
          new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(question)),
          consent,
          "ASK_V1",
          question,
          Timestamp.from(now.plusSeconds(600)),
          Timestamp.from(now.plus(Duration.ofHours(24))),
          new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(key)));
    } catch (DuplicateKeyException e) {
      throw conflict("AI_REQUEST_DUPLICATE", "该问题正在处理或已处理，请查看记录");
    }
    persistence.insertInitialAttempt(
        attemptId,
        jobId,
        ownerId,
        model.priceId,
        string(budget, "id"),
        today,
        reserved,
        Timestamp.from(now.plusSeconds(model.timeoutSeconds)));
    persistence.consumeReservedDailyQuota(today, ownerId, GLOBAL_SCOPE);
    persistence.markAttemptSending(attemptId);
    Reservation result = new Reservation();
    result.jobId = jobId;
    result.attemptId = attemptId;
    result.ownerId = ownerId;
    result.budgetId = string(budget, "id");
    result.reserved = reserved;
    result.model = model;
    result.apiKey = apiKey;
    result.peakPeriod = isPeakPeriod(now);
    return result;
  }

  protected Reservation reserveSystem(String prompt, String requestKey) {
    return reserveSystem(prompt, requestKey, null);
  }

  protected Reservation reserveSystem(String prompt, String requestKey, String sourceId) {
    return reserveSystem(
        prompt, requestKey, sourceId, "generate_english_articles", "article_generation");
  }

  protected Reservation reserveSystem(
      String prompt, String requestKey, String sourceId, String actionCode, String sourceType) {
    Map<String, Object> modelRow = modelRow(null);
    AiRuntimeModel model = runtime(modelRow);
    String apiKey = parameters.required(model.apiKeyParamKey);
    if (model.inputPerMillion.signum() == 0 && model.outputPerMillion.signum() == 0)
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_PRICE_NOT_CONFIGURED", "模型价格尚未核实，暂时无法使用短文 AI 功能");
    LocalDate today = LocalDate.now(BUSINESS_ZONE), month = today.withDayOfMonth(1);
    ensureQuota(today, SYSTEM_SCOPE, globalDailyLimit());
    ensureQuota(today, GLOBAL_SCOPE, globalDailyLimit());
    ensureGuard(SYSTEM_SCOPE, 1);
    ensureGuard(GLOBAL_SCOPE, properties.getAi().getGlobalConcurrency());
    reserveQuota(today, SYSTEM_SCOPE);
    reserveQuota(today, GLOBAL_SCOPE);
    reserveGuard(SYSTEM_SCOPE);
    reserveGuard(GLOBAL_SCOPE);
    List<Map<String, Object>> budgets = persistence.lockBudgetInCurrency(month, model.currency);
    if (budgets.isEmpty())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI_BUDGET_NOT_CONFIGURED",
          "本月 AI 预算尚未配置，暂时无法使用短文 AI 功能");
    Map<String, Object> budget = budgets.get(0);
    int estimatedInput = Math.max(1, prompt.codePointCount(0, prompt.length()) + 512);
    BigDecimal reserved = cost(model, estimatedInput, model.maxOutputTokens);
    if (persistence.reserveBudgetIfAvailable(reserved, string(budget, "id"), reserved) != 1)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "AI_MONTH_BUDGET_EXCEEDED", "本月 AI 预算已用完，暂时无法使用短文 AI 功能");
    String jobId = CryptoUtils.randomId(),
        attemptId = CryptoUtils.randomId(),
        key =
            requestKey == null || requestKey.trim().isEmpty()
                ? CryptoUtils.randomToken(18)
                : requestKey.trim();
    Instant now = Instant.now();
    try {
      persistence.insertSystemJob(
          jobId,
          properties.getAdminPrincipalId(),
          SYSTEM_SCOPE,
          actionCode,
          sourceType,
          sourceId == null ? CryptoUtils.randomId() : sourceId,
          new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(prompt)),
          "article_word_glossary".equals(actionCode)
              ? "ARTICLE_WORD_GLOSSARY_V1"
              : ("explain_article_paragraph".equals(actionCode)
                  ? "ARTICLE_EXPLANATION_V1"
                  : "ARTICLE_DAILY_V1"),
          prompt,
          Timestamp.from(now.plusSeconds(600)),
          Timestamp.from(now.plus(Duration.ofDays(30))),
          new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(key)));
    } catch (DuplicateKeyException e) {
      throw conflict("AI_REQUEST_DUPLICATE", "该系统 AI 请求正在处理或已处理");
    }
    persistence.insertScheduledAttempt(
        attemptId,
        jobId,
        SYSTEM_SCOPE,
        model.priceId,
        string(budget, "id"),
        today,
        reserved,
        Timestamp.from(now.plusSeconds(model.timeoutSeconds)));
    persistence.consumeReservedDailyQuota(today, SYSTEM_SCOPE, GLOBAL_SCOPE);
    persistence.markAttemptSending(attemptId);
    Reservation result = new Reservation();
    result.jobId = jobId;
    result.attemptId = attemptId;
    result.ownerId = SYSTEM_SCOPE;
    result.budgetId = string(budget, "id");
    result.reserved = reserved;
    result.model = model;
    result.apiKey = apiKey;
    result.peakPeriod = isPeakPeriod(now);
    return result;
  }

  /**
   * The provider call runs outside database locks; settlement and guard release share one
   * transaction.
   */
  protected Map<String, Object> settleSuccess(Reservation r, AiGatewayResult answer) {
    BigDecimal actual = cost(r.model, answer, r.peakPeriod);
    String output = write(Collections.singletonMap("answer", answer.answer));
    Map<String, Object> usageDetail =
        answer.usage == null
            ? new LinkedHashMap<String, Object>()
            : new LinkedHashMap<String, Object>(answer.usage);
    usageDetail.put("pricing_period", r.peakPeriod ? "peak" : "off_peak");
    usageDetail.put("price_id", r.model.priceId);
    String usage = write(usageDetail);
    persistence.settleBudgetReservation(r.reserved, actual, r.budgetId);
    persistence.markAttemptSucceeded(
        answer.providerRequestId,
        actual,
        answer.inputTokens,
        answer.outputTokens,
        usage,
        r.attemptId);
    persistence.markJobSucceeded(output, r.jobId);
    releaseGuards(r.ownerId);
    LOGGER.debug(
        "AI settlement succeeded: inputTokens={}, outputTokens={}",
        answer.inputTokens,
        answer.outputTokens);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("jobId", r.jobId);
    result.put("answer", answer.answer);
    result.put("modelId", r.model.id);
    result.put("modelName", r.model.displayName);
    result.put("specification", r.model.specification);
    result.put("inputTokens", answer.inputTokens);
    result.put("outputTokens", answer.outputTokens);
    result.put("cacheHitInputTokens", answer.cacheHitInputTokens);
    result.put("cacheMissInputTokens", answer.cacheMissInputTokens);
    result.put("pricingPeriod", r.peakPeriod ? "peak" : "off_peak");
    result.put("cost", actual);
    result.put("currency", r.model.currency);
    return result;
  }

  protected void settleFailure(Reservation r, String code) {
    // A transport failure does not prove zero billing; retain its reservation for reconciliation.
    boolean confirmedNoCharge =
        "AI_PROVIDER_REJECTED".equals(code) || "AI_PROVIDER_RATE_LIMIT".equals(code);
    if (confirmedNoCharge) persistence.releaseBudgetReservation(r.reserved, r.budgetId);
    persistence.markAttemptFailedOrUnknown(
        confirmedNoCharge ? "failed" : "unknown",
        confirmedNoCharge ? BigDecimal.ZERO : null,
        confirmedNoCharge ? Timestamp.from(Instant.now()) : null,
        code,
        r.attemptId);
    persistence.markJobFailed(code, r.jobId);
    LOGGER.warn("AI settlement failed: state={}", confirmedNoCharge ? "failed" : "unknown");
    releaseGuards(r.ownerId);
  }

  private Map<String, Object> modelRow(String id) {
    List<Map<String, Object>> rows =
        id == null || id.trim().isEmpty()
            ? persistence.selectDefaultModel()
            : persistence.selectEnabledModel(id);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_MODEL_UNAVAILABLE", "暂无可用 AI 模型");
    if (value(rows.get(0), "price_id") == null)
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_PRICE_NOT_CONFIGURED", "模型价格尚未配置");
    return rows.get(0);
  }

  private AiRuntimeModel runtime(Map<String, Object> row) {
    AiRuntimeModel m = new AiRuntimeModel();
    m.id = string(row, "id");
    m.providerCode = string(row, "provider_code");
    m.modelCode = string(row, "model_code");
    m.displayName = string(row, "display_name");
    m.specification = string(row, "specification");
    m.baseUrl = string(row, "base_url");
    m.apiKeyParamKey = string(row, "api_key_param_key");
    m.maxOutputTokens = number(row, "max_output_tokens");
    m.timeoutSeconds = number(row, "timeout_seconds");
    m.versionNo = number(row, "version_no");
    m.priceId = string(row, "price_id");
    m.currency = string(row, "currency");
    m.inputPerMillion = decimal(row, "input_per_million");
    m.outputPerMillion = decimal(row, "output_per_million");
    m.pricingJson = string(row, "pricing_json");
    return m;
  }

  private Map<String, Object> modelView(Map<String, Object> row) {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("id", value(row, "id"));
    item.put("providerCode", value(row, "provider_code"));
    item.put("modelCode", value(row, "model_code"));
    item.put("displayName", value(row, "display_name"));
    item.put("specification", value(row, "specification"));
    item.put("baseUrl", value(row, "base_url"));
    item.put("apiKeyParamKey", value(row, "api_key_param_key"));
    item.put("defaultModel", number(row, "is_default") == 1);
    item.put("maxOutputTokens", value(row, "max_output_tokens"));
    item.put("timeoutSeconds", value(row, "timeout_seconds"));
    item.put("versionNo", value(row, "version_no"));
    item.put("currency", value(row, "currency"));
    item.put("inputPerMillion", value(row, "input_per_million"));
    item.put("outputPerMillion", value(row, "output_per_million"));
    item.put("priceEffectiveAt", value(row, "price_effective_at"));
    Map<String, Object> pricing = readJsonMap(string(row, "pricing_json"));
    item.put("pricing", pricing);
    item.put(
        "priceManaged",
        pricing != null && "deepseek-official-v1".equals(String.valueOf(pricing.get("schema"))));
    item.put("currentPricingPeriod", isPeakPeriod(Instant.now()) ? "peak" : "off_peak");
    return item;
  }

  private void validateModel(AiModelSaveRequest r) {
    if (r == null) throw bad("INVALID_AI_MODEL", "模型配置不能为空");
    if (!CODE.matcher(clean(r.getProviderCode())).matches()
        || !CODE.matcher(clean(r.getModelCode())).matches())
      throw bad("INVALID_AI_MODEL_CODE", "供应商和模型代码格式不正确");
    if (!clean(r.getBaseUrl()).startsWith("https://"))
      throw bad("INVALID_AI_BASE_URL", "模型地址必须使用 HTTPS");
    if (!clean(r.getApiKeyParamKey()).matches("[A-Za-z0-9._-]{3,100}"))
      throw bad("INVALID_AI_CREDENTIAL_KEY", "密钥参数名格式不正确");
    if (!officialPriceManaged(r.getProviderCode())
        && (r.getInputPerMillion() == null || r.getOutputPerMillion() == null))
      throw bad("INVALID_AI_PRICE", "请完整配置输入和输出价格");
    if (r.isDefaultModel() && !r.isEnabled()) throw bad("INVALID_AI_DEFAULT", "默认模型必须同时启用");
  }
  /** 每日额度按「系统参数」当前值建行并同步，改参数当天即生效（不再依赖 yml）。 */
  private void ensureQuota(LocalDate date, String scope, int limit) {
    if (persistence.countDailyQuota(date, scope) == 0)
      try {
        persistence.insertAiDailyQuota(CryptoUtils.randomId(), date, scope, limit);
      } catch (DuplicateKeyException ignored) {
      }
    else persistence.updateDailyQuotaLimitIfChanged(limit, date, scope, limit);
  }

  private int personalDailyLimit() {
    return parameters.intValue(
        AppParameterService.AI_PERSONAL_DAILY_LIMIT,
        properties.getAi().getPersonalDailyLimit(),
        1,
        10000);
  }

  private int globalDailyLimit() {
    return parameters.intValue(
        AppParameterService.AI_GLOBAL_DAILY_LIMIT,
        properties.getAi().getGlobalDailyLimit(),
        1,
        100000);
  }

  private void reserveQuota(LocalDate date, String scope) {
    if (persistence.reserveDailyQuotaIfAvailable(date, scope) != 1)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "AI_DAILY_QUOTA_EXCEEDED", "今日 AI 使用次数已用完");
  }

  private void ensureGuard(String scope, int limit) {
    if (persistence.countConcurrencyGuard(scope) == 0)
      try {
        persistence.insertAiConcurrencyGuard(CryptoUtils.randomId(), scope, limit);
      } catch (DuplicateKeyException ignored) {
      }
  }

  private void reserveGuard(String scope) {
    if (persistence.acquireConcurrencyIfAvailable(scope) != 1)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "AI_CONCURRENCY_EXCEEDED", "已有 AI 任务在处理，请稍后再试");
  }

  private void releaseGuards(String owner) {
    persistence.releaseConcurrency(owner, GLOBAL_SCOPE);
  }

  private BigDecimal cost(AiRuntimeModel model, int input, int output) {
    return model
        .inputPerMillion
        .multiply(BigDecimal.valueOf(Math.max(input, 0)))
        .add(model.outputPerMillion.multiply(BigDecimal.valueOf(Math.max(output, 0))))
        .divide(BigDecimal.valueOf(1000000), 6, RoundingMode.CEILING);
  }

  private BigDecimal cost(AiRuntimeModel model, AiGatewayResult usage, boolean peakPeriod) {
    Map<String, Object> pricing = readJsonMap(model.pricingJson);
    if (pricing == null || !"deepseek-official-v1".equals(String.valueOf(pricing.get("schema"))))
      return cost(model, usage.inputTokens, usage.outputTokens);
    Object rawRates = pricing.get(peakPeriod ? "peak" : "offPeak");
    if (!(rawRates instanceof Map)) return cost(model, usage.inputTokens, usage.outputTokens);
    Map<?, ?> rates = (Map<?, ?>) rawRates;
    int hit = Math.max(usage.cacheHitInputTokens, 0),
        miss = Math.max(usage.cacheMissInputTokens, 0);
    if (hit + miss < usage.inputTokens) miss += usage.inputTokens - hit - miss;
    BigDecimal amount =
        rate(rates, "inputCacheHit")
            .multiply(BigDecimal.valueOf(hit))
            .add(rate(rates, "inputCacheMiss").multiply(BigDecimal.valueOf(miss)))
            .add(
                rate(rates, "output")
                    .multiply(BigDecimal.valueOf(Math.max(usage.outputTokens, 0))));
    return amount.divide(BigDecimal.valueOf(1000000), 6, RoundingMode.CEILING);
  }

  private BigDecimal rate(Map<?, ?> rates, String key) {
    Object value = rates.get(key);
    if (value == null) throw new IllegalStateException("官方价格字段缺失: " + key);
    return new BigDecimal(String.valueOf(value));
  }

  static boolean isPeakPeriod(Instant instant) {
    ZonedDateTime local = instant.atZone(BUSINESS_ZONE);
    int day = local.getDayOfWeek().getValue();
    if (day > 5) return false;
    LocalTime time = local.toLocalTime();
    return (!time.isBefore(LocalTime.of(9, 0)) && time.isBefore(LocalTime.NOON))
        || (!time.isBefore(LocalTime.of(14, 0)) && time.isBefore(LocalTime.of(18, 0)));
  }

  private boolean officialPriceManaged(String providerCode) {
    return "deepseek".equalsIgnoreCase(clean(providerCode));
  }

  private Map<String, Object> readJsonMap(String value) {
    if (value == null || value.trim().isEmpty()) return null;
    try {
      return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception ignored) {
      return null;
    }
  }

  private void audit(String action, String type, String id, String metadata) {
    persistence.insertAdminAudit(
        CryptoUtils.randomId(),
        properties.getAdminPrincipalId(),
        action,
        type,
        id,
        metadata,
        Timestamp.from(Instant.now().plus(Duration.ofDays(180))));
  }

  private Object value(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? row.get(key.toUpperCase()) : value;
  }

  private String string(Map<String, Object> row, String key) {
    Object value = value(row, key);
    return value == null ? null : String.valueOf(value);
  }

  private int number(Map<String, Object> row, String key) {
    Object value = value(row, key);
    return value instanceof Number
        ? ((Number) value).intValue()
        : value == null ? 0 : Integer.parseInt(String.valueOf(value));
  }

  private BigDecimal decimal(Map<String, Object> row, String key) {
    Object value = value(row, key);
    return value == null
        ? BigDecimal.ZERO
        : value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  private ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  private static class Reservation {
    String jobId, attemptId, ownerId, budgetId, apiKey;
    BigDecimal reserved;
    AiRuntimeModel model;
    boolean peakPeriod;
  }
}
