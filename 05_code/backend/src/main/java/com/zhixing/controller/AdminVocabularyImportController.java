package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AliyunTtsService;
import com.zhixing.service.VocabularyImportService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 管理端词库数据源、暂存批次与人工审核接口。 */
@RestController
@RequestMapping("/api/admin/vocabulary")
public class AdminVocabularyImportController {
  private final VocabularyImportService imports;
  private final AppProperties properties;
  private final AliyunTtsService tts;

  public AdminVocabularyImportController(
      VocabularyImportService imports, AppProperties properties, AliyunTtsService tts) {
    this.imports = imports;
    this.properties = properties;
    this.tts = tts;
  }

  /** 列出启用的词库数据源及许可状态。 */
  @GetMapping("/datasets")
  public List<Map<String, Object>> datasets(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    return imports.datasets();
  }

  /** 上传并校验词库快照，暂存为数据源供批次导入。 */
  @PostMapping("/datasets")
  public Map<String, Object> dataset(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    admin(token);
    return imports.createDataset(
        string(body, "datasetName"),
        string(body, "providerName"),
        string(body, "licenseStatus"),
        string(body, "licenseNote"),
        string(body, "payload"));
  }

  /** 删除没有被导入批次使用的数据源。 */
  @DeleteMapping("/datasets/{id}")
  public Map<String, Object> deleteDataset(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.deleteDataset(id, properties.getAdminPrincipalId());
  }

  /** 列出最近的导入批次及处理状态。 */
  @GetMapping("/import-batches")
  public List<Map<String, Object>> batches(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    return imports.batches();
  }

  /** 为指定数据源和目标词书创建暂存导入批次。 */
  @PostMapping("/import-batches")
  public Map<String, Object> create(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    admin(token);
    return imports.createBatch(
        string(body, "datasetId"),
        string(body, "targetBookId"),
        map(body.get("options")),
        properties.getAdminPrincipalId());
  }

  /** 读取批次统计、暂存词条及语音任务概况。 */
  @GetMapping("/import-batches/{id}")
  public Map<String, Object> batch(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.batch(id);
  }

  /** 删除允许清理的未发布批次及关联暂存任务。 */
  @DeleteMapping("/import-batches/{id}")
  public Map<String, Object> deleteBatch(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.deleteBatch(id, properties.getAdminPrincipalId());
  }

  /** 解析批次词条，识别复用项并规划人工审核和语音生成。 */
  @PostMapping("/import-batches/{id}/start")
  public Map<String, Object> start(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.start(id, properties.getAdminPrincipalId());
  }

  /** 暂停正在处理或待审核的批次。 */
  @PostMapping("/import-batches/{id}/pause")
  public Map<String, Object> pause(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.pause(id, properties.getAdminPrincipalId());
  }

  /** 重试批次待处理或失败的语音任务。 */
  @PostMapping("/import-batches/{id}/retry")
  public Map<String, Object> retry(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.retryTts(id, properties.getAdminPrincipalId());
  }

  /** 通过授权、审核和语音门禁后事务性发布词条到词书。 */
  @PostMapping("/import-batches/{id}/publish")
  public Map<String, Object> publish(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    admin(token);
    return imports.publish(id, properties.getAdminPrincipalId());
  }

  /** 批准或拒绝批次中的指定待审核词条。 */
  @PostMapping("/import-items/review")
  public Map<String, Object> review(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    admin(token);
    return imports.review(
        string(body, "batchId"),
        strings(body.get("itemIds")),
        string(body, "decision"),
        properties.getAdminPrincipalId());
  }

  /** 列出词库语音生成可用音色。 */
  @GetMapping("/tts/voices")
  public List<Map<String, Object>> voices(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    return tts.vocabularyVoices();
  }

  /** 生成选定音色和采样率的试听音频。 */
  @PostMapping("/tts/preview")
  public Map<String, Object> preview(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    admin(token);
    return tts.previewVocabularyVoice(string(body, "voice"), integer(body.get("sampleRate")));
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }

  private String string(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value == null ? null : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map
        ? new LinkedHashMap<String, Object>((Map<String, Object>) value)
        : new LinkedHashMap<String, Object>();
  }

  private List<String> strings(Object raw) {
    if (!(raw instanceof List)) return Collections.emptyList();
    List<String> result = new ArrayList<String>();
    for (Object item : (List<?>) raw) if (item != null) result.add(String.valueOf(item));
    return result;
  }

  private Integer integer(Object value) {
    if (value instanceof Number) return ((Number) value).intValue();
    try {
      return value == null ? null : Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TTS_SAMPLE_RATE", "采样率不正确");
    }
  }
}
