package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.AiModelSaveRequest;
import com.zhixing.dto.TtsConfigRequest;
import com.zhixing.service.AdminAiUsageService;
import com.zhixing.service.AiOfficialPricingService;
import com.zhixing.service.AiService;
import com.zhixing.service.AliyunTtsService;
import java.math.BigDecimal;
import java.util.*;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {
  private final AiService ai;
  private final AiOfficialPricingService pricing;
  private final AliyunTtsService tts;
  private final AppProperties properties;
  private final AdminAiUsageService usage;

  public AdminAiController(
      AiService ai,
      AiOfficialPricingService pricing,
      AliyunTtsService tts,
      AppProperties properties,
      AdminAiUsageService usage) {
    this.ai = ai;
    this.pricing = pricing;
    this.tts = tts;
    this.properties = properties;
    this.usage = usage;
  }
  /** 按日期和用户筛选管理员用量日志。 */
  @GetMapping("/usage-logs")
  public Map<String, Object> usageLogs(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize) {
    requireAdmin(token);
    return usage.logs(dateFrom, dateTo, keyword, page, pageSize);
  }

  /** 读取管理员模型配置及凭据是否配置。 */
  @GetMapping("/models")
  public List<Map<String, Object>> models(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    return ai.adminModels();
  }

  /** 新建模型并保留价格快照和管理员审计。 */
  @PostMapping("/models")
  public Map<String, Object> create(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @Valid @RequestBody AiModelSaveRequest request) {
    requireAdmin(token);
    return ai.saveModel(null, request);
  }

  /** 按预期版本更新模型，避免覆盖并发配置。 */
  @PutMapping("/models/{id}")
  public Map<String, Object> update(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id,
      @Valid @RequestBody AiModelSaveRequest request) {
    requireAdmin(token);
    return ai.saveModel(id, request);
  }

  /** 读取近期调用费用和本月预算。 */
  @GetMapping("/usage")
  public Map<String, Object> usage(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "100") int size) {
    requireAdmin(token);
    return ai.adminUsage(size);
  }

  /** 读取官方价格同步状态和最近运行结果。 */
  @GetMapping("/pricing/status")
  public Map<String, Object> pricingStatus(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    return pricing.status();
  }

  /** 设置本月预算，不允许低于已消费及已预留金额。 */
  @PutMapping("/budget")
  public Map<String, Object> budget(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> request) {
    requireAdmin(token);
    Object raw = request.get("limitAmount");
    BigDecimal limit;
    try {
      limit = new BigDecimal(String.valueOf(raw));
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_BUDGET", "请输入正确的月预算");
    }
    return ai.saveBudget(limit, String.valueOf(request.getOrDefault("currency", "CNY")));
  }

  /** 人工核定费用未知的调用，并更新预算账本。 */
  @PutMapping("/attempts/{id}/reconcile")
  public Map<String, Object> reconcile(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id,
      @RequestBody Map<String, Object> request) {
    requireAdmin(token);
    BigDecimal amount;
    try {
      amount = new BigDecimal(String.valueOf(request.get("settledAmount")));
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_SETTLEMENT", "请输入正确的核定费用");
    }
    return ai.reconcileAttempt(id, amount);
  }

  /** 读取语音配置摘要及最近使用日志，不返回密钥。 */
  @GetMapping("/tts")
  public Map<String, Object> tts(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    return tts.adminConfig();
  }

  /** 更新语音配置，凭据通过参数服务加密保存。 */
  @PutMapping("/tts")
  public Map<String, Object> saveTts(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @Valid @RequestBody TtsConfigRequest request) {
    requireAdmin(token);
    return tts.saveConfig(request);
  }

  /** 使用已配置凭据测试语音服务连接。 */
  @PostMapping("/tts/connection-test")
  public Map<String, Object> testTts(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    return tts.connectionTest();
  }

  /** 核对指定账号的语音配置状态。 */
  @PostMapping("/tts/account-check")
  public Map<String, Object> checkTtsAccount(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> request) {
    requireAdmin(token);
    return tts.accountCheck(String.valueOf(request.getOrDefault("accountId", "")));
  }

  /** 使用临时令牌合成测试音频，令牌不记录到日志。 */
  @PostMapping("/tts/temporary-token-test")
  public Map<String, Object> testTemporaryToken(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> request) {
    requireAdmin(token);
    return tts.previewWithTemporaryToken(
        String.valueOf(request.getOrDefault("nlsToken", "")),
        String.valueOf(request.getOrDefault("text", "")),
        String.valueOf(request.getOrDefault("voice", "")),
        request.get("sampleRate") instanceof Number
            ? ((Number) request.get("sampleRate")).intValue()
            : null);
  }

  /** 加密保存临时令牌并启用生产语音模式。 */
  @PostMapping("/tts/temporary-token-production")
  public Map<String, Object> enableTemporaryTokenProduction(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> request) {
    requireAdmin(token);
    return tts.enableTemporaryTokenProduction(String.valueOf(request.getOrDefault("nlsToken", "")));
  }

  /** 禁用并删除已保存的生产临时令牌。 */
  @DeleteMapping("/tts/temporary-token-production")
  public Map<String, Object> disableTemporaryTokenProduction(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    return tts.disableTemporaryTokenProduction();
  }

  private void requireAdmin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
