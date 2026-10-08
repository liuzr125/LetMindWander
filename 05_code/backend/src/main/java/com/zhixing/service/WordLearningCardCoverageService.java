package com.zhixing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.mapper.WordLearningCardCoverageMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deterministic baseline only. Semantic relations and quotations require separate evidence/review.
 */
@Service
public class WordLearningCardCoverageService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(WordLearningCardCoverageService.class);
  public static final String GENERATOR_VERSION = "school-cet-card-standard-v3";
  private static final Set<String> TARGET_LEVELS =
      new LinkedHashSet<String>(Arrays.asList("primary", "junior", "senior", "cet4", "cet6"));
  private final WordLearningCardCoverageMapper mapper;
  private final TransactionTemplate tx;
  private final ObjectMapper json;

  public WordLearningCardCoverageService(
      WordLearningCardCoverageMapper mapper,
      PlatformTransactionManager manager,
      ObjectMapper json) {
    this.mapper = mapper;
    this.tx = new TransactionTemplate(manager);
    this.json = json;
  }

  public Map<String, Object> generate() {
    return tx.execute(status -> generateInTransaction());
  }

  private Map<String, Object> generateInTransaction() {
    List<Map<String, Object>> targets = mapper.selectCoverageTargets();
    // 以发布版本去重，别名或多词书关联不会为同一版本重复生成训练卡。
    Map<String, Target> byVersion = new LinkedHashMap<String, Target>();
    for (Map<String, Object> row : targets) {
      Target t = new Target();
      t.version = text(row, "version_id");
      t.word = text(row, "word_term");
      t.meaning = text(row, "meaning");
      byVersion.put(t.version, t);
    }

    for (Map<String, Object> row : mapper.selectTargetScopes()) {
      Target t = byVersion.get(text(row, "version_id"));
      String level = text(row, "level_code");
      if (t != null && TARGET_LEVELS.contains(level)) t.scopes.add(level);
    }
    for (Map<String, Object> row : mapper.selectTargetSensesAndExamples()) {
      Target t = byVersion.get(text(row, "version_id"));
      if (t == null) continue;
      if (t.partOfSpeech == null) {
        t.partOfSpeech = clean(text(row, "part_of_speech"));
        String sense = clean(text(row, "sense_meaning"));
        if (sense != null) t.meaning = sense;
      }
      if (t.exampleId == null) t.exampleId = clean(text(row, "example_id"));
    }
    Map<String, Set<String>> keys = new HashMap<String, Set<String>>();
    for (Map<String, Object> row : mapper.selectExistingCardKeys())
      keys.computeIfAbsent(text(row, "content_version_id"), k -> new HashSet<String>())
          .add(text(row, "card_key"));

    List<Object[]> cards = new ArrayList<Object[]>();
    for (Target t : byVersion.values()) {
      Set<String> existing = keys.computeIfAbsent(t.version, k -> new HashSet<String>());
      if (!existing.contains("system-recall-v1")) cards.add(recall(t));
      if (!existing.contains("system-usage-v1")) cards.add(usage(t));
    }
    for (int offset = 0; offset < cards.size(); offset += 100)
      mapper.insertBaselineCards(
          insertBaselineCardsRows(cards.subList(offset, Math.min(offset + 100, cards.size()))));

    Map<String, CardState> states = new HashMap<String, CardState>();
    for (Map<String, Object> row : mapper.selectPublishedCardCounts()) {
      CardState state =
          states.computeIfAbsent(text(row, "content_version_id"), k -> new CardState());
      String type = text(row, "card_type");
      int amount = ((Number) row.get("amount")).intValue();
      state.count += amount;
      state.counts.put(type, amount);
      if ("mnemonic".equals(type)) state.mnemonic = true;
      if ("usage".equals(type)) state.usage = true;
    }
    boolean lexicalEvidenceLoaded = mapper.countImportedLexicalSnapshots() > 0;
    int complete = 0, examples = 0;
    List<Object[]> coverage = new ArrayList<Object[]>();
    for (Target t : byVersion.values()) {
      CardState state = states.getOrDefault(t.version, new CardState());
      boolean done = state.mnemonic && state.usage;
      if (done) complete++;
      if (t.exampleId != null) examples++;
      List<String> issues = new ArrayList<String>();
      if (!state.mnemonic) issues.add("missing_mnemonic");
      if (!state.usage) issues.add("missing_usage");
      if (t.exampleId == null) issues.add("missing_example_reference");
      int relationTypes = 0;
      for (String type : Arrays.asList("synonym", "confusable", "derivative"))
        if (state.counts.getOrDefault(type, 0) > 0) relationTypes++;
      String relationStatus =
          relationTypes == 3
              ? "complete"
              : (relationTypes > 0
                  ? "partial"
                  : (lexicalEvidenceLoaded ? "evidence_not_available" : "evidence_required"));
      for (String type : Arrays.asList("synonym", "confusable", "derivative"))
        if (state.counts.getOrDefault(type, 0) == 0) issues.add("missing_" + type);
      coverage.add(
          new Object[] {
            t.version,
            join(t.scopes),
            state.count,
            done ? "complete" : "blocked",
            t.exampleId == null ? "missing" : "linked",
            relationStatus,
            t.exampleId,
            GENERATOR_VERSION,
            toJson(issues)
          });
    }
    for (int offset = 0; offset < coverage.size(); offset += 100)
      mapper.upsertCoverage(
          upsertCoverageRows(coverage.subList(offset, Math.min(offset + 100, coverage.size()))));
    List<Object[]> typeCoverage = new ArrayList<Object[]>();
    for (Target t : byVersion.values()) {
      CardState state = states.getOrDefault(t.version, new CardState());
      for (String type : WordLearningCardPreferenceService.TYPES) {
        int count = state.counts.containsKey(type) ? state.counts.get(type) : 0;
        String requirement = requirement(type);
        String coverageStatus =
            count > 0 ? "available" : missingStatus(type, lexicalEvidenceLoaded);
        typeCoverage.add(
            new Object[] {
              t.version,
              type,
              requirement,
              count,
              coverageStatus,
              statusNote(type, count, lexicalEvidenceLoaded),
              GENERATOR_VERSION
            });
      }
    }
    for (int offset = 0; offset < typeCoverage.size(); offset += 100)
      mapper.upsertTypeCoverage(
          upsertTypeCoverageRows(
              typeCoverage.subList(offset, Math.min(offset + 100, typeCoverage.size()))));
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("targetWords", byVersion.size());
    result.put("insertedCards", cards.size());
    result.put("completeWords", complete);
    result.put("wordsWithLinkedExamples", examples);
    result.put("typeCoverageRows", typeCoverage.size());
    result.put("generatorVersion", GENERATOR_VERSION);
    LOG.info(
        "Learning-card baseline generation: targetCount={}, insertedCount={}, completeCount={}",
        byVersion.size(),
        cards.size(),
        complete);
    return result;
  }

  private Object[] recall(Target t) {
    String meaning = shortText(t.meaning, 180, "当前义项");
    String label = label(t);
    return new Object[] {
      id(t.version, "system-recall-v1"),
      t.version,
      "system-recall-v1",
      "mnemonic",
      label,
      "中英双向回想",
      "先遮住英文，只看主要释义“" + meaning + "”，说出并拼写这个单词；再反过来看到 " + t.word + "，说出一个主要意思。答不出时看答案，稍后再试。",
      "不看页面，说出“" + meaning + "”对应的英文，并逐字母拼写。",
      null,
      "original",
      "系统规则生成 · 词条双向回想",
      0,
      "original",
      "根据当前已审核词条的词头和主要释义生成；不声称词源、近义或派生关系。",
      GENERATOR_VERSION,
      "approved",
      GENERATOR_VERSION,
      900
    };
  }

  private Object[] usage(Target t) {
    return new Object[] {
      id(t.version, "system-usage-v1"),
      t.version,
      "system-usage-v1",
      "usage",
      label(t),
      "把例句变成自己的话",
      t.exampleId == null
          ? "暂时没有可引用的已发布例句。先用这个词口头说一个与你今天有关的短句；不确定时不要自行保存为标准例句。"
          : "先朗读上面的已发布例句，再只替换一个人物、地点、时间或数量，保留 " + t.word + " 的原有用法。最后遮住原句复述。",
      "用 " + t.word + " 说一句与你今天有关的话。",
      t.exampleId,
      t.exampleId == null ? "original" : "content",
      t.exampleId == null ? "系统规则生成 · 口头造句提示" : "当前已发布词条例句",
      t.exampleId == null ? 0 : 1,
      t.exampleId == null ? "original" : "inherited",
      t.exampleId == null
          ? "原创练习指令，不生成或冒充标准英文例句。"
          : "例句内容通过 example_id 读取当前词条既有审核版本，授权状态继承该词条；卡片不复制、不改写原记录。",
      GENERATOR_VERSION,
      "approved",
      GENERATOR_VERSION,
      910
    };
  }

  private String label(Target t) {
    String pos = clean(t.partOfSpeech);
    return shortText(
        (pos == null ? "词条" : pos) + " · " + shortText(t.meaning, 110, "主要义项"), 160, "词条 · 主要义项");
  }

  private String toJson(List<String> issues) {
    try {
      return json.writeValueAsString(issues);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String id(String version, String key) {
    return UUID.nameUUIDFromBytes(
            ("word-card:" + version + ":" + key).getBytes(StandardCharsets.UTF_8))
        .toString()
        .replace("-", "");
  }

  private String join(Set<String> values) {
    return String.join(",", values);
  }

  private String requirement(String type) {
    if ("mnemonic".equals(type) || "usage".equals(type)) return "required";
    if ("quote".equals(type)) return "optional";
    return "evidence_required";
  }

  private String missingStatus(String type, boolean evidenceLoaded) {
    if ("mnemonic".equals(type) || "usage".equals(type)) return "missing_required";
    if ("quote".equals(type)) return "optional_not_available";
    return evidenceLoaded ? "evidence_not_available" : "evidence_required";
  }

  private String statusNote(String type, int count, boolean evidenceLoaded) {
    if (count > 0) return "已有" + count + "张通过发布门禁的卡片";
    if ("quote".equals(type)) return "影视或歌词引用不是每词必填；需真实出处、精确定位与授权核验";
    if ("mnemonic".equals(type) || "usage".equals(type)) return "基础训练卡缺失，必须补齐后才算达标";
    return evidenceLoaded ? "已核对固定版本开放词汇数据与本库拼写比较，未找到可安全发布的对应关系" : "尚未导入可核验词汇关系证据，不允许按拼写自动编造词义关系";
  }

  private String shortText(String value, int max, String fallback) {
    String v = clean(value);
    if (v == null) return fallback;
    return v.length() <= max ? v : v.substring(0, max - 1) + "…";
  }

  private String clean(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString();
  }

  private static class Target {
    String version, word, meaning, partOfSpeech, exampleId;
    Set<String> scopes = new LinkedHashSet<String>();
  }

  private static class CardState {
    int count;
    boolean mnemonic, usage;
    Map<String, Integer> counts = new HashMap<String, Integer>();
  }

  private List<Map<String, Object>> insertBaselineCardsRows(List<Object[]> rows) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object[] row : rows) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", row[0]);
      item.put("versionId", row[1]);
      item.put("cardKey", row[2]);
      item.put("cardType", row[3]);
      item.put("senseLabel", row[4]);
      item.put("title", row[5]);
      item.put("body", row[6]);
      item.put("recallPrompt", row[7]);
      item.put("exampleId", row[8]);
      item.put("sourceKind", row[9]);
      item.put("sourceTitle", row[10]);
      item.put("sourceVerified", row[11]);
      item.put("rightsStatus", row[12]);
      item.put("rightsNote", row[13]);
      item.put("generatorVersion", row[14]);
      item.put("reviewStatus", row[15]);
      item.put("reviewedBy", row[16]);
      item.put("sortNo", row[17]);
      result.add(item);
    }
    return result;
  }

  private List<Map<String, Object>> upsertCoverageRows(List<Object[]> rows) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object[] row : rows) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("versionId", row[0]);
      item.put("targetScopes", row[1]);
      item.put("publishedCardCount", row[2]);
      item.put("baselineStatus", row[3]);
      item.put("exampleStatus", row[4]);
      item.put("relationStatus", row[5]);
      item.put("sourceExampleId", row[6]);
      item.put("generatorVersion", row[7]);
      item.put("issuesJson", row[8]);
      result.add(item);
    }
    return result;
  }

  private List<Map<String, Object>> upsertTypeCoverageRows(List<Object[]> rows) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object[] row : rows) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("versionId", row[0]);
      item.put("cardType", row[1]);
      item.put("requirementLevel", row[2]);
      item.put("publishedCount", row[3]);
      item.put("coverageStatus", row[4]);
      item.put("statusNote", row[5]);
      item.put("generatorVersion", row[6]);
      result.add(item);
    }
    return result;
  }
}
