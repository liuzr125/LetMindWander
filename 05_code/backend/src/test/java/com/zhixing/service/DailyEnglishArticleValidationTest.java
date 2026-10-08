package com.zhixing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class DailyEnglishArticleValidationTest {
  private static final String BODY =
      "Every morning our happy family walks to school. I carry a book and an apple. We learn how to keep the garden clean, share simple work, and help new friends. The small project gives us a dream for a greener home and a kinder day together.";
  private static final LocalDate DATE = LocalDate.of(2026, 10, 8);

  @Test
  void coverageFillDoesNotCallModelWhenBothLevelsHaveReachedTarget() {
    try (Fixture f = new Fixture()) {
      f.seedArticles("intro", 101);
      f.seedArticles("advanced", 105);
      assertThat(f.service.fillCoverage())
          .containsEntry("generatedCount", 0)
          .containsEntry("remainingCount", 0);
      verifyNoInteractions(f.ai);
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM english_article_generation_run", Integer.class))
          .isZero();
    }
  }

  @Test
  void coverageFillGeneratesOnlyGlobalDeficitAndIsSafeToRepeat() throws Exception {
    try (Fixture f = new Fixture()) {
      f.seedArticles("intro", 100);
      f.seedArticles("advanced", 101);
      when(f.parameters.optionalStored(eq("article_generation.enabled"), anyString()))
          .thenReturn("true");
      when(f.ai.generateSystemContent(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(response(articles(1, 1)));
      Map<String, Object> result = f.service.fillCoverage();
      assertThat(result).containsEntry("generatedCount", 1).containsEntry("remainingCount", 0);
      assertThat(f.service.fillCoverage()).containsEntry("generatedCount", 0);
      verify(f.ai, times(1))
          .generateSystemContent(anyString(), anyString(), anyString(), anyString());
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM english_article_generation_run", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void coverageFillReportsMissingLevelWithoutAnEligibleBook() throws Exception {
    try (Fixture f = new Fixture()) {
      f.seedArticles("intro", 101);
      f.seedArticles("advanced", 100);
      when(f.parameters.optionalStored(eq("article_generation.enabled"), anyString()))
          .thenReturn("true");
      Map<String, Object> result = f.service.fillCoverage();
      assertThat(result).containsEntry("generatedCount", 0).containsEntry("remainingCount", 1);
      verifyNoInteractions(f.ai);
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT state FROM english_article_generation_run", String.class))
          .isEqualTo("failed");
    }
  }

  @Test
  void regeneratesOnlyTheInvalidSlotAndPersistsItsValidatedSiblings() throws Exception {
    try (Fixture f = new Fixture()) {
      List<Map<String, Object>> first = articles(1, 5);
      first.get(2).put("body", "");
      when(f.ai.generateSystemContent(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(response(first), response(articles(3, 1)));
      Map<String, Object> run = f.service.generate(DATE, "validation");
      assertThat(run.get("state")).isEqualTo("success");
      assertThat(((Number) run.get("generated_count")).intValue()).isEqualTo(5);
      assertThat(f.slots()).containsExactly(1, 2, 3, 4, 5);
      verify(f.ai)
          .generateSystemContent(
              anyString(),
              contains("Create exactly 1 replacement"),
              endsWith(":repair"),
              anyString());
      verify(f.ai, times(2))
          .generateSystemContent(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Test
  void failedRepairKeepsGoodArticlesAndNextRunFillsTheActualHole() throws Exception {
    try (Fixture f = new Fixture()) {
      List<Map<String, Object>> first = articles(1, 5);
      first.get(2).put("summaryZh", "");
      List<Map<String, Object>> repair = articles(3, 1);
      repair.get(0).put("summaryZh", "");
      when(f.ai.generateSystemContent(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(response(first), response(repair), response(articles(3, 1)));
      Map<String, Object> partial = f.service.generate(DATE, "partial");
      assertThat(partial.get("state")).isEqualTo("partial");
      assertThat(((Number) partial.get("generated_count")).intValue()).isEqualTo(4);
      assertThat(((Number) partial.get("failed_count")).intValue()).isEqualTo(1);
      assertThat(String.valueOf(partial.get("error_message")))
          .contains("高中英语", "槽位 3", "summaryZh", "长度 0", "2–500", "补生成一次");
      assertThat(f.slots()).containsExactly(1, 2, 4, 5);
      Map<String, Object> resumed = f.service.generate(DATE, "resume");
      assertThat(resumed.get("state")).isEqualTo("success");
      assertThat(((Number) resumed.get("generated_count")).intValue()).isEqualTo(1);
      assertThat(((Number) resumed.get("skipped_count")).intValue()).isEqualTo(4);
      assertThat(f.slots()).containsExactly(1, 2, 3, 4, 5);
      verify(f.ai, times(3))
          .generateSystemContent(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Test
  void wordCountIsCheckedEvenWhenTheCharacterLengthPasses() throws Exception {
    try (Fixture f = new Fixture()) {
      List<Map<String, Object>> first = articles(1, 5);
      first
          .get(0)
          .put("body", "apple book garden " + String.join(" ", Collections.nCopies(85, "school")));
      List<Map<String, Object>> repair = articles(1, 1);
      repair.get(0).put("body", first.get(0).get("body"));
      when(f.ai.generateSystemContent(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(response(first), response(repair));
      Map<String, Object> run = f.service.generate(DATE, "word-count");
      assertThat(run.get("state")).isEqualTo("partial");
      assertThat(String.valueOf(run.get("error_message"))).contains("槽位 1", "单词数 88", "45–80");
      assertThat(f.slots()).containsExactly(2, 3, 4, 5);
    }
  }

  @Test
  void aProviderFailureDuringRepairDoesNotDiscardValidatedArticles() throws Exception {
    try (Fixture f = new Fixture()) {
      List<Map<String, Object>> first = articles(1, 5);
      first.get(1).remove("body");
      when(f.ai.generateSystemContent(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(response(first))
          .thenThrow(new IllegalStateException("模型服务暂不可用"));
      Map<String, Object> run = f.service.generate(DATE, "provider-failure");
      assertThat(run.get("state")).isEqualTo("partial");
      assertThat(((Number) run.get("failed_count")).intValue()).isEqualTo(1);
      assertThat(String.valueOf(run.get("error_message"))).contains("槽位 2", "字段 body 缺失", "补生成失败");
      assertThat(f.slots()).containsExactly(1, 3, 4, 5);
    }
  }

  private List<Map<String, Object>> articles(int first, int count) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (int i = first; i < first + count; i++) {
      Map<String, Object> article = new LinkedHashMap<String, Object>();
      article.put("title", "Garden Story " + i);
      article.put("titleTranslationZh", "校园花园故事 " + i);
      article.put("summaryZh", "校园花园学习故事");
      article.put("body", BODY);
      article.put("translationZh", "每天早晨，我们幸福的一家人走向学校，在校园花园里读书学习并帮助朋友。");
      result.add(article);
    }
    return result;
  }

  private String response(List<Map<String, Object>> articles) throws Exception {
    return new ObjectMapper().writeValueAsString(Collections.singletonMap("articles", articles));
  }

  private static final class Fixture implements AutoCloseable {
    final EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(
                "article-validation-"
                    + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
            .addScript("schema.sql")
            .build();
    final JdbcTemplate jdbc = new JdbcTemplate(db);
    final AiService ai = mock(AiService.class);
    final AppParameterService parameters = mock(AppParameterService.class);
    final String book = CryptoUtils.randomId();
    final DailyEnglishArticleService service;

    Fixture() {
      when(parameters.optionalStored(anyString(), anyString()))
          .thenAnswer(call -> call.getArgument(1));
      AppProperties properties = new AppProperties();
      properties.getArticleGeneration().setPerBookCount(5);
      service =
          new DailyEnglishArticleService(
              com.zhixing.MapperTestSupport.mapper(
                  jdbc.getDataSource(),
                  com.zhixing.mapper.DailyEnglishArticlePersistenceMapper.class),
              new ObjectMapper(),
              ai,
              parameters,
              properties,
              new DataSourceTransactionManager(db));
      String source = "e0000000000000000000000000000017",
          rss = CryptoUtils.randomId(),
          topic = CryptoUtils.randomId(),
          version = CryptoUtils.randomId();
      jdbc.update(
          "INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'原创','ai_original','原创')",
          source);
      jdbc.update(
          "INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'RSS','rss','选题')",
          rss);
      jdbc.update(
          "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'tech',?,?,'published',?,?)",
          topic,
          rss,
          CryptoUtils.sha256(topic),
          version,
          version);
      jdbc.update(
          "INSERT INTO content_version(id,content_id,version_no,title,body,origin_url,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'School Garden','Topic','https://example.test/topic','选题',?,'approved',?)",
          version,
          topic,
          CryptoUtils.sha256(version),
          source);
      jdbc.update(
          "INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,state) VALUES(?,'VALIDATION_SENIOR','高中英语','official','senior','active')",
          book);
      String[] terms = {
        "apple", "book", "clean", "dream", "family", "garden", "happy", "learn", "morning", "school"
      };
      for (String term : terms) {
        String content = CryptoUtils.randomId(), wordVersion = CryptoUtils.randomId();
        jdbc.update(
            "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'word',?,?,'published',?,?)",
            content,
            source,
            CryptoUtils.sha256(content),
            wordVersion,
            wordVersion);
        jdbc.update(
            "INSERT INTO content_version(id,content_id,version_no,title,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,'释义','原创',?,'approved',?)",
            wordVersion,
            content,
            term,
            term,
            CryptoUtils.sha256(wordVersion),
            source);
        jdbc.update(
            "INSERT INTO vocabulary_book_word(id,book_id,content_id) VALUES(?,?,?)",
            CryptoUtils.randomId(),
            book,
            content);
      }
    }

    List<Integer> slots() {
      return jdbc.queryForList(
          "SELECT slot_no FROM english_article_book WHERE book_id=? AND generated_date=? ORDER BY slot_no",
          Integer.class,
          book,
          DATE);
    }

    void seedArticles(String difficulty, int count) {
      for (int i = 0; i < count; i++) {
        String content = CryptoUtils.randomId(), version = CryptoUtils.randomId();
        jdbc.update(
            "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article','e0000000000000000000000000000017',?,'published',?,?)",
            content,
            CryptoUtils.sha256(content),
            version,
            version);
        jdbc.update(
            "INSERT INTO content_version(id,content_id,version_no,title,body,difficulty,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,'Test body',?,'原创',?,'approved','e0000000000000000000000000000017')",
            version,
            content,
            "Existing " + i,
            difficulty,
            CryptoUtils.sha256(version));
      }
    }

    public void close() {
      db.shutdown();
    }
  }
}
