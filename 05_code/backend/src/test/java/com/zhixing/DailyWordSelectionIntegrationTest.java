package com.zhixing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.*;
import com.zhixing.model.*;
import com.zhixing.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:dailyselection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
      "spring.sql.init.mode=always",
      "app.wechat.mock-enabled=true",
      "app.registration-store=memory"
    })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DailyWordSelectionIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired DailyTaskService daily;
  @Autowired WordStudyService study;
  @Autowired LearningPlanMapper plans;
  @SpyBean MaterialMapper materials;
  @SpyBean DailyPackageMapper packages;
  @Autowired MockMvc mvc;
  String owner, book, source, plan;
  List<String> words;
  LocalDate today;

  @BeforeEach
  void seed() {
    owner = CryptoUtils.randomId();
    book = CryptoUtils.randomId();
    source = CryptoUtils.randomId();
    plan = CryptoUtils.randomId();
    today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    jdbc.update(
        "INSERT INTO app_user(id,wx_app_id,wx_open_id,nickname) VALUES(?,'daily-test',?,'选词测试')",
        owner,
        owner);
    jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,'test','test')", source);
    jdbc.update(
        "INSERT INTO learning_plan(id,owner_id,version_no,effective_date,daily_budget_min,weekdays_mask,difficulty,tech_count,new_word_count,journal_enabled,review_enabled) VALUES(?,?,1,?,10,127,'intro',0,3,0,0)",
        plan,
        owner,
        today.minusDays(2));
    jdbc.update(
        "INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,state) VALUES(?,?,?,'general','intro',12,'active')",
        book,
        book,
        "测试词书");
    jdbc.update(
        "INSERT INTO user_vocabulary_book(id,owner_id,book_id,state,daily_new_limit) VALUES(?,?,?,'active',3)",
        CryptoUtils.randomId(),
        owner,
        book);
    words = new ArrayList<>();
    for (int n = 0; n < 12; n++) {
      String id = CryptoUtils.randomId(), v = CryptoUtils.randomId();
      words.add(id);
      jdbc.update(
          "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'word',?,?,'published',?,?,CURRENT_TIMESTAMP)",
          id,
          source,
          CryptoUtils.sha256(id),
          v,
          v);
      jdbc.update(
          "INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'intro',30,?,'测试释义','test',?,'approved',?)",
          v,
          id,
          "word" + n,
          "test",
          "test",
          "word" + n,
          CryptoUtils.sha256(v),
          source);
      jdbc.update(
          "INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,5,1)",
          CryptoUtils.randomId(),
          book,
          id,
          n);
    }
  }

  @Test
  void firstClickUsesRandomCandidatesAndSubsequentReadsAndPlanChangesKeepTaskIds() {
    DailyPackageView preview = daily.get(owner, today);
    assertThat(preview.getWordsSelectedAt()).isNull();
    assertThat(ids(preview)).containsExactlyElementsOf(words.subList(0, 3));
    List<MaterialCandidate> random = materials.selectWordContent(owner, book, "intro", 12, today);
    List<MaterialCandidate> chosen = Arrays.asList(random.get(9), random.get(7), random.get(5));
    doReturn(chosen).when(materials).selectRandomWordContent(owner, book, "intro", 3);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(ids(fixed)).containsExactlyInAnyOrder(words.get(9), words.get(7), words.get(5));
    List<String> taskIds =
        active(fixed).stream().map(DailyTaskView::getId).collect(Collectors.toList());
    assertThat(fixed.getWordsSelectedAt()).isNotNull();
    assertThat(fixed.getWordsBookId()).isEqualTo(book);
    jdbc.update("UPDATE user_vocabulary_book SET state='paused' WHERE owner_id=?", owner);
    jdbc.update("UPDATE learning_plan SET new_word_count=8 WHERE id=?", plan);
    daily.refreshToday(owner);
    assertThat(active(daily.selectWords(owner, today)))
        .extracting(DailyTaskView::getId)
        .containsExactlyElementsOf(taskIds);
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids(fixed));
    verify(materials, times(1)).selectRandomWordContent(owner, book, "intro", 3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM package_revision WHERE owner_id=? AND reason='word_selection'",
                Integer.class,
                owner))
        .isEqualTo(1);
  }

  @Test
  void actualRandomSqlExcludesLearnedExposedUnpublishedAndOtherBookWords() {
    jdbc.update("UPDATE learning_content SET state='draft' WHERE id=?", words.get(0));
    jdbc.update(
        "UPDATE content_version SET review_status='pending' WHERE content_id=?", words.get(1));
    jdbc.update("DELETE FROM vocabulary_book_word WHERE content_id=?", words.get(2));
    jdbc.update(
        "INSERT INTO word_study_state(owner_id,content_id,source_kind,business_date,first_seen_date,exposed) VALUES(?,?,'new',?,?,1)",
        owner,
        words.get(3),
        today,
        today);
    jdbc.update(
        "INSERT INTO learning_record(id,owner_id,content_id,learning_key,last_version_id,learning_status) SELECT ?,?,?,?,published_version_id,'understood' FROM learning_content WHERE id=?",
        CryptoUtils.randomId(),
        owner,
        words.get(4),
        "word:" + words.get(4),
        words.get(4));
    jdbc.update("UPDATE learning_plan SET new_word_count=20 WHERE id=?", plan);
    DailyPackageView result = daily.selectWords(owner, today);
    assertThat(ids(result)).containsExactlyInAnyOrderElementsOf(words.subList(5, 12));
    assertThat(new HashSet<>(ids(result))).hasSize(ids(result).size());
    assertThat(daily.selectWords(owner, today).getWordsSelectedAt())
        .isEqualTo(result.getWordsSelectedAt());
  }

  @Test
  void legacyStartedWordsKeepTheWholeExistingRangeAndProgress() {
    DailyPackageView preview = daily.get(owner, today);
    String id = active(preview).get(0).getId();
    jdbc.update("UPDATE daily_task SET status='DOING',started_at=CURRENT_TIMESTAMP WHERE id=?", id);
    DailyPackageView result = daily.selectWords(owner, today);
    assertThat(ids(result)).containsExactlyElementsOf(ids(preview));
    assertThat(active(result).get(0).getStatus()).isEqualTo("DOING");
    verify(materials, never())
        .selectRandomWordContent(eq(owner), anyString(), anyString(), anyInt());
  }

  @Test
  void missingBookDoesNotFreezeAndOnlyCurrentBusinessDateCanBeSelected() throws Exception {
    jdbc.update("DELETE FROM user_vocabulary_book WHERE owner_id=?", owner);
    assertThatThrownBy(() -> daily.selectWords(owner, today))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("词书");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_package WHERE owner_id=? AND words_selected_at IS NOT NULL",
                Integer.class,
                owner))
        .isZero();
    assertThatThrownBy(() -> daily.selectWords(owner, today.minusDays(1)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("今天");
    mvc.perform(post("/api/days/{date}/words/select", today)).andExpect(status().isUnauthorized());
  }

  @Test
  void priorDaySnapshotDoesNotFreezeToday() {
    DailyPackageView yesterday = daily.get(owner, today.minusDays(1));
    jdbc.update(
        "UPDATE daily_package SET words_selected_at=CURRENT_TIMESTAMP,words_book_id=? WHERE owner_id=? AND business_date=?",
        book,
        owner,
        today.minusDays(1));
    DailyPackageView result = daily.selectWords(owner, today);
    assertThat(active(result))
        .extracting(DailyTaskView::getId)
        .doesNotContainAnyElementsOf(
            active(yesterday).stream().map(DailyTaskView::getId).collect(Collectors.toList()));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_package WHERE owner_id=? AND words_selected_at IS NOT NULL",
                Integer.class,
                owner))
        .isEqualTo(2);
  }

  @Test
  void concurrentFirstClicksReturnOnePersistedRange() throws Exception {
    daily.get(owner, today);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<DailyPackageView>> results = new ArrayList<>();
      for (int i = 0; i < 4; i++)
        results.add(
            pool.submit(
                () -> {
                  go.await();
                  return daily.selectWords(owner, today);
                }));
      go.countDown();
      List<String> expected = ids(results.get(0).get(10, TimeUnit.SECONDS));
      for (Future<DailyPackageView> result : results)
        assertThat(ids(result.get(10, TimeUnit.SECONDS))).containsExactlyElementsOf(expected);
      verify(materials, times(1)).selectRandomWordContent(owner, book, "intro", 3);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedSnapshotWriteRollsBackAndAllowsSafeRetry() {
    DailyPackageView before = daily.get(owner, today);
    doThrow(new IllegalStateException("test write failed"))
        .when(packages)
        .insertRevision(
            argThat(
                r ->
                    r != null
                        && owner.equals(r.getOwnerId())
                        && "word_selection".equals(r.getReason())));
    assertThatThrownBy(() -> daily.selectWords(owner, today))
        .hasMessageContaining("test write failed");
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids(before));
    assertThat(daily.get(owner, today).getWordsSelectedAt()).isNull();
    reset(packages);
    assertThat(daily.selectWords(owner, today).getWordsSelectedAt()).isNotNull();
  }

  @Test
  void emptyEligiblePoolIsPersistedAndDoesNotKeepResampling() {
    jdbc.update(
        "UPDATE content_version SET review_status='pending' WHERE content_id IN (SELECT content_id FROM vocabulary_book_word WHERE book_id=?)",
        book);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(ids(fixed)).isEmpty();
    assertThat(fixed.getWordsSelectedAt()).isNotNull();
    jdbc.update(
        "UPDATE content_version SET review_status='approved' WHERE content_id IN (SELECT content_id FROM vocabulary_book_word WHERE book_id=?)",
        book);
    assertThat(ids(daily.selectWords(owner, today))).isEmpty();
    verify(materials, times(1)).selectRandomWordContent(owner, book, "intro", 3);
  }

  @Test
  void remainingTimeBudgetLimitsFirstSelectionWithoutReshufflingLater() {
    DailyPackageView preview = daily.get(owner, today);
    jdbc.update(
        "UPDATE daily_package SET budget_seconds=30 WHERE owner_id=? AND business_date=?",
        owner,
        today);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(ids(fixed)).hasSize(1);
    assertThat(active(fixed).stream().mapToInt(DailyTaskView::getEstimatedSeconds).sum())
        .isEqualTo(30);
    assertThat(fixed.getCurrentCount()).isLessThan(preview.getCurrentCount());
    assertThat(ids(daily.selectWords(owner, today))).containsExactlyElementsOf(ids(fixed));
  }

  @Test
  void dailyFeedbackNeverAdvancesOutsideFixedRangeEvenAfterBookSwitch() {
    DailyPackageView fixed = daily.selectWords(owner, today);
    jdbc.update("UPDATE user_vocabulary_book SET state='paused' WHERE owner_id=?", owner);
    Map<String, Object> queue = study.queue(owner, "daily", ids(fixed).get(0));
    Set<String> seen = new HashSet<>();
    for (int n = 0; n < 3; n++) {
      Map<?, ?> current = (Map<?, ?>) queue.get("current");
      String id = String.valueOf(current.get("contentId"));
      seen.add(id);
      Map<String, Object> request = new HashMap<>();
      request.put("attemptId", CryptoUtils.randomId());
      request.put("feedback", "remember");
      request.put("businessDate", today.toString());
      request.put("expectedVersion", current.get("version"));
      request.put("mode", "daily");
      queue = study.feedback(owner, id, request);
    }
    assertThat(seen).containsExactlyInAnyOrderElementsOf(ids(fixed));
    assertThat(queue.get("remaining")).isEqualTo(0);
    assertThat(queue.get("current")).isNull();
    assertThat(active(daily.get(owner, today))).allMatch(t -> "DONE".equals(t.getStatus()));
  }

  @Test
  void legacyMixedBookRangeStillQueuesEverySavedWord() {
    DailyPackageView preview = daily.get(owner, today);
    jdbc.update("UPDATE daily_task SET status='DOING' WHERE id=?", active(preview).get(0).getId());
    // 旧版换书后可能留下前一本词书的 TODO；首日升级保留整批事实。
    jdbc.update("DELETE FROM vocabulary_book_word WHERE content_id=?", ids(preview).get(1));
    DailyPackageView fixed = daily.selectWords(owner, today);
    Map<String, Object> queue = study.queue(owner, "daily", ids(fixed).get(1));
    assertThat(queue.get("remaining")).isEqualTo(3);
    assertThat(((Map<?, ?>) queue.get("current")).get("contentId")).isEqualTo(ids(fixed).get(1));
  }

  List<DailyTaskView> active(DailyPackageView pack) {
    return pack.getTasks().stream()
        .filter(t -> "word".equals(t.getTaskType()) && !"CANCELLED".equals(t.getStatus()))
        .collect(Collectors.toList());
  }

  List<String> ids(DailyPackageView pack) {
    return active(pack).stream().map(DailyTaskView::getContentId).collect(Collectors.toList());
  }
}
