package com.zhixing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.MapperTestSupport;
import com.zhixing.common.*;
import com.zhixing.config.AppProperties;
import com.zhixing.mapper.*;
import com.zhixing.model.CollectionRunView;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;

public class DailyTechnicalContentTest {
  @Test
  void officialNewMaterialsReachTenWithoutAiAndRepeatedRunCostsNothing() throws Exception {
    try (Fixture f = new Fixture()) {
      f.newsCount = 12;
      CollectionRunView first = f.service().runManually();
      assertThat(first.getState()).isEqualTo("success");
      assertThat(first.getInsertedCount()).isEqualTo(10);
      assertThat(first.getGeneratedCount()).isZero();
      assertThat(first.getDailyInsertedCount()).isEqualTo(10);
      int fetches = f.fetches.get();
      CollectionRunView second = f.service().runManually();
      assertThat(second.getInsertedCount()).isZero();
      assertThat(second.getRemainingCount()).isZero();
      assertThat(f.fetches.get()).isEqualTo(fetches);
      verifyNoInteractions(f.ai);
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM learning_content", Integer.class))
          .isEqualTo(10);
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM content_version WHERE review_status='pending'",
                  Integer.class))
          .isEqualTo(10);
    }
  }

  @Test
  void duplicateOfficialItemsBecomeInspirationAndAiFillsOnlyRemainingNine() throws Exception {
    try (Fixture f = new Fixture()) {
      f.newsCount = 3;
      f.seedOld(0);
      f.seedOld(1);
      f.validAi();
      CollectionRunView run = f.service().runManually();
      assertThat(run.getInsertedCount()).isEqualTo(10);
      assertThat(run.getGeneratedCount()).isEqualTo(9);
      assertThat(run.getSkippedCount()).isEqualTo(2);
      assertThat(run.getDailyInsertedCount()).isEqualTo(10);
      assertThat(run.getRemainingCount()).isZero();
      verify(f.ai, times(9))
          .generateSystemContent(
              anyString(),
              anyString(),
              anyString(),
              eq(run.getId()),
              eq("generate_tech_materials"),
              eq("content_collection"));
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM learning_content WHERE source_id='00000000000000000000000000000085' AND state='pending'",
                  Integer.class))
          .isEqualTo(9);
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM content_version WHERE review_status='approved'",
                  Integer.class))
          .isZero();
      f.service().runManually();
      assertThat(f.calls.get()).isEqualTo(9);
    }
  }

  @Test
  void feedOutageUsesStoredTrustedMetadataButNeverInventsANewOfficialAnnouncement()
      throws Exception {
    try (Fixture f = new Fixture()) {
      f.seedOld(0);
      f.feedFailure = true;
      f.validAi();
      CollectionRunView run = f.service().runManually();
      assertThat(run.getState()).isEqualTo("success");
      assertThat(run.getGeneratedCount()).isEqualTo(10);
      assertThat(run.getErrorCode()).isEqualTo("COLLECTION_SOURCE_WARNING");
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT COUNT(*) FROM content_version WHERE origin_author LIKE 'AI 原创%' AND origin_published_at IS NULL",
                  Integer.class))
          .isEqualTo(10);
    }
  }

  @Test
  void noTrustedReferenceNeverCallsAiOrFakesNewRows() throws Exception {
    try (Fixture f = new Fixture()) {
      CollectionRunView run = f.service().runManually();
      assertThat(run.getState()).isEqualTo("failed");
      assertThat(run.getRemainingCount()).isEqualTo(10);
      assertThat(run.getErrorMessage()).contains("没有可用的官方资料");
      verifyNoInteractions(f.ai);
    }
  }

  @Test
  void duplicateAiBodiesAreRejectedAndAttemptsAreBounded() throws Exception {
    try (Fixture f = new Fixture()) {
      f.seedOld(0);
      when(f.ai.generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenReturn(f.response(1));
      CollectionRunView run = f.service().runManually();
      assertThat(run.getGeneratedCount()).isEqualTo(1);
      assertThat(run.getState()).isEqualTo("partial");
      assertThat(run.getRemainingCount()).isEqualTo(9);
      verify(f.ai, times(20))
          .generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM learning_content", Integer.class))
          .isEqualTo(2);
    }
  }

  @Test
  void budgetFailurePreservesProgressAndAutomaticRetriesStopAfterThree() throws Exception {
    try (Fixture f = new Fixture()) {
      f.newsCount = 1;
      when(f.ai.generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenThrow(
              new ApiException(
                  HttpStatus.TOO_MANY_REQUESTS, "AI_MONTHLY_BUDGET_EXCEEDED", "月度预算不足"));
      ContentCollectionService service = f.service();
      CollectionRunView run = service.runManually();
      assertThat(run.getInsertedCount()).isEqualTo(1);
      assertThat(run.getRemainingCount()).isEqualTo(9);
      assertThat(run.getErrorCode()).isEqualTo("AI_MONTHLY_BUDGET_EXCEEDED");
      for (int i = 0; i < 3; i++) {
        f.jdbc.update(
            "UPDATE content_collection_day SET next_retry_at=?",
            Timestamp.from(Instant.now().minusSeconds(1)));
        service.retryDailyShortfall();
      }
      service.retryDailyShortfall();
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT auto_attempts FROM content_collection_day", Integer.class))
          .isEqualTo(3);
      assertThat(
              f.jdbc.queryForObject(
                  "SELECT next_retry_at FROM content_collection_day", Object.class))
          .isNull();
      verify(f.ai, times(4))
          .generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM learning_content", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void changingOnlyTitleAndDateCannotCountAsOriginalNewContent() throws Exception {
    try (Fixture f = new Fixture()) {
      f.seedOld(0);
      when(f.ai.generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(
              call -> {
                com.fasterxml.jackson.databind.node.ObjectNode article =
                    (com.fasterxml.jackson.databind.node.ObjectNode) f.json.readTree(f.response(1));
                int number = f.calls.incrementAndGet();
                article.put("title", "不同标题测试学习材料" + number);
                article.put("body", article.path("body").asText() + "2026-11-" + number);
                return article.toString();
              });
      CollectionRunView run = f.service().runManually();
      assertThat(run.getGeneratedCount()).isEqualTo(1);
      assertThat(f.calls.get()).isEqualTo(20);
      assertThat(run.getErrorMessage()).contains("高度相似");
    }
  }

  @Test
  void failedVersionOrTopicWriteRollsBackTheWholeNewContent() throws Exception {
    try (Fixture f = new Fixture()) {
      f.newsCount = 1;
      ContentCollectionMapper failing =
          mock(ContentCollectionMapper.class, org.mockito.AdditionalAnswers.delegatesTo(f.mapper));
      doThrow(new IllegalStateException("测试主题写入失败"))
          .when(failing)
          .insertContentTopic(anyString(), anyString(), anyString());
      CollectionRunView run = f.service(failing).runManually();
      assertThat(run.getInsertedCount()).isZero();
      assertThat(run.getRemainingCount()).isEqualTo(10);
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM learning_content", Integer.class))
          .isZero();
      assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM content_version", Integer.class))
          .isZero();
      verifyNoInteractions(f.ai);
    }
  }

  @Test
  void expiredLeaseCanRecoverAndPausedTaskNeverRetries() throws Exception {
    try (Fixture f = new Fixture()) {
      LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai"));
      f.quota.ensureDay(Fixture.SCHEDULE, date);
      f.quota.claim(
          Fixture.SCHEDULE,
          date,
          "crashed",
          Instant.now().minusSeconds(600),
          Instant.now().minusSeconds(1),
          1);
      assertThat(f.quota.due(Fixture.SCHEDULE, date, Instant.now())).isEqualTo(1);
      f.jdbc.update("UPDATE content_collection_schedule SET state='paused'");
      f.service().retryDailyShortfall();
      verifyNoInteractions(f.ai);
      f.jdbc.update("UPDATE content_collection_schedule SET state='active'");
      f.newsCount = 10;
      f.service().retryDailyShortfall();
      assertThat(
              f.quota.countInserted(
                  date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                  date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()))
          .isEqualTo(10);
    }
  }

  @Test
  void secondInstanceCannotGenerateWhileFirstOwnsTheDay() throws Exception {
    try (Fixture f = new Fixture()) {
      f.seedOld(0);
      CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
      when(f.ai.generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(
              call -> {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("test timeout");
                return f.response(f.calls.incrementAndGet());
              });
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<CollectionRunView> first = executor.submit(() -> f.service().runManually());
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> f.service().runManually())
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("其他实例");
        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).getDailyInsertedCount()).isEqualTo(10);
        assertThat(f.calls.get()).isEqualTo(10);
      } finally {
        release.countDown();
        executor.shutdownNow();
      }
    }
  }

  static class Fixture implements AutoCloseable {
    static final String SCHEDULE = "00000000000000000000000000000090",
        SOURCE = "00000000000000000000000000000081";
    final EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName(
                "daily-tech-"
                    + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
            .addScript("schema.sql")
            .addScript("data.sql")
            .build();
    final JdbcTemplate jdbc = new JdbcTemplate(db);
    final AiService ai = mock(AiService.class);
    final ObjectMapper json = new ObjectMapper();
    final ContentCollectionMapper mapper =
        MapperTestSupport.mapper(db, ContentCollectionMapper.class);
    final TechnicalCollectionQuotaMapper quota =
        MapperTestSupport.mapper(db, TechnicalCollectionQuotaMapper.class);
    final AtomicInteger calls = new AtomicInteger(), fetches = new AtomicInteger();
    int newsCount;
    boolean feedFailure;

    Fixture() {
      jdbc.execute(
          "CREATE ALIAS IF NOT EXISTS UNHEX FOR 'com.zhixing.service.DailyTechnicalContentTest.unhex'");
    }

    ContentCollectionService service() {
      return service(mapper);
    }

    ContentCollectionService service(ContentCollectionMapper collectionMapper) {
      return new ContentCollectionService(
          collectionMapper,
          new AppProperties(),
          quota,
          ai,
          json,
          new DataSourceTransactionManager(db)) {
        @Override
        protected List<FeedItem> fetch(FeedSource source, int limit) {
          fetches.incrementAndGet();
          if (feedFailure) throw new IllegalStateException("测试来源不可用");
          List<FeedItem> items = new ArrayList<>();
          if (source.id.equals(SOURCE))
            for (int i = 0; i < newsCount; i++)
              items.add(
                  new FeedItem(
                      "Official topic " + i,
                      "Official summary " + i,
                      "https://kubernetes.io/blog/test-" + i,
                      Instant.now()));
          return items;
        }
      };
    }

    void seedOld(int index) {
      String content = CryptoUtils.randomId(), version = CryptoUtils.randomId();
      jdbc.update(
          "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,created_at) VALUES(?,'tech',?,?,'pending',?,?)",
          content,
          SOURCE,
          CryptoUtils.sha256(
              SOURCE
                  + "\nhttps://kubernetes.io/blog/test-"
                  + index
                  + "\nOfficial topic "
                  + index
                  + "\nOfficial summary "
                  + index),
          version,
          Timestamp.from(Instant.now().minusSeconds(172800)));
      jdbc.update(
          "INSERT INTO content_version(id,content_id,version_no,title,summary,body,origin_url,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,? ,?,'官方摘要',?,'pending',?)",
          version,
          content,
          "Official topic " + index,
          "Official summary " + index,
          "历史待审核资料",
          "https://kubernetes.io/blog/test-" + index,
          CryptoUtils.sha256(version),
          SCHEDULE);
    }

    String response(int index) throws Exception {
      String pool = "容器资源进程网络内存隔离配置验证边界架构流程安全风险观察应用故障排查模型输入输出工具测试维护系统性能授权分析";
      Random random = new Random(index);
      StringBuilder sample = new StringBuilder();
      for (int i = 0; i < 420; i++) sample.append(pool.charAt(random.nextInt(pool.length())));
      String paragraph = "围绕不同技术案例说明适用边界，需在隔离环境核验。" + sample;
      Map<String, String> response = new LinkedHashMap<>();
      response.put("title", "技术验证思路与场景分析" + index);
      response.put("summary", "这是一篇用于解释技术问题、观察方法与适用边界的原创学习材料。");
      response.put("body", paragraph);
      return json.writeValueAsString(response);
    }

    void validAi() throws Exception {
      when(ai.generateSystemContent(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(call -> response(calls.incrementAndGet()));
    }

    @Override
    public void close() {
      db.shutdown();
    }
  }

  public static byte[] unhex(String value) {
    byte[] result = new byte[value.length() / 2];
    for (int i = 0; i < result.length; i++)
      result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    return result;
  }
}
