package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for the transaction-owned word recall queue. */
public interface WordStudyMapper {
  List<Map<String, Object>> selectCursorOwner(@Param("owner") String owner);

  int insertCursor(@Param("owner") String owner);

  Long selectCursorPosition(@Param("owner") String owner);

  List<String> selectLegacyLearningWords(@Param("owner") String owner, @Param("book") String book);

  List<Map<String, Object>> selectUnstartedWordTasks(
      @Param("owner") String owner, @Param("date") java.sql.Date date);

  List<Map<String, Object>> selectDailyPending(
      @Param("owner") String owner, @Param("date") java.sql.Date date);

  List<Map<String, Object>> selectConsolidationPending(
      @Param("owner") String owner, @Param("date") java.sql.Date date);

  int countConsolidation(@Param("owner") String owner, @Param("date") java.sql.Date date);

  Map<String, Object> selectDailyProgress(
      @Param("owner") String owner, @Param("date") java.sql.Date date);

  int countDailyPendingContent(
      @Param("owner") String owner,
      @Param("content") String content,
      @Param("date") java.sql.Date date);

  List<Map<String, Object>> selectDueWordReviews(
      @Param("owner") String owner, @Param("date") java.sql.Date date);

  int activateReviewState(
      @Param("task") String task,
      @Param("schedule") String schedule,
      @Param("date") java.sql.Date date,
      @Param("owner") String owner,
      @Param("content") String content);

  int convertNewStateToReview(
      @Param("task") String task,
      @Param("schedule") String schedule,
      @Param("owner") String owner,
      @Param("content") String content);

  int bindReviewTask(
      @Param("task") String task,
      @Param("schedule") String schedule,
      @Param("owner") String owner,
      @Param("content") String content);

  List<Map<String, Object>> selectActiveContentSchedules(
      @Param("owner") String owner, @Param("content") String content);

  List<String> selectLearnedStatuses(
      @Param("owner") String owner, @Param("content") String content);

  int resetPendingForNewDay(
      @Param("date") java.sql.Date date,
      @Param("owner") String owner,
      @Param("dateAgain") java.sql.Date dateAgain);

  int enableFreeStudy(@Param("owner") String owner, @Param("content") String content);

  int bindNewTask(
      @Param("task") String task,
      @Param("originTask") String originTask,
      @Param("owner") String owner,
      @Param("content") String content);

  int insertState(
      @Param("owner") String owner,
      @Param("content") String content,
      @Param("kind") String kind,
      @Param("task") String task,
      @Param("schedule") String schedule,
      @Param("date") java.sql.Date date,
      @Param("firstSeen") java.sql.Date firstSeen,
      @Param("interval") int interval);

  Integer countLegacyLearning(@Param("owner") String owner, @Param("content") String content);

  int markExposed(@Param("owner") String owner, @Param("content") String content);

  int bindOriginTask(
      @Param("task") String task, @Param("owner") String owner, @Param("content") String content);

  int setUnclearRecovery(@Param("owner") String owner, @Param("content") String content);

  List<Map<String, Object>> selectState(
      @Param("owner") String owner, @Param("content") String content);

  List<String> selectReviewTask(
      @Param("owner") String owner,
      @Param("schedule") String schedule,
      @Param("date") java.sql.Date date);

  List<Map<String, Object>> selectAttemptReceipt(
      @Param("owner") String owner, @Param("attempt") String attempt);

  Integer countDueActiveSchedule(
      @Param("owner") String owner,
      @Param("schedule") String schedule,
      @Param("date") java.sql.Date date);

  int advanceCursor(@Param("position") long position, @Param("owner") String owner);

  List<Map<String, Object>> selectTaskForStart(
      @Param("task") String task, @Param("owner") String owner);

  int updateFeedbackState(
      @Param("streak") int streak,
      @Param("recovery") String recovery,
      @Param("pending") int pending,
      @Param("position") long position,
      @Param("eligibleAt") java.sql.Timestamp eligibleAt,
      @Param("owner") String owner,
      @Param("content") String content);

  int insertAttemptReceipt(
      @Param("owner") String owner,
      @Param("attempt") String attempt,
      @Param("content") String content,
      @Param("requestJson") String requestJson,
      @Param("resultJson") String resultJson);

  List<Map<String, Object>> selectScheduleSnapshot(
      @Param("owner") String owner, @Param("knowledge") String knowledge);

  int updateCompletedState(
      @Param("interval") int interval,
      @Param("mode") String mode,
      @Param("date") java.sql.Date date,
      @Param("schedule") String schedule,
      @Param("owner") String owner,
      @Param("content") String content);

  int insertReviewFeedback(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("knowledge") String knowledge,
      @Param("schedule") String schedule,
      @Param("date") java.sql.Date date,
      @Param("feedback") String feedback,
      @Param("beforeStage") int beforeStage,
      @Param("beforeDue") java.sql.Date beforeDue,
      @Param("afterStage") int afterStage,
      @Param("afterDue") java.sql.Date afterDue,
      @Param("task") String task,
      @Param("effectiveAt") java.sql.Timestamp effectiveAt);

  int bindLastFeedback(@Param("feedback") String feedback, @Param("schedule") String schedule);

  List<String> selectOriginalTaskStatus(@Param("task") String task, @Param("owner") String owner);

  List<String> selectTaskStatus(@Param("task") String task, @Param("owner") String owner);

  List<Integer> selectStage(@Param("owner") String owner, @Param("content") String content);

  List<Map<String, Object>> selectAuditSchedule(
      @Param("owner") String owner, @Param("content") String content);

  int insertStudyEvent(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("content") String content,
      @Param("attempt") String attempt,
      @Param("feedback") String feedback,
      @Param("kind") String kind,
      @Param("wordTerm") String wordTerm,
      @Param("mode") String mode,
      @Param("beforeStage") int beforeStage,
      @Param("afterStage") int afterStage,
      @Param("beforeInterval") int beforeInterval,
      @Param("afterInterval") int afterInterval,
      @Param("nextDue") java.sql.Date nextDue,
      @Param("streak") int streak,
      @Param("completed") int completed,
      @Param("eventAt") java.sql.Timestamp eventAt);

  List<String> selectCurrentRounds(@Param("owner") String owner, @Param("content") String content);

  int insertEventRound(@Param("event") String event, @Param("round") String round);

  int deleteBookStates(@Param("owner") String owner, @Param("book") String book);

  List<Map<String, Object>> selectPending(
      @Param("owner") String owner,
      @Param("date") java.sql.Date date,
      @Param("book") String book,
      @Param("reviewOnly") boolean reviewOnly);

  int countCompletedWordTask(
      @Param("owner") String owner, @Param("content") String content, @Param("task") String task);

  int countWordKnowledge(@Param("knowledge") String knowledge);

  int countCompletedReviewTask(
      @Param("owner") String owner,
      @Param("knowledge") String knowledge,
      @Param("task") String task);
}
