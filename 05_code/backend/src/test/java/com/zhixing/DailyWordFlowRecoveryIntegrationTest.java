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
  @org.springframework.beans.factory.annotation.Autowired
  com.zhixing.service.ReviewService reviewService;

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
    doReturn(Collections.emptyList())
        .when(materials)
        .selectUnfinishedWordContent(owner, book, "intro", 3, today);
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

  @Test
  void recoveryDailyUnknownWordsRotateWithoutWaitingUntilEachIsRecognized() {
    DailyPackageView fixed = daily.selectWords(owner, today);
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String first = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    Set<String> cycle = new LinkedHashSet<>();
    for (int i = 0; i < 3; i++) {
      String current = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
      cycle.add(current);
      queue = feedback(current, queue, "daily", i == 1 ? "unclear" : "fuzzy");
      assertThat(queue.get("waitSeconds")).isEqualTo(0);
      assertThat(queue.get("practiceMode")).isEqualTo("continuous");
      assertThat(queue.get("dailyRecognized")).isEqualTo(0);
      assertThat(queue.get("allCompleted")).isEqualTo(false);
    }
    assertThat(cycle).containsExactlyInAnyOrderElementsOf(ids(fixed));
    assertThat(((Map<?, ?>) queue.get("current")).get("contentId")).isEqualTo(first);
    Set<String> learned = new HashSet<>();
    for (int i = 0; i < 3; i++) {
      String current = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
      assertThat(learned).doesNotContain(current);
      learned.add(current);
      queue = feedback(current, queue, "daily", "remember");
      assertThat(queue.get("dailyRecognized")).isEqualTo(i + 1);
      assertThat(queue.get("unfinishedCount")).isEqualTo(2 - i);
    }
    assertThat(queue.get("allCompleted")).isEqualTo(true);
    assertThat(queue.get("current")).isNull();
    assertThat(daily.get(owner, today).getDoneCount()).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM review_schedule WHERE owner_id=? AND state='active'",
                Integer.class,
                owner))
        .isEqualTo(3);
  }

  @Test
  void recoveryOnlyOneUnknownDailyWordCanBePracticedImmediatelyUntilRemembered() {
    jdbc.update("UPDATE learning_plan SET new_word_count=1 WHERE id=?", plan);
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String only = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    for (String answer : Arrays.asList("unclear", "fuzzy", "unclear")) {
      queue = feedback(only, queue, "daily", answer);
      assertThat(((Map<?, ?>) queue.get("current")).get("contentId")).isEqualTo(only);
      assertThat(queue.get("waitSeconds")).isEqualTo(0);
      assertThat(queue.get("dailyRecognized")).isEqualTo(0);
    }
    queue = feedback(only, queue, "daily", "remember");
    assertThat(queue.get("dailyRecognized")).isEqualTo(1);
    assertThat(queue.get("dailyTotal")).isEqualTo(1);
    assertThat(queue.get("allCompleted")).isEqualTo(true);
  }

  @Test
  void recoveryOldDailyCooldownDoesNotDisableFeedbackAfterUpgrade() {
    daily.selectWords(owner, today);
    study.queue(owner, "daily", null);
    jdbc.update(
        "UPDATE word_study_state SET eligible_position=4,eligible_at=? WHERE owner_id=?",
        java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(120)),
        owner);
    Map<String, Object> queue = study.queue(owner, "daily", null);
    assertThat(queue.get("current")).isNotNull();
    String id = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    Map<String, Object> next = feedback(id, queue, "daily", "fuzzy");
    assertThat(next.get("current")).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT eligible_at FROM word_study_state WHERE owner_id=? AND content_id=?",
                Object.class,
                owner,
                id))
        .isNull();
  }

  @Test
  void recoverySkippedDailyWordIsRestoredRatherThanCountedAsRecognized() {
    DailyPackageView fixed = daily.selectWords(owner, today);
    com.zhixing.model.DailyTaskView task = active(fixed).get(0);
    com.zhixing.dto.TaskEventRequest skip = new com.zhixing.dto.TaskEventRequest();
    skip.setEventType("skip");
    skip.setExpectedVersion(task.getVersionNo());
    daily.event(owner, task.getId(), skip);
    Map<String, Object> queue = study.queue(owner, "daily", task.getContentId());
    assertThat(queue.get("dailyRecognized")).isEqualTo(0);
    assertThat(queue.get("unfinishedCount")).isEqualTo(3);
    assertThat(((Map<?, ?>) queue.get("current")).get("contentId")).isEqualTo(task.getContentId());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM daily_task WHERE id=?", String.class, task.getId()))
        .isEqualTo("TODO");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_event WHERE owner_id=? AND event_type='resume'",
                Integer.class,
                owner))
        .isEqualTo(1);
    feedback(task.getContentId(), queue, "daily", "remember");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM daily_task WHERE id=?", String.class, task.getId()))
        .isEqualTo("DONE");
  }

  @Test
  void recoveryScheduledReviewCanRepeatImmediatelyAndKeepsFutureSpacing() {
    jdbc.update("UPDATE learning_plan SET new_word_count=1 WHERE id=?", plan);
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String id = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    feedback(id, queue, "daily", "remember");
    jdbc.update("UPDATE review_schedule SET due_date=? WHERE owner_id=?", today, owner);
    jdbc.update(
        "UPDATE word_study_state SET last_completed_date=? WHERE owner_id=?",
        today.minusDays(1),
        owner);
    queue = study.queue(owner, "review", null);
    Map<String, Object> next = feedback(id, queue, "review", "fuzzy");
    assertThat(next.get("practiceMode")).isEqualTo("continuous");
    assertThat(next.get("remaining")).isEqualTo(1);
    assertThat(next.get("current")).isNotNull();
    assertThat(((Map<?, ?>) next.get("current")).get("recallRequired")).isEqualTo(true);
    assertThat(((Number) next.get("waitSeconds")).longValue()).isZero();
    feedback(id, next, "review", "remember");
    assertThat(
            jdbc.queryForObject(
                    "SELECT due_date FROM review_schedule WHERE owner_id=?",
                    java.sql.Date.class,
                    owner)
                .toLocalDate())
        .isEqualTo(today.plusDays(1));
  }

  @Test
  void recoveryResumeQuotaPrioritizesOldWordsAndPreservesFreshAndFrozenRange() {
    Set<String> old = new HashSet<>(ids(daily.get(owner, today)));
    jdbc.update(
        "UPDATE daily_package SET business_date=? WHERE owner_id=?", today.minusDays(1), owner);
    assertThat(materials.selectUnfinishedWordContent(owner, book, "intro", 12, today)).hasSize(3);
    assertThat(materials.selectRandomWordContent(owner, book, "intro", 12, today))
        .noneMatch(w -> old.contains(w.getId()));
    jdbc.update("UPDATE learning_plan SET new_word_count=5 WHERE id=?", plan);
    DailyPackageView fixed = daily.selectWords(owner, today);
    assertThat(ids(fixed)).hasSize(5);
    assertThat(ids(fixed).stream().filter(old::contains).count()).isEqualTo(3);
    assertThat(ids(daily.selectWords(owner, today))).containsExactlyElementsOf(ids(fixed));
  }

  @Test
  void recoveryResumeQuotaFillsFromOldWordsWhenNoFreshWordsRemain() {
    jdbc.update("UPDATE learning_plan SET new_word_count=12 WHERE id=?", plan);
    Set<String> old = new HashSet<>(ids(daily.get(owner, today)));
    jdbc.update(
        "UPDATE daily_package SET business_date=? WHERE owner_id=?", today.minusDays(1), owner);
    jdbc.update("UPDATE learning_plan SET new_word_count=5 WHERE id=?", plan);
    assertThat(ids(daily.selectWords(owner, today))).hasSize(5).allMatch(old::contains);
  }

  @Test
  void recoveryRecallRequiredAfterExposureAndFeedbackPreservesExplicitFamiliarity() {
    Map<String, Object> queue = study.queue(owner, "daily", null);
    Map<?, ?> current = (Map<?, ?>) queue.get("current");
    String id = String.valueOf(current.get("contentId"));
    assertThat(current.get("recallRequired")).isEqualTo(false);
    assertThat(((Map<?, ?>) current.get("memoryEvidence")).get("independentCorrect")).isEqualTo(0);
    feedback(id, queue, "daily", "fuzzy");
    assertThat(
            jdbc.queryForObject(
                "SELECT familiarity_percent FROM learning_record WHERE owner_id=? AND content_id=?",
                Integer.class,
                owner,
                id))
        .isNull();
    queue = study.queue(owner, "daily", id);
    assertThat(((Map<?, ?>) queue.get("current")).get("recallRequired")).isEqualTo(true);
    jdbc.update(
        "UPDATE learning_record SET familiarity_percent=37 WHERE owner_id=? AND content_id=?",
        owner,
        id);
    feedback(id, queue, "daily", "remember");
    assertThat(
            jdbc.queryForObject(
                "SELECT familiarity_percent FROM learning_record WHERE owner_id=? AND content_id=?",
                Integer.class,
                owner,
                id))
        .isEqualTo(37);
  }

  @Test
  void recoveryMemoryEvidenceSeparatesFirstAnswersAndOtherUsers() {
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String id = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    String[] results = {
      "independent_correct", "hinted_correct", "incorrect", "independent_correct"
    };
    for (int i = 0; i < results.length; i++)
      jdbc.update(
          "INSERT INTO word_memory_evidence(id,owner_id,content_id,dimension,business_date,episode_id,first_attempt_id,first_result,hint_used,rule_version) VALUES(?,?,?,'meaning',?,?,?,?,?,'test')",
          CryptoUtils.randomId(),
          i == 3 ? CryptoUtils.randomId() : owner,
          id,
          today,
          CryptoUtils.randomId(),
          CryptoUtils.randomId(),
          results[i],
          i == 1 ? 1 : 0);
    Map<?, ?> evidence =
        (Map<?, ?>)
            ((Map<?, ?>) study.queue(owner, "daily", id).get("current")).get("memoryEvidence");
    assertThat(evidence.get("recallDays")).isEqualTo(0);
    assertThat(evidence.get("independentCorrect")).isEqualTo(1);
    assertThat(evidence.get("hintedCorrect")).isEqualTo(1);
    assertThat(evidence.get("firstIncorrect")).isEqualTo(1);
  }

  @Test
  void recoveryReplacementOnlyTouchesUnavailableUnfinishedWordsAndRejectsStaleRetry() {
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String known = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    feedback(known, queue, "daily", "remember");
    List<String> selected = ids(daily.get(owner, today));
    String bad = selected.stream().filter(id -> !id.equals(known)).findFirst().get();
    feedback(bad, study.queue(owner, "daily", bad), "daily", "unclear");
    jdbc.update("UPDATE learning_content SET state='withdrawn' WHERE id IN (?,?)", known, bad);
    DailyPackageView before = daily.get(owner, today);
    DailyPackageView replaced = daily.replaceUnavailableWords(owner, today, before.getVersionNo());
    assertThat(ids(replaced)).hasSize(3).contains(known).doesNotContain(bad);
    assertThat(replaced.getDoneCount()).isEqualTo(1);
    assertThat(replaced.getWordsUnavailableCount()).isZero();
    assertThat(replaced.getVersionNo()).isEqualTo(before.getVersionNo() + 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT cancel_reason FROM daily_task WHERE owner_id=? AND content_id=?",
                String.class,
                owner,
                bad))
        .isEqualTo("content_unavailable");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM package_revision WHERE owner_id=? AND reason='word_unavailable_replace'",
                Integer.class,
                owner))
        .isEqualTo(1);
    assertThatThrownBy(() -> daily.replaceUnavailableWords(owner, today, before.getVersionNo()))
        .isInstanceOf(com.zhixing.common.ApiException.class);
    assertThat(ids(daily.get(owner, today))).containsExactlyElementsOf(ids(replaced));
    jdbc.update("UPDATE learning_content SET state='published' WHERE id=?", bad);
    assertThat(materials.selectWordContent(owner, book, "intro", 100, today.plusDays(1)))
        .extracting(com.zhixing.model.MaterialCandidate::getId)
        .contains(bad);
    assertThat(ids(daily.selectWords(owner, today))).containsExactlyElementsOf(ids(replaced));
  }

  @Test
  void recoveryReplacementWithoutEligibleCandidatesPreservesRangeAndCompletion() {
    DailyPackageView before = daily.selectWords(owner, today);
    jdbc.update("UPDATE learning_content SET state='withdrawn' WHERE source_id=?", source);
    DailyPackageView after = daily.replaceUnavailableWords(owner, today, before.getVersionNo());
    assertThat(ids(after)).containsExactlyElementsOf(ids(before));
    assertThat(after.getVersionNo()).isEqualTo(before.getVersionNo());
    assertThat(after.getWordsUnavailableCount()).isEqualTo(3);
    assertThat(after.getDoneCount()).isZero();
    assertThatThrownBy(
            () -> daily.replaceUnavailableWords(owner, today.minusDays(1), before.getVersionNo()))
        .isInstanceOf(com.zhixing.common.ApiException.class);
    assertThatThrownBy(
            () ->
                daily.replaceUnavailableWords(CryptoUtils.randomId(), today, before.getVersionNo()))
        .isInstanceOf(com.zhixing.common.ApiException.class);
  }

  @Test
  void recoveryReviewSnapshotPreservesUnavailableWordsAndRestoresCompletedFacts() {
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String id = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    feedback(id, queue, "daily", "remember");
    jdbc.update("UPDATE review_schedule SET due_date=? WHERE owner_id=?", today, owner);
    jdbc.update(
        "UPDATE word_study_state SET last_completed_date=? WHERE owner_id=?",
        today.minusDays(1),
        owner);
    jdbc.update(
        "UPDATE learning_content SET state='withdrawn',published_version_id=NULL WHERE id=?", id);
    Map<String, Object> blocked = reviewService.snapshot(owner, today);
    java.util.List<com.zhixing.model.ReviewItemView> items =
        (java.util.List<com.zhixing.model.ReviewItemView>) blocked.get("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getAvailable()).isFalse();
    assertThat(items.get(0).getWordReview()).isTrue();
    assertThat(blocked.get("completedCount")).isEqualTo(0);
    assertThat(study.queue(owner, "review", null).get("current")).isNull();
    jdbc.update(
        "UPDATE learning_content SET state='published',published_version_id=current_version_id WHERE id=?",
        id);
    feedback(id, study.queue(owner, "review", null), "review", "remember");
    Map<String, Object> finished = reviewService.snapshot(owner, today);
    assertThat((java.util.List<?>) finished.get("items")).isEmpty();
    assertThat(finished.get("completedCount")).isEqualTo(1);
    assertThat(reviewService.snapshot(CryptoUtils.randomId(), today).get("completedCount"))
        .isEqualTo(0);
  }

  @Test
  void recoveryDeletedWordKnowledgeDoesNotLeaveAGhostReviewQueue() {
    Map<String, Object> queue = study.queue(owner, "daily", null);
    String id = String.valueOf(((Map<?, ?>) queue.get("current")).get("contentId"));
    feedback(id, queue, "daily", "remember");
    jdbc.update("UPDATE review_schedule SET due_date=? WHERE owner_id=?", today, owner);
    jdbc.update(
        "UPDATE word_study_state SET last_completed_date=? WHERE owner_id=?",
        today.minusDays(1),
        owner);
    assertThat(study.queue(owner, "review", null).get("current")).isNotNull();
    jdbc.update("UPDATE knowledge_item SET state='deleted' WHERE owner_id=?", owner);
    assertThat(study.queue(owner, "review", null).get("current")).isNull();
    assertThat((java.util.List<?>) reviewService.snapshot(owner, today).get("items")).isEmpty();
  }
}
