package com.zhixing.service;

import com.fasterxml.jackson.databind.*;
import com.zhixing.common.*;
import com.zhixing.mapper.OnlineVocabularyCatalogPersistenceMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OnlineVocabularyCatalogService {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OnlineVocabularyCatalogService.class);
  private final OnlineVocabularyCatalogPersistenceMapper mapper;
  private final ObjectMapper json;
  private final OnlineVocabularyDownloader downloader;
  private final TransactionTemplate tx;

  public OnlineVocabularyCatalogService(
      OnlineVocabularyCatalogPersistenceMapper mapper,
      ObjectMapper json,
      OnlineVocabularyDownloader downloader,
      PlatformTransactionManager manager) {
    this.mapper = mapper;
    this.json = json;
    this.downloader = downloader;
    this.tx = new TransactionTemplate(manager);
  }

  public void initializeCatalogue() {
    try (java.io.InputStream in =
        new ClassPathResource("vocabulary-online-books.json").getInputStream()) {
      for (JsonNode b : json.readTree(in)) {
        // Never overwrite an operator's licence decision, snapshot pointer or soft-delete mark.
        try {
          mapper.insertCatalogBook(
              s(b, "bookCode"),
              s(b, "bookName"),
              s(b, "editionLabel"),
              s(b, "providerName"),
              s(b, "category"),
              b.path("sortNo").asInt(),
              s(b, "sourceUrl"),
              s(b, "downloadPath"),
              s(b, "sourceRevision"),
              b.path("expectedCount").isNumber() ? b.get("expectedCount").asInt() : null,
              s(b, "licenseStatus"),
              s(b, "licenseNote"));
        } catch (DuplicateKeyException ignored) {
          // Public catalogue metadata follows the reviewed manifest; operator licence decisions and
          // snapshots stay untouched.
          mapper.updateCatalogBook(
              s(b, "bookName"),
              s(b, "editionLabel"),
              s(b, "providerName"),
              s(b, "category"),
              b.path("sortNo").asInt(),
              s(b, "sourceUrl"),
              s(b, "downloadPath"),
              s(b, "sourceRevision"),
              b.path("expectedCount").isNumber() ? b.get("expectedCount").asInt() : null,
              s(b, "bookCode"));
        }
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Catalogue manifest unavailable", e);
    }
  }

  public List<Map<String, Object>> books() {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> r : mapper.listCatalogBooks()) {
      Map<String, Object> v = new LinkedHashMap<String, Object>();
      for (String key :
          Arrays.asList(
              "book_code",
              "book_name",
              "edition_label",
              "provider_name",
              "category",
              "sort_no",
              "source_url",
              "source_revision",
              "expected_count",
              "license_status",
              "license_note",
              "latest_dataset_id",
              "actual_count",
              "quality_summary",
              "last_synced_at")) v.put(camel(key), r.get(key));
      v.put("downloadable", r.get("download_path") != null);
      result.add(v);
    }
    return result;
  }

  public Map<String, Object> words(String code, int page, int size, String keyword) {
    page = Math.max(1, page);
    size = Math.max(1, Math.min(100, size));
    Map<String, Object> book = book(code);
    Object dataset = book.get("latest_dataset_id");
    String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    if (term.length() > 80) throw bad("INVALID_KEYWORD", "搜索词过长");
    // LOCATE treats % and _ literally and keeps the query parameterized.
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("total", mapper.countMatchingWords(dataset, term));
    out.put("page", page);
    out.put("pageSize", size);
    out.put("items", mapper.listMatchingWords(dataset, term, size, (page - 1) * size));
    return out;
  }

  public Map<String, Object> sync(String code, String admin) {
    Map<String, Object> b = book(code);
    if (b.get("download_path") == null)
      throw bad("BOOK_METADATA_ONLY", "该热门教材只有书目记录，尚无已核对的电子词表；不能用通用数据冒充");
    byte[] zip =
        downloader.download((String) b.get("source_revision"), (String) b.get("download_path"));
    String raw = downloader.unpack(zip, code);
    List<Map<String, Object>> rows = normalize(raw, code);
    if (rows.isEmpty() || rows.size() > 15000)
      throw bad("ONLINE_ROW_COUNT_INVALID", "词表必须包含 1 至 15000 行");
    if (b.get("expected_count") != null
        && rows.size() != ((Number) b.get("expected_count")).intValue())
      throw bad("ONLINE_COUNT_MISMATCH", "下载行数与锁定目录不一致，停止入库以防不完整数据");
    final String payload = write(rows);
    // Content-derived snapshot identity makes retries reuse the same immutable dataset.
    final String snapshot =
        UUID.nameUUIDFromBytes(
                (code + ":" + b.get("source_revision") + ":" + payload)
                    .getBytes(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "");
    return tx.execute(
        status -> {
          Map<String, Object> locked = mapper.lockCatalogBook(code);
          Integer exists = mapper.countDatasetSnapshot(snapshot);
          if (exists == 0) {
            mapper.insertDatasetSnapshot(
                snapshot,
                "online-" + snapshot,
                locked.get("book_name") + " · " + code,
                locked.get("provider_name"),
                "online_snapshot",
                locked.get("source_url"),
                "json",
                "word-entry-v1",
                code + "@" + locked.get("source_revision").toString().substring(0, 12),
                locked.get("license_note"),
                new com.zhixing.mapper.type.BinaryHash(CryptoUtils.sha256(payload)),
                rows.size());
            int i = 0;
            for (Map<String, Object> row : rows) {
              Map<String, Object> normalized = new LinkedHashMap<String, Object>(row);
              normalized.remove(
                  "sourceRecord"); // Raw evidence has its own column; avoid duplicating whole
              // dictionaries.
              mapper.insertSnapshotWord(
                  CryptoUtils.randomId(),
                  snapshot,
                  code,
                  ++i,
                  row.get("sourceWordId"),
                  row.get("word"),
                  row.get("word").toString().toLowerCase(Locale.ROOT),
                  row.get("phoneticUs"),
                  row.get("phoneticUk"),
                  write(normalized),
                  write(row.get("sourceRecord")),
                  write(row.get("qualityFlags")));
            }
          }
          Map<String, Object> quality = new LinkedHashMap<String, Object>();
          quality.put("rows", rows.size());
          quality.put(
              "missingPhonetic",
              rows.stream()
                  .filter(r -> empty(r.get("phoneticUs")) || empty(r.get("phoneticUk")))
                  .count());
          quality.put(
              "missingExample",
              rows.stream().filter(r -> ((List<?>) r.get("sourceExamples")).isEmpty()).count());
          quality.put(
              "needsSenseMapping",
              rows.stream()
                  .filter(r -> Boolean.TRUE.equals(r.get("requiresContentReview")))
                  .count());
          quality.put("note", "来源音标尚未核对；目录数量不代表当年考纲覆盖率");
          mapper.completeBookSync(snapshot, rows.size(), write(quality), code);
          mapper.insertSyncAudit(
              CryptoUtils.randomId(),
              admin,
              "online_vocabulary_sync",
              "vocabulary_dataset",
              snapshot,
              write(quality),
              java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(90L * 86400)));
          Map<String, Object> result = new LinkedHashMap<String, Object>();
          result.put("datasetId", snapshot);
          result.put("itemCount", rows.size());
          result.put("reusedSnapshot", exists > 0);
          result.put("quality", quality);
          LOGGER.debug(
              "Online vocabulary sync prepared: rows={}, reusedSnapshot={}",
              rows.size(),
              exists > 0);
          return result;
        });
  }
  /**
   * Canonical contract preserves every sense/example. Unmapped examples never get silently
   * assigned.
   */
  public List<Map<String, Object>> normalize(String input, String code) {
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    Set<String> seen = new HashSet<String>();
    try {
      for (String line : input.split("\\r?\\n")) {
        if (line.trim().isEmpty()) continue;
        JsonNode root = json.readTree(line);
        if (!code.equals(s(root, "bookId"))) throw bad("ONLINE_BOOK_MISMATCH", "下载内容不属于所选词书");
        JsonNode content = root.path("content").path("word").path("content");
        String word = s(root, "headWord");
        if (word == null || word.trim().isEmpty() || word.length() > 80)
          throw bad("ONLINE_WORD_INVALID", "来源包含空词头或超长词头");
        word = word.trim();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        List<String> flags = new ArrayList<String>();
        row.put("schemaVersion", "word-entry-v1");
        row.put("word", word);
        row.put("sourceWordId", s(root.path("content").path("word"), "wordId"));
        row.put("phoneticUs", s(content, "usphone"));
        row.put("phoneticUk", s(content, "ukphone"));
        row.put(
            "phonetic",
            s(content, "ukphone") != null ? s(content, "ukphone") : s(content, "usphone"));
        row.put("phoneticStatus", "source_unverified");
        flags.add("phonetic_source_unverified");
        if (empty(row.get("phoneticUs")) || empty(row.get("phoneticUk")))
          flags.add("missing_phonetic");
        if (!seen.add(word.toLowerCase(Locale.ROOT))) flags.add("duplicate_word");
        List<Map<String, Object>> senses = new ArrayList<Map<String, Object>>();
        for (JsonNode t : content.path("trans")) {
          if (s(t, "tranCn") == null) continue;
          Map<String, Object> sense = new LinkedHashMap<String, Object>();
          sense.put("partOfSpeech", s(t, "pos") == null ? "unknown" : s(t, "pos"));
          sense.put("meaning", s(t, "tranCn"));
          sense.put("examples", new ArrayList<Map<String, Object>>());
          senses.add(sense);
        }
        List<Map<String, Object>> examples = new ArrayList<Map<String, Object>>();
        for (JsonNode e : content.path("sentence").path("sentences")) {
          if (s(e, "sContent") == null) continue;
          Map<String, Object> example = new LinkedHashMap<String, Object>();
          example.put("sentence", s(e, "sContent"));
          example.put("translation", s(e, "sCn") == null ? "" : s(e, "sCn"));
          examples.add(example);
        }
        boolean review = senses.isEmpty() || (senses.size() > 1 && !examples.isEmpty());
        if (senses.isEmpty()) flags.add("missing_meaning");
        if (senses.size() > 1 && !examples.isEmpty()) flags.add("example_sense_mapping_required");
        if (examples.isEmpty()) flags.add("missing_example");
        if (senses.size() == 1) senses.get(0).put("examples", examples);
        row.put("senses", senses);
        row.put("sourceExamples", examples);
        row.put("requiresContentReview", review);
        if (!senses.isEmpty()) {
          row.put("pos", senses.get(0).get("partOfSpeech"));
          row.put("meaning", senses.get(0).get("meaning"));
        }
        if (!examples.isEmpty()) {
          row.put("example", examples.get(0).get("sentence"));
          row.put("translation", examples.get(0).get("translation"));
        }
        row.put("qualityFlags", flags);
        row.put("sourceRecord", json.convertValue(root, Map.class));
        out.add(row);
        if (out.size() > 15000) throw bad("ONLINE_ROW_LIMIT", "单次最多下载 15000 行");
      }
    } catch (java.io.IOException e) {
      throw bad("ONLINE_JSON_INVALID", "来源不是有效逐行 JSON，未保存任何快照");
    }
    return out;
  }

  private Map<String, Object> book(String code) {
    List<Map<String, Object>> rows = mapper.findCatalogBook(code);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "ONLINE_BOOK_NOT_FOUND", "在线词书不存在");
    return rows.get(0);
  }

  private boolean empty(Object x) {
    return x == null || x.toString().trim().isEmpty();
  }

  private String s(JsonNode n, String k) {
    return n.hasNonNull(k) && !n.get(k).asText().trim().isEmpty() ? n.get(k).asText().trim() : null;
  }

  private String write(Object x) {
    try {
      return json.writeValueAsString(x);
    } catch (Exception e) {
      throw new IllegalStateException("JSON failed", e);
    }
  }

  private String camel(String x) {
    StringBuilder b = new StringBuilder();
    boolean up = false;
    for (char c : x.toCharArray()) {
      if (c == '_') {
        up = true;
      } else {
        b.append(up ? Character.toUpperCase(c) : c);
        up = false;
      }
    }
    return b.toString();
  }

  private ApiException bad(String c, String m) {
    return new ApiException(HttpStatus.BAD_REQUEST, c, m);
  }
}
