package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.VocabularyImportPersistenceMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端词库导入的暂存层。它刻意不直接修改正式词典：解析、碰撞和审核都先落在批次表中， 只有后续 TTS/发布门禁全部通过后才允许接入 VocabularyPublishService。 */
@Service
public class VocabularyImportService {
  private static final Logger LOGGER = LoggerFactory.getLogger(VocabularyImportService.class);
  private static final int MAX_ROWS = 5000;
  private final VocabularyImportPersistenceMapper mapper;
  private final ObjectMapper json;
  private final AliyunTtsService tts;

  public VocabularyImportService(
      VocabularyImportPersistenceMapper mapper, ObjectMapper json, AliyunTtsService tts) {
    this.mapper = mapper;
    this.json = json;
    this.tts = tts;
  }

  public List<Map<String, Object>> datasets() {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : mapper.listEnabledDatasets()) result.add(dataset(row));
    return result;
  }

  @Transactional
  public Map<String, Object> createDataset(
      String name, String provider, String licenseStatus, String licenseNote, String payload) {
    String cleanName = text(name, 160, "数据集名称");
    String cleanProvider = text(provider, 160, "提供方");
    String cleanLicense =
        choice(
            licenseStatus,
            Arrays.asList("verified", "metadata_only", "unknown", "restricted"),
            "许可状态");
    String cleanNote = text(licenseNote, 1000, "授权说明");
    List<Map<String, Object>> parsed = parse(payload);
    String id = CryptoUtils.randomId();
    String code = "upload-" + id.substring(0, 12);
    mapper.insertUploadedDataset(
        id,
        code,
        cleanName,
        cleanProvider,
        "upload-" + id.substring(0, 8),
        cleanLicense,
        cleanNote,
        new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(payload)),
        parsed.size(),
        payload);
    return dataset(mapper.findDatasetForDeletion(id));
  }

  /** Deletes an unused source snapshot only; batch history is deliberately never orphaned. */
  @Transactional
  public Map<String, Object> deleteDataset(String datasetId, String adminId) {
    Map<String, Object> source = mapper.lockDatasetForDeletion(datasetId);
    if (source == null) throw fail(HttpStatus.NOT_FOUND, "DATASET_NOT_FOUND", "数据源不存在或已删除");
    int batches = mapper.countDatasetBatches(datasetId);
    if (batches > 0)
      throw fail(
          HttpStatus.CONFLICT,
          "DATASET_IN_USE",
          "该数据源已被 " + batches + " 个批次引用，不能删除；请保留批次快照以保证历史可追溯");
    int words = 0;
    if ("online_snapshot".equals(string(source, "source_type"))) {
      words = mapper.countSnapshotWords(datasetId);
      mapper.deleteSnapshotWords(datasetId);
      mapper.clearBooksSnapshot(datasetId);
    }
    mapper.deleteDataset(datasetId);
    Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    metadata.put("datasetCode", string(source, "dataset_code"));
    metadata.put("removedOnlineWords", words);
    audit(
        adminId,
        "vocabulary_dataset_delete",
        "vocabulary_dataset",
        datasetId,
        "succeeded",
        metadata);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("deleted", true);
    result.put("datasetName", string(source, "dataset_name"));
    result.put("removedOnlineWords", words);
    return result;
  }

  /**
   * Deletes a staged import batch with its rows and TTS tasks; published batches stay for
   * traceability.
   */
  @Transactional
  public Map<String, Object> deleteBatch(String batchId, String adminId) {
    Map<String, Object> batch = mapper.findBatchForDeletion(batchId);
    if (batch == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "词库批次不存在或已删除");
    String state = string(batch, "state");
    if ("published".equals(state))
      throw fail(HttpStatus.CONFLICT, "IMPORT_BATCH_PUBLISHED", "批次已发布，删除会破坏线上词书与历史快照，已拒绝");
    if ("running".equals(state))
      throw fail(HttpStatus.CONFLICT, "IMPORT_BATCH_RUNNING", "批次正在执行，请先暂停或等待结束后再删除");
    int items = mapper.countPublishedItems(batchId);
    int tasks = mapper.countRunningAudioTasks(batchId);
    mapper.deleteBatchItems(batchId);
    mapper.deleteBatchAudioTasks(batchId);
    mapper.deleteBatch(batchId);
    Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    metadata.put("datasetName", string(batch, "dataset_name"));
    metadata.put("batchState", state);
    metadata.put("removedItems", items);
    metadata.put("removedTtsTasks", tasks);
    audit(
        adminId,
        "vocabulary_import_batch_delete",
        "vocabulary_import_batch",
        batchId,
        "succeeded",
        metadata);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("deleted", true);
    result.put("batchId", batchId);
    result.put("datasetName", string(batch, "dataset_name"));
    result.put("batchState", state);
    result.put("removedItems", items);
    result.put("removedTtsTasks", tasks);
    return result;
  }

  @Transactional
  public Map<String, Object> createBatch(
      String datasetId, String targetBookId, Map<String, Object> options, String adminId) {
    Map<String, Object> dataset = mapper.findDataset(datasetId);
    if (dataset == null) throw fail(HttpStatus.NOT_FOUND, "DATASET_NOT_FOUND", "数据源不存在或已停用");
    if (mapper.findTargetBook(targetBookId) == null)
      throw fail(HttpStatus.NOT_FOUND, "VOCABULARY_BOOK_NOT_FOUND", "目标词书不存在或未启用");
    Map<String, Object> snapshot =
        options == null
            ? new LinkedHashMap<String, Object>()
            : new LinkedHashMap<String, Object>(options);
    snapshot.put("usVoice", tts.vocabularyVoice(snapshot.get("usVoice"), "us", "eva"));
    snapshot.put("ukVoice", tts.vocabularyVoice(snapshot.get("ukVoice"), "uk", "luna"));
    int sampleRate = number(snapshot.get("sampleRate"), 16000);
    if (sampleRate != 8000 && sampleRate != 16000)
      throw fail(HttpStatus.BAD_REQUEST, "INVALID_TTS_SAMPLE_RATE", "采样率只能是 8000 或 16000");
    snapshot.put("sampleRate", sampleRate);
    snapshot.put("exportSql", bool(snapshot.get("exportSql")));
    snapshot.put("directPublish", bool(snapshot.get("directPublish")));
    snapshot.put("licenseStatusSnapshot", value(dataset, "license_status"));
    String id = CryptoUtils.randomId();
    mapper.insertBatch(id, datasetId, targetBookId, write(snapshot), adminId);
    audit(
        adminId, "vocabulary_import_create", "vocabulary_import_batch", id, "succeeded", snapshot);
    return batch(id);
  }

  public List<Map<String, Object>> batches() {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : mapper.listRecentBatches()) result.add(batchRow(row));
    return result;
  }

  public Map<String, Object> batch(String id) {
    Map<String, Object> row = mapper.findBatchDetail(id);
    if (row == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "词库批次不存在");
    Map<String, Object> result = batchRow(row);
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> item : mapper.listBatchItems(id)) items.add(itemRow(item));
    result.put("items", items);
    result.put("ttsPending", mapper.countSucceededAudioTasks(id));
    result.put("ttsFailed", mapper.countFailedAudioTasks(id));
    return result;
  }

  /** 同步执行轻量暂存步骤；实际语音合成由后续 worker 消费 tts_generation_task。 */
  @Transactional
  public Map<String, Object> start(String id, String adminId) {
    Map<String, Object> current = mapper.lockBatchForParsing(id);
    if (current == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "词库批次不存在");
    String state = string(current, "state");
    if (!("draft".equals(state) || "paused".equals(state) || "failed".equals(state)))
      throw fail(HttpStatus.CONFLICT, "IMPORT_BATCH_STATE", "当前批次不能启动，请先新建或暂停后恢复");
    Map<String, Object> source = mapper.findDataset(string(current, "dataset_id"));
    if (source == null) throw fail(HttpStatus.CONFLICT, "DATASET_DISABLED", "数据源已停用");
    if (mapper.countPublishedItems(id) > 0) {
      int waiting = mapper.countPendingReviews(id);
      mapper.updateBatchReviewState(
          waiting > 0 ? "review_required" : "ready_for_review",
          waiting > 0 ? "review" : "tts_plan",
          id);
      return batch(id); // Resume without discarding completed audio or reviews.
    }
    mapper.deleteBatchAudioTasks(id);
    mapper.deleteBatchItems(id);
    mapper.beginBatchParsing(id);
    List<Map<String, Object>> rows;
    try {
      if ("online_snapshot".equals(string(source, "source_type"))
          && "word-entry-v1".equals(string(source, "parser_code"))) {
        rows = new ArrayList<Map<String, Object>>();
        for (String payload : mapper.listSnapshotPayloads(string(source, "id")))
          rows.add(readMap(payload));
        if (rows.isEmpty() || rows.size() != number(value(source, "item_count"), 0))
          throw fail(HttpStatus.CONFLICT, "ONLINE_SNAPSHOT_INCOMPLETE", "词条快照不完整，禁止创建不完整批次");
      } else rows = parse(string(source, "source_payload"));
    } catch (ApiException e) {
      markFailed(id, e.getCode(), e.getMessage());
      throw e;
    }
    if (rows.size() > MAX_ROWS) {
      markFailed(id, "IMPORT_TOO_LARGE", "单批次最多导入 5000 行");
      throw fail(HttpStatus.BAD_REQUEST, "IMPORT_TOO_LARGE", "单批次最多导入 5000 行");
    }
    int created = 0, reused = 0, review = 0, skipped = 0, failed = 0, success = 0, rowNo = 0;
    Set<String> seen = new HashSet<String>();
    Map<String, Object> options = readMap(string(current, "options_json"));
    for (Map<String, Object> raw : rows) {
      rowNo++;
      String term = nullable(raw.get("word"));
      if (term == null) term = nullable(raw.get("term"));
      String normalized = normalize(term);
      String decision = "create",
          reviewStatus = "approved",
          risk = "[]",
          errorCode = null,
          errorMessage = null,
          contentId = null;
      if (normalized == null) {
        decision = "review";
        reviewStatus = "pending";
        risk = "[\"invalid_word\"]";
        errorCode = "INVALID_WORD";
        errorMessage = "词头必须是 1 至 80 个英文字符、空格、连字符或撇号";
        review++;
        failed++;
      } else if (!seen.add(normalized)) {
        decision = "skip";
        reviewStatus = "rejected";
        risk = "[\"duplicate_in_batch\"]";
        errorCode = "DUPLICATE_IN_BATCH";
        errorMessage = "同一批次中出现重复词头";
        skipped++;
      } else {
        List<String> existing =
            mapper.findWordIdsByHash(
                new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(normalized)));
        if (!existing.isEmpty()) {
          decision = "reuse";
          contentId = existing.get(0);
          reused++;
        } else created++;
        success++;
      }
      if ("create".equals(decision)) {
        String structureError = null;
        try {
          new VocabularyEntry(raw).validate();
        } catch (ApiException e) {
          structureError = e.getMessage();
        }
        if (bool(raw.get("requiresContentReview")) || structureError != null) {
          decision = "review";
          reviewStatus = "pending";
          risk = write(raw.get("qualityFlags"));
          errorCode = "WORD_CONTENT_REVIEW_REQUIRED";
          errorMessage = structureError == null ? "例句义项归属待确认，请修正结构化数据源后重新导入" : structureError;
          created--;
          success--;
          review++;
        }
      }
      String itemId = CryptoUtils.randomId();
      mapper.insertBatchItem(
          itemId,
          id,
          rowNo,
          write(raw),
          term,
          normalized,
          normalized == null
              ? null
              : new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(normalized)),
          decision,
          contentId,
          reviewStatus,
          risk,
          errorCode,
          errorMessage);
      if ("create".equals(decision)) createTtsTasks(id, itemId, normalized, raw, options);
    }
    String next = review > 0 ? "review_required" : "ready_for_review";
    mapper.finishBatchParsing(
        next,
        review > 0 ? "review" : "tts_plan",
        rows.size(),
        success,
        failed,
        reused,
        created,
        skipped,
        review,
        id);
    audit(
        adminId,
        "vocabulary_import_start",
        "vocabulary_import_batch",
        id,
        "succeeded",
        Collections.<String, Object>singletonMap("total", rows.size()));
    return batch(id);
  }

  @Transactional
  public Map<String, Object> review(
      String batchId, List<String> itemIds, String decision, String adminId) {
    if (!"approve".equals(decision) && !"reject".equals(decision))
      throw fail(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_DECISION", "审核结果必须是 approve 或 reject");
    if (itemIds == null || itemIds.isEmpty() || itemIds.size() > 100)
      throw fail(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_ITEMS", "每次请选择 1 至 100 条记录");
    Map<String, Object> batch = mapper.lockBatchForParsing(batchId);
    if (batch == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "词库批次不存在");
    for (String itemId : itemIds) {
      Map<String, Object> item = mapper.findBatchItem(itemId, batchId);
      if (item == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_ITEM_NOT_FOUND", "待审核项不存在");
      if (!"review".equals(string(item, "decision"))) continue;
      if ("approve".equals(decision)) {
        Map<String, Object> raw = readMap(string(item, "raw_json"));
        if (bool(raw.get("requiresContentReview")))
          throw fail(
              HttpStatus.CONFLICT, "WORD_CONTENT_REVIEW_REQUIRED", "请先修正例句义项归属并重新导入，不能直接跳过内容校验");
        new VocabularyEntry(raw).validate();
        String normalized = string(item, "normalized_word");
        if (normalized == null)
          throw fail(HttpStatus.CONFLICT, "INVALID_WORD_REQUIRES_FIX", "无效词头不能直接通过，需要修正数据源后重新导入");
        mapper.approveBatchItem(itemId);
        createTtsTasks(
            batchId,
            itemId,
            normalized,
            readMap(string(item, "raw_json")),
            readMap(string(batch, "options_json")));
      } else mapper.rejectBatchItem(itemId);
    }
    int waiting = mapper.countReviewDecisions(batchId);
    if (waiting == 0) mapper.markBatchReadyForReview(batchId);
    audit(
        adminId,
        "vocabulary_import_review",
        "vocabulary_import_batch",
        batchId,
        "succeeded",
        Collections.<String, Object>singletonMap("decision", decision));
    return batch(batchId);
  }

  @Transactional
  public Map<String, Object> pause(String id, String adminId) {
    int updated = mapper.pauseBatch(id);
    if (updated == 0) throw fail(HttpStatus.CONFLICT, "IMPORT_BATCH_STATE", "当前批次不能暂停");
    audit(
        adminId,
        "vocabulary_import_pause",
        "vocabulary_import_batch",
        id,
        "succeeded",
        Collections.<String, Object>emptyMap());
    return batch(id);
  }

  /** 只消费 pending/failed 任务；object_key 和 MediaService 的幂等检查保证成功项不会重复生成。 */
  @Transactional
  public Map<String, Object> retryTts(String id, String adminId) {
    Map<String, Object> batch = mapper.lockBatchWithSource(id);
    if (batch == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "词库批次不存在");
    if (!"verified".equals(string(batch, "license_status")))
      throw fail(HttpStatus.CONFLICT, "DATASET_LICENSE_UNVERIFIED", "请先核验来源许可，再生成收费音频");
    if (!Arrays.asList("ready_for_review", "tts_failed", "ready_to_publish")
        .contains(string(batch, "state")))
      throw fail(HttpStatus.CONFLICT, "IMPORT_BATCH_STATE", "当前状态不能生成音频");
    if (!tts.configured())
      throw fail(HttpStatus.SERVICE_UNAVAILABLE, "TTS_NOT_CONFIGURED", "请先在 AI 模型与费用页面配置阿里云 TTS");
    Map<String, Object> options = readMap(string(batch, "options_json"));
    List<Map<String, Object>> tasks = mapper.listAudioTasksToRun(id);
    for (Map<String, Object> task : tasks) {
      String taskId = string(task, "id");
      mapper.startAudioTask(taskId);
      try {
        Map<String, Object> raw = readMap(string(task, "raw_json"));
        VocabularyEntry entry = new VocabularyEntry(raw);
        String text =
            "example".equals(string(task, "target_type"))
                ? entry.sentence(
                    number(value(task, "sense_no"), 1),
                    Math.max(1, number(value(task, "example_no"), 1)))
                : string(task, "word_term");
        String purpose =
            "example".equals(string(task, "target_type")) ? "example_audio" : "word_audio";
        String asset =
            tts.synthesizeVocabularyAudio(
                text,
                string(task, "voice"),
                purpose,
                string(task, "object_key"),
                string(batch, "source_url"),
                string(batch, "license_note"),
                number(options.get("sampleRate"), 16000));
        mapper.completeAudioTask(asset, taskId);
      } catch (ApiException e) {
        mapper.failAudioTask(e.getCode(), e.getMessage(), taskId);
      }
    }
    int bad = mapper.countIncompleteAudioTasks(id);
    mapper.updateBatchReviewState(
        bad == 0
            ? "ready_to_publish"
            : (mapper.countFailedAudioTasks(id) > 0 ? "tts_failed" : "ready_for_review"),
        bad == 0 ? "audio_complete" : "tts",
        id);
    audit(
        adminId,
        "vocabulary_import_retry_tts",
        "vocabulary_import_batch",
        id,
        bad == 0 ? "succeeded" : "partial",
        Collections.<String, Object>singletonMap("remaining", bad));
    return batch(id);
  }

  /** 发布入口先执行硬门禁，避免没有 ready 音频或未核验授权的半成品进入正式词库。 */
  @Transactional
  public Map<String, Object> publish(String id, String adminId) {
    // Locking the batch makes review/publication gates and the published transition atomic.
    Map<String, Object> batch = mapper.lockBatchWithLicense(id);
    if (batch == null) throw fail(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "词库批次不存在");
    if (!"verified".equals(string(batch, "license_status")))
      throw fail(HttpStatus.CONFLICT, "DATASET_LICENSE_UNVERIFIED", "数据源许可未核验，禁止正式发布");
    if ("published".equals(string(batch, "state"))) return batch(id);
    if (!Arrays.asList("ready_for_review", "ready_to_publish", "tts_failed")
            .contains(string(batch, "state"))
        || mapper.countPublishedItems(id) == 0)
      throw fail(HttpStatus.CONFLICT, "IMPORT_BATCH_STATE", "请先解析并审核批次，空批次不能发布");
    int unresolved = mapper.countPendingReviews(id);
    int pending = mapper.countUnusableAudioTasks(id);
    if (unresolved > 0) throw fail(HttpStatus.CONFLICT, "IMPORT_REVIEW_PENDING", "仍有待审核项，不能发布");
    if (pending > 0) throw fail(HttpStatus.CONFLICT, "TTS_GATE_NOT_READY", "仍有未完成或失败的语音任务，不能发布");
    publishItems(id, batch, adminId);
    mapper.markBatchPublished(id);
    audit(
        adminId,
        "vocabulary_import_publish",
        "vocabulary_import_batch",
        id,
        "succeeded",
        Collections.<String, Object>emptyMap());
    return batch(id);
  }

  private void publishItems(String batchId, Map<String, Object> batch, String adminId) {
    Map<String, Object> dataset = mapper.findDatasetForDeletion(string(batch, "dataset_id"));
    String sourceId = CryptoUtils.randomId();
    mapper.insertImportedSource(
        sourceId,
        string(dataset, "dataset_name"),
        string(dataset, "source_type"),
        string(dataset, "source_url"),
        string(dataset, "license_note"));
    String stage = string(mapper.findBookLevel(string(batch, "target_book_id")), "level_code");
    int sort = mapper.findLastBookSort(string(batch, "target_book_id"));
    for (Map<String, Object> item : mapper.listPublishableItems(batchId)) {
      String contentId = string(item, "content_id");
      // A different batch may have published the word meanwhile: preserve that version.
      if ("create".equals(string(item, "decision"))) {
        Map<String, Object> existing =
            mapper.findWordByHash(
                new com.zhixing.mapper.type.BinaryHash(
                    CryptoUtils.sha256(string(item, "normalized_word"))));
        if (existing != null) contentId = string(existing, "id");
        else {
          Map<String, Object> raw = readMap(string(item, "raw_json"));
          VocabularyEntry entry = new VocabularyEntry(raw);
          entry.validate();
          if (bool(raw.get("requiresContentReview")))
            throw fail(HttpStatus.CONFLICT, "WORD_CONTENT_REVIEW_REQUIRED", "例句义项未确认，禁止发布");
          contentId = CryptoUtils.randomId();
          String version = CryptoUtils.randomId(), term = string(item, "normalized_word");
          String meaning = VocabularyEntry.text(entry.senses.get(0).get("meaning"), "");
          String summary = meaning.length() > 500 ? meaning.substring(0, 500) : meaning;
          List<Map<String, Object>> firstExamples = entry.examples(1);
          String example =
              firstExamples.isEmpty()
                  ? null
                  : VocabularyEntry.text(firstExamples.get(0).get("sentence"), "");
          String translation =
              firstExamples.isEmpty()
                  ? null
                  : VocabularyEntry.text(firstExamples.get(0).get("translation"), "");
          mapper.insertPublishedWord(
              contentId,
              sourceId,
              new com.zhixing.mapper.type.BinaryHash(
                  CryptoUtils.sha256("import:" + batchId + ":" + term)),
              new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(term)),
              stage,
              version,
              version);
          mapper.insertApprovedVersion(
              version,
              contentId,
              term,
              summary,
              term,
              entry.phoneticUk,
              summary,
              example,
              translation,
              string(dataset, "source_url"),
              string(dataset, "license_note"),
              new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(write(raw))),
              adminId);
          Map<Integer, String> senseIds = new HashMap<Integer, String>();
          Map<String, String> exampleIds = new HashMap<String, String>();
          for (int sn = 1; sn <= entry.senses.size(); sn++) {
            Map<String, Object> sense = entry.senses.get(sn - 1);
            String senseId = CryptoUtils.randomId();
            senseIds.put(sn, senseId);
            mapper.insertWordSense(
                senseId,
                version,
                VocabularyEntry.text(sense.get("partOfSpeech"), "unknown"),
                sense.get("meaning"),
                sn);
            int en = 0;
            for (Map<String, Object> ex : entry.examples(sn)) {
              String eid = CryptoUtils.randomId();
              exampleIds.put(sn + ":" + (++en), eid);
              mapper.insertWordExample(
                  eid,
                  senseId,
                  ex.get("sentence"),
                  VocabularyEntry.text(ex.get("translation"), ""),
                  en);
            }
          }
          for (Map<String, Object> task : mapper.listCompletedItemAudioTasks(string(item, "id"))) {
            boolean ex = "example".equals(string(task, "target_type"));
            int sn = number(value(task, "sense_no"), 1),
                en = Math.max(1, number(value(task, "example_no"), 1));
            String sid = senseIds.get(sn), eid = ex ? exampleIds.get(sn + ":" + en) : null;
            if (sid == null || (ex && eid == null))
              throw fail(HttpStatus.CONFLICT, "TTS_TARGET_INVALID", "语音任务与词条结构不匹配");
            mapper.insertPronunciation(
                CryptoUtils.randomId(),
                version,
                ex ? null : sid,
                eid,
                (ex ? "example:" : "sense:") + (ex ? eid : sid),
                string(task, "accent"),
                ex
                    ? null
                    : ("uk".equals(string(task, "accent")) ? entry.phoneticUk : entry.phoneticUs),
                string(task, "asset_id"));
          }
        }
        mapper.attachItemContent(contentId, string(item, "id"));
      }
      try {
        mapper.insertBookWord(
            CryptoUtils.randomId(),
            string(batch, "target_book_id"),
            contentId,
            ++sort,
            "import:" + batchId);
      } catch (DuplicateKeyException ignored) {
      }
    }
    mapper.refreshBookWordCount(string(batch, "target_book_id"), string(batch, "target_book_id"));
  }

  private void createTtsTasks(
      String batchId,
      String itemId,
      String word,
      Map<String, Object> raw,
      Map<String, Object> options) {
    VocabularyEntry entry = new VocabularyEntry(raw);
    entry.validate();
    for (int sn = 1; sn <= entry.senses.size(); sn++) {
      for (String accent : Arrays.asList("us", "uk")) {
        String voice =
            nonBlank(options.get(accent + "Voice"), "us".equals(accent) ? "eva" : "luna");
        String prefix =
            "english/audio/import/"
                + batchId
                + "/"
                + UUID.nameUUIDFromBytes(word.getBytes(StandardCharsets.UTF_8)).toString()
                + "/sense-"
                + sn
                + "/";
        task(batchId, itemId, "sense", sn, 0, accent, voice, prefix + accent + ".mp3");
        for (int en = 1; en <= entry.examples(sn).size(); en++)
          task(
              batchId,
              itemId,
              "example",
              sn,
              en,
              accent,
              voice,
              prefix + "example-" + en + "/" + accent + ".mp3");
      }
    }
  }

  private void task(
      String batchId,
      String itemId,
      String target,
      int sn,
      int en,
      String accent,
      String voice,
      String key) {
    try {
      mapper.insertAudioTask(
          CryptoUtils.randomId(), batchId, itemId, target, sn, en, accent, voice, key);
    } catch (DuplicateKeyException ignored) {
    }
  }

  private void markFailed(String id, String code, String message) {
    mapper.markBatchFailed(code, message, id);
    // Never log uploaded payloads, synthesized text, administrator tokens or provider errors.
    LOGGER.warn("Vocabulary import batch marked failed");
  }

  private Map<String, Object> dataset(Map<String, Object> r) {
    Map<String, Object> x = new LinkedHashMap<String, Object>();
    x.put("datasetId", string(r, "id"));
    x.put("datasetCode", string(r, "dataset_code"));
    x.put("datasetName", string(r, "dataset_name"));
    x.put("providerName", string(r, "provider_name"));
    x.put("sourceType", string(r, "source_type"));
    x.put("sourceUrl", string(r, "source_url"));
    x.put("dataFormat", string(r, "data_format"));
    x.put("versionLabel", string(r, "version_label"));
    x.put("licenseStatus", string(r, "license_status"));
    x.put("licenseNote", string(r, "license_note"));
    x.put("itemCount", number(value(r, "item_count"), 0));
    x.put("updatedAt", value(r, "updated_at"));
    return x;
  }

  private Map<String, Object> batchRow(Map<String, Object> r) {
    Map<String, Object> x = new LinkedHashMap<String, Object>();
    x.put("batchId", string(r, "id"));
    x.put("datasetId", string(r, "dataset_id"));
    x.put("datasetName", string(r, "dataset_name"));
    x.put("licenseStatus", string(r, "license_status"));
    x.put("targetBookId", string(r, "target_book_id"));
    x.put("targetBookName", string(r, "book_name"));
    x.put("state", string(r, "state"));
    x.put("currentStep", string(r, "current_step"));
    x.put("options", readMap(string(r, "options_json")));
    for (String key :
        Arrays.asList(
            "total_count",
            "success_count",
            "failed_count",
            "reused_count",
            "created_count",
            "alias_count",
            "skipped_count",
            "review_count")) x.put(camel(key), number(value(r, key), 0));
    x.put("lastErrorCode", string(r, "last_error_code"));
    x.put("lastErrorMessage", string(r, "last_error_message"));
    x.put("startedAt", value(r, "started_at"));
    x.put("finishedAt", value(r, "finished_at"));
    x.put("updatedAt", value(r, "updated_at"));
    return x;
  }

  private Map<String, Object> itemRow(Map<String, Object> r) {
    Map<String, Object> x = new LinkedHashMap<String, Object>();
    x.put("itemId", string(r, "id"));
    x.put("rowNo", number(value(r, "row_no"), 0));
    x.put("wordTerm", string(r, "word_term"));
    x.put("normalizedWord", string(r, "normalized_word"));
    x.put("decision", string(r, "decision"));
    x.put("contentId", string(r, "content_id"));
    x.put("reviewStatus", string(r, "review_status"));
    x.put("riskFlags", readList(string(r, "risk_flags_json")));
    x.put("errorCode", string(r, "error_code"));
    x.put("errorMessage", string(r, "error_message"));
    return x;
  }

  private List<Map<String, Object>> parse(String payload) {
    if (payload == null || payload.trim().isEmpty())
      throw fail(HttpStatus.BAD_REQUEST, "DATASET_PAYLOAD_EMPTY", "数据源没有可解析的 CSV 或 JSON 内容");
    String input = payload.trim();
    try {
      if (input.startsWith("[")) {
        List<Map<String, Object>> values =
            json.readValue(input, new TypeReference<List<Map<String, Object>>>() {});
        if (values == null || values.isEmpty())
          throw fail(HttpStatus.BAD_REQUEST, "DATASET_PAYLOAD_EMPTY", "数据源中没有词条");
        return values;
      }
      return csv(input);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw fail(
          HttpStatus.BAD_REQUEST, "DATASET_PARSE_FAILED", "数据源格式不正确，请提供 JSON 数组或含 word 列的 CSV");
    }
  }

  private List<Map<String, Object>> csv(String input) {
    List<String> lines = Arrays.asList(input.replace("\r\n", "\n").replace('\r', '\n').split("\n"));
    if (lines.size() < 2)
      throw fail(HttpStatus.BAD_REQUEST, "DATASET_PARSE_FAILED", "CSV 至少需要表头和一行数据");
    List<String> heads = cells(lines.get(0));
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).trim().isEmpty()) continue;
      List<String> values = cells(lines.get(i));
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      for (int n = 0; n < heads.size(); n++)
        row.put(
            heads.get(n).trim().toLowerCase(Locale.ROOT),
            n < values.size() ? values.get(n).trim() : "");
      out.add(row);
    }
    if (out.isEmpty()) throw fail(HttpStatus.BAD_REQUEST, "DATASET_PAYLOAD_EMPTY", "CSV 中没有词条");
    return out;
  }

  private List<String> cells(String line) {
    List<String> out = new ArrayList<String>();
    StringBuilder b = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
          b.append(c);
          i++;
        } else quoted = !quoted;
      } else if (c == ',' && !quoted) {
        out.add(b.toString());
        b.setLength(0);
      } else b.append(c);
    }
    if (quoted) throw fail(HttpStatus.BAD_REQUEST, "DATASET_PARSE_FAILED", "CSV 引号未闭合");
    out.add(b.toString());
    return out;
  }

  private String normalize(String raw) {
    if (raw == null) return null;
    String x = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    return x.matches("[a-z][a-z' -]{0,79}") ? x : null;
  }

  private String objectPart(String word) {
    return word.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  private Map<String, Object> readMap(String source) {
    try {
      return source == null
          ? new LinkedHashMap<String, Object>()
          : json.readValue(source, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<String, Object>();
    }
  }

  private List<Object> readList(String source) {
    try {
      return source == null
          ? Collections.emptyList()
          : json.readValue(source, new TypeReference<List<Object>>() {});
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("JSON serialization failed", e);
    }
  }

  private void audit(
      String admin,
      String action,
      String type,
      String id,
      String result,
      Map<String, Object> meta) {
    mapper.insertAudit(
        CryptoUtils.randomId(),
        admin,
        action,
        type,
        id,
        result,
        write(meta),
        Timestamp.from(Instant.now().plusSeconds(90L * 24 * 3600)));
  }

  private Object value(Map<String, Object> row, String key) {
    if (row == null) return null;
    for (Map.Entry<String, Object> e : row.entrySet())
      if (key.equalsIgnoreCase(e.getKey())) return e.getValue();
    return null;
  }

  private String string(Map<String, Object> row, String key) {
    Object value = value(row, key);
    return value == null ? null : String.valueOf(value);
  }

  private String nullable(Object value) {
    String s = value == null ? null : String.valueOf(value).trim();
    return s == null || s.isEmpty() ? null : s;
  }

  private String text(String value, int max, String label) {
    String x = nullable(value);
    if (x == null || x.length() > max)
      throw fail(HttpStatus.BAD_REQUEST, "INVALID_DATASET_FIELD", label + "不能为空且长度不能超过 " + max);
    return x;
  }

  private String choice(String value, List<String> options, String label) {
    String x = nullable(value);
    if (x == null || !options.contains(x))
      throw fail(HttpStatus.BAD_REQUEST, "INVALID_DATASET_FIELD", label + "不正确");
    return x;
  }

  private String nonBlank(Object value, String fallback) {
    String s = nullable(value);
    return s == null ? fallback : s;
  }

  private int number(Object value, int fallback) {
    if (value instanceof Number) return ((Number) value).intValue();
    try {
      return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      return fallback;
    }
  }

  private boolean bool(Object value) {
    return value instanceof Boolean
        ? (Boolean) value
        : "true".equalsIgnoreCase(String.valueOf(value));
  }

  private String camel(String value) {
    StringBuilder out = new StringBuilder();
    boolean upper = false;
    for (char c : value.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        out.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return out.toString();
  }

  private ApiException fail(HttpStatus status, String code, String message) {
    return new ApiException(status, code, message);
  }
}
