package com.zhixing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.mapper.DailyTaskMapper;
import com.zhixing.model.DailyPackageView;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DailyWordFlowRecoveryIntegrationTest extends DailyWordSelectionIntegrationTest {
  @SpyBean DailyTaskMapper taskMapper;
  @SpyBean ContentMapper contentMapper;
  @SpyBean com.zhixing.service.DailyTaskService dailySpy;

  Map<String, Object> feedback(String id, Map<String, Object> queue, String mode, String answer) {
    Map<String, Object> input = new HashMap<>();
    input.put("attemptId", CryptoUtils.randomId());
    input.put("feedback", answer);
    input.put("businessDate", today.toString());
    input.put("expectedVersion", ((Map<?, ?>) queue.get("current")).get("version"));
    input.put("mode", mode);
    return study.feedback(owner, id, input);
  }

  String yesterdayFuzzy() {
    String first = ids(daily.selectWords(owner, today)).get(0);
    feedback(first, study.queue(owner, "daily", first), "daily", "fuzzy");
    jdbc.update(
        "UPDATE daily_package SET business_date=? WHERE owner_id=?", today.minusDays(1), owner);
    jdbc.update(
        "UPDATE word_study_state SET business_date=?,first_seen_date=? WHERE owner_id=?",
        today.minusDays(1),
        today.minusDays(1),
        owner);
    return first;
  }

  @Test
  void recoveryInitializationFailureRollsBackAndRetryBuildsACompletePackage() {
    AtomicBoolean inTransaction = new AtomicBoolean();
    doAnswer(
            call -> {
              inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
              throw new IllegalStateException("test failed query");
            })
        .when(materials)
        .selectWordContent(eq(owner), eq(book), anyString(), anyInt(), any());
    assertThatThrownBy(() -> daily.get(owner, today)).hasMessageContaining("test failed query");
    assertThat(inTransaction.get()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_package WHERE owner_id=?", Integer.class, owner))
        .isZero();
    reset(materials);
    assertThat(active(daily.get(owner, today))).hasSize(3);
  }

  @Test
  void recoveryRepairsOldPartialPackageWithoutDuplicatingTasks() {
    jdbc.update(
        "INSERT INTO daily_package(id,owner_id,business_date,plan_id,budget_seconds,state) VALUES(?,?,?,?,600,'active')",
        CryptoUtils.randomId(),
        owner,
        today,
        plan);
    DailyPackageView repaired = daily.get(owner, today);
    assertThat(active(repaired)).hasSize(3);
    assertThat(repaired.getOriginalCount()).isEqualTo(3);
    List<String> ids = ids(repaired);
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM package_revision WHERE owner_id=? AND reason='activation_repair'",
                Integer.class,
                owner))
        .isEqualTo(1);
  }

  @Test
  void recoveryUnavailableWordsRemainUnfinishedAndResumeWhenRepublished() {
    DailyPackageView fixed = daily.selectWords(owner, today);
    jdbc.update("UPDATE learning_content SET state='withdrawn' WHERE source_id=?", source);
    Map<String, Object> blocked = study.queue(owner, "daily", ids(fixed).get(0));
    assertThat(blocked.get("remaining")).isEqualTo(0);
    assertThat(blocked.get("unavailableCount")).isEqualTo(3);
    assertThat(blocked.get("unfinishedCount")).isEqualTo(3);
    assertThat(blocked.get("allCompleted")).isEqualTo(false);
    DailyPackageView pack = daily.get(owner, today);
    assertThat(pack.getWordsUnavailableCount()).isEqualTo(3);
    assertThat(active(pack))
        .allMatch(t -> Boolean.FALSE.equals(t.getAvailable()) && "TODO".equals(t.getStatus()));
    jdbc.update("UPDATE learning_content SET state='published' WHERE source_id=?", source);
    Map<String, Object> resumed = study.queue(owner, "daily", null);
    assertThat(resumed.get("current")).isNotNull();
    assertThat(resumed.get("unavailableCount")).isEqualTo(0);
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids(fixed));
  }

  @Test
  void recoveryConsolidationSurvivesNextDayAndCompletesWithoutChangingTodayRange() {
    String old = yesterdayFuzzy();
    java.util.List<com.zhixing.model.MaterialCandidate> other =
        materials.selectWordContent(owner, book, "intro", 100, today);
    other.removeIf(candidate -> old.equals(candidate.getId()));
    doReturn(other.subList(0, 3))
        .when(materials)
        .selectRandomWordContent(owner, book, "intro", 3, today);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(ids(fixed)).doesNotContain(old);
    assertThat(fixed.getPendingConsolidationCount()).isEqualTo(1);
    Map<String, Object> queue = study.queue(owner, "consolidation", null);
    assertThat(((Map<?, ?>) queue.get("current")).get("contentId")).isEqualTo(old);
    Map<String, Object> result = feedback(old, queue, "consolidation", "remember");
    assertThat(result.get("allCompleted")).isEqualTo(true);
    assertThat(daily.get(owner, today).getPendingConsolidationCount()).isZero();
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids(fixed));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id WHERE rs.owner_id=? AND k.source_content_id=?",
                Integer.class,
                owner,
                old))
        .isEqualTo(1);
  }

  @Test
  void recoveryConsolidationIsVisibleWithoutTodayPlanAndUnavailableIsNotCompletion() {
    String old = yesterdayFuzzy();
    jdbc.update("DELETE FROM learning_plan WHERE owner_id=?", owner);
    assertThat(daily.get(owner, today).getPendingConsolidationCount()).isEqualTo(1);
    jdbc.update("UPDATE learning_content SET state='withdrawn' WHERE id=?", old);
    Map<String, Object> queue = study.queue(owner, "consolidation", null);
    assertThat(queue.get("remaining")).isEqualTo(0);
    assertThat(queue.get("unavailableCount")).isEqualTo(1);
    assertThat(queue.get("allCompleted")).isEqualTo(false);
  }

  @Test
  void recoveryWordListHasBatchMeaningsAndStableTargetAndNote() {
    daily.get(owner, today);
    jdbc.update("UPDATE daily_package SET budget_seconds=30 WHERE owner_id=?", owner);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(fixed.getWordsTargetCount()).isEqualTo(3);
    assertThat(fixed.getWordsSelectionNote()).contains("预算");
    assertThat(fixed.getWordsBookName()).isEqualTo("测试词书");
    clearInvocations(taskMapper, contentMapper);
    assertThat(active(daily.get(owner, today))).allMatch(t -> "测试释义".equals(t.getMeaning()));
    verify(taskMapper, times(1)).selectWordInfo(anyString(), eq(owner));
    verify(contentMapper, never()).selectDetail(anyString(), anyString());
    jdbc.update("UPDATE learning_plan SET new_word_count=8 WHERE id=?", plan);
    daily.refreshToday(owner);
    assertThat(daily.get(owner, today).getWordsTargetCount()).isEqualTo(3);
    assertThat(daily.get(owner, today).getWordsSelectionNote()).contains("预算");
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids(fixed));
  }

  @Test
  void recoveryUnfinishedWordsBecomeFutureCandidatesAndRebindOnlyTheNewTask() {
    String old = yesterdayFuzzy();
    String originalTask =
        jdbc.queryForObject(
            "SELECT task_id FROM word_study_state WHERE owner_id=? AND content_id=?",
            String.class,
            owner,
            old);
    List<com.zhixing.model.MaterialCandidate> candidates =
        materials.selectWordContent(owner, book, "intro", 100, today);
    assertThat(candidates).extracting(com.zhixing.model.MaterialCandidate::getId).contains(old);
    List<com.zhixing.model.MaterialCandidate> chosen = new ArrayList<>();
    chosen.add(candidates.stream().filter(c -> old.equals(c.getId())).findFirst().get());
    candidates.stream().filter(c -> !old.equals(c.getId())).limit(2).forEach(chosen::add);
    doReturn(chosen).when(materials).selectRandomWordContent(owner, book, "intro", 3, today);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(ids(fixed)).contains(old);
    assertThat(fixed.getPendingConsolidationCount()).isZero();
    String newTask =
        active(fixed).stream().filter(t -> old.equals(t.getContentId())).findFirst().get().getId();
    assertThat(newTask).isNotEqualTo(originalTask);
    Map<String, Object> queue = study.queue(owner, "daily", old);
    assertThat(((Map<?, ?>) queue.get("current")).get("contentId")).isEqualTo(old);
    feedback(old, queue, "daily", "remember");
    assertThat(
            jdbc.queryForObject("SELECT status FROM daily_task WHERE id=?", String.class, newTask))
        .isEqualTo("DONE");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM daily_task WHERE id=?", String.class, originalTask))
        .isEqualTo("DOING");
    assertThat(materials.selectWordContent(owner, book, "intro", 100, today.plusDays(1)))
        .extracting(com.zhixing.model.MaterialCandidate::getId)
        .doesNotContain(old);
    assertThat(ids(daily.selectWords(owner, today))).containsExactlyElementsOf(ids(fixed));
  }

  @Test
  void recoveryIncompleteWordIsEligibleStartingTomorrowAndOnlyInsideItsBook() {
    String first = ids(daily.selectWords(owner, today)).get(0);
    feedback(first, study.queue(owner, "daily", first), "daily", "fuzzy");
    assertThat(materials.selectWordContent(owner, book, "intro", 100, today))
        .extracting(com.zhixing.model.MaterialCandidate::getId)
        .doesNotContain(first);
    assertThat(materials.selectWordContent(owner, book, "intro", 100, today.plusDays(1)))
        .extracting(com.zhixing.model.MaterialCandidate::getId)
        .contains(first);
    assertThat(
            materials.selectWordContent(
                owner, CryptoUtils.randomId(), "intro", 100, today.plusDays(1)))
        .isEmpty();
  }

  @Test
  void recoveryDailyQueueAvoidsReadingTheSameFullPackageTwice() {
    daily.selectWords(owner, today);
    clearInvocations(dailySpy);
    study.queue(owner, "daily", null);
    verify(dailySpy, times(1)).selectWords(owner, today);
    verify(dailySpy, never()).get(anyString(), any());
  }

  @Test
  void recoveryDailyFeedbackUsesScopedMembershipQueryWithoutReloadingWordList() {
    String first = ids(daily.selectWords(owner, today)).get(0);
    Map<String, Object> queue = study.queue(owner, "daily", first);
    clearInvocations(dailySpy, taskMapper);
    feedback(first, queue, "daily", "remember");
    verify(dailySpy, never()).get(anyString(), any());
    verify(taskMapper, never()).selectWordInfo(anyString(), anyString());
    verify(taskMapper, never()).selectWordsBookName(anyString(), anyString());
  }

  @Test
  void recoveryDailyMembershipStillRejectsAnUnallocatedFreeStudyWord() {
    List<String> selected = ids(daily.selectWords(owner, today));
    String outside = words.stream().filter(id -> !selected.contains(id)).findFirst().get();
    Map<String, Object> queue = study.queue(owner, "all", outside);
    assertThatThrownBy(() -> feedback(outside, queue, "daily", "remember"))
        .isInstanceOf(com.zhixing.common.ApiException.class)
        .hasMessageContaining("今日待学范围");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_record WHERE owner_id=? AND content_id=?",
                Integer.class,
                owner,
                outside))
        .isZero();
  }
}
