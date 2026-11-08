package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.mapper.VocabularyBookMapper;
import com.zhixing.mapper.WordStudyMapper;
import com.zhixing.model.ContentDetailView;
import com.zhixing.model.DailyPackageView;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-owned recall queue. Owner lock, version and attempt receipt protect every transition. */
@Service
public class WordStudyService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(WordStudyService.class);
  static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private final WordStudyMapper studyMapper;
  private final ContentMapper contents;
  private final DailyTaskService tasks;
  private final VocabularyBookMapper books;
  private final BookStudyService history;
  private final WordStudyPreferenceService preferences;
  private final ObjectMapper json;

  public WordStudyService(
      WordStudyMapper studyMapper,
      ContentMapper contents,
      DailyTaskService tasks,
      VocabularyBookMapper books,
      BookStudyService history,
      WordStudyPreferenceService preferences,
      ObjectMapper json) {
    this.studyMapper = studyMapper;
    this.contents = contents;
    this.tasks = tasks;
    this.books = books;
    this.history = history;
    this.preferences = preferences;
    this.json = json;
  }

  LocalDate today() {
    return LocalDate.now(ZONE);
  }

  Instant now() {
    return Instant.now();
  }

  private void lock(String owner) {
    books.lockOwner(owner);
    if (studyMapper.selectCursorOwner(owner).isEmpty()) studyMapper.insertCursor(owner);
  }

  private long position(String owner) {
    return studyMapper.selectCursorPosition(owner);
  }

  /**
   * Initialize missing state and rebind due tasks under the owner lock; completed words stay hidden
   * until due.
   */
  @Transactional
  public Map<String, Object> queue(String owner, String mode, String contentId) {
    lock(owner);
    LocalDate date = today();
    if ("daily".equals(mode)) {
      DailyPackageView pack = tasks.selectWords(owner, date);
      if (pack.getTasks().stream()
          .anyMatch(t -> "word".equals(t.getTaskType()) && "SKIPPED".equals(t.getStatus())))
        tasks.resumeSkippedWords(owner, date);
    } else tasks.get(owner, date);
    // Lazy compatibility: old learning records remain pending, understood records remain learned.
    String book = books.selectActiveBookId(owner);
    if (book != null) {
      for (String id : studyMapper.selectLegacyLearningWords(owner, book))
        ensure(owner, id, "new", null, null, 1);
    }
    for (Map<String, Object> t :
        studyMapper.selectUnstartedWordTasks(owner, java.sql.Date.valueOf(date)))
      ensure(owner, str(t, "content_id"), "new", str(t, "id"), null, 1);
    for (Map<String, Object> r :
        studyMapper.selectDueWordReviews(owner, java.sql.Date.valueOf(date))) {
      String id = str(r, "source_content_id");
      Map<String, Object> existing = state(owner, id);
      String taskId = reviewTask(owner, str(r, "id"));
      if (existing == null)
        ensure(owner, id, "review", taskId, str(r, "id"), legacyInterval(num(r, "stage")));
      else if (num(existing, "pending") == 0 && !date.equals(day(existing, "last_completed_date")))
        studyMapper.activateReviewState(
            taskId, str(r, "id"), java.sql.Date.valueOf(date), owner, id);
      else if (num(existing, "pending") == 1) {
        if ("new".equals(str(existing, "source_kind")))
          studyMapper.convertNewStateToReview(taskId, str(r, "id"), owner, id);
        else studyMapper.bindReviewTask(taskId, str(r, "id"), owner, id);
      }
    }
    if (contentId != null
        && !contentId.isEmpty()
        && !"daily".equals(mode)
        && !"consolidation".equals(mode)) {
      required(owner, contentId);
      Map<String, Object> old = state(owner, contentId);
      if (old == null) {
        List<Map<String, Object>> schedules =
            studyMapper.selectActiveContentSchedules(owner, contentId);
        List<String> learned = studyMapper.selectLearnedStatuses(owner, contentId);
        if (learned.isEmpty() && schedules.isEmpty())
          ensure(owner, contentId, "new", null, null, 1);
      }
    }
    // Across midnight a partial success must be confirmed afresh; unfinished work survives.
    studyMapper.resetPendingForNewDay(
        java.sql.Date.valueOf(date), owner, java.sql.Date.valueOf(date));
    if (contentId != null
        && !"daily".equals(mode)
        && !"consolidation".equals(mode)
        && state(owner, contentId) != null) studyMapper.enableFreeStudy(owner, contentId);
    return view(owner, mode, contentId);
  }

  private void ensure(
      String owner, String id, String kind, String task, String schedule, int interval) {
    Map<String, Object> old = state(owner, id);
    if (old != null) {
      if ("new".equals(kind)
          && task != null
          && (num(old, "exposed") == 0
              || (num(old, "pending") == 1 && "new".equals(str(old, "source_kind")))))
        studyMapper.bindNewTask(task, task, owner, id);
      return;
    }
    ContentDetailView word = required(owner, id);
    studyMapper.insertState(
        owner,
        id,
        kind,
        task,
        schedule,
        java.sql.Date.valueOf(today()),
        java.sql.Date.valueOf(today()),
        interval);
    if ("new".equals(kind) && studyMapper.countLegacyLearning(owner, id) > 0)
      studyMapper.markExposed(owner, id);
    if ("new".equals(kind) && task != null) studyMapper.bindOriginTask(task, owner, id);
    if ("review".equals(kind) && !Boolean.TRUE.equals(word.getUnderstood()))
      studyMapper.setUnclearRecovery(owner, id);
  }

  private Map<String, Object> state(String owner, String id) {
    List<Map<String, Object>> rows = studyMapper.selectState(owner, id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private String reviewTask(String owner, String schedule) {
    List<String> rows =
        studyMapper.selectReviewTask(owner, schedule, java.sql.Date.valueOf(today()));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private ContentDetailView required(String owner, String id) {
    ContentDetailView word = contents.selectDetail(owner, id);
    if (word == null || !"word".equals(word.getContentType()))
      throw fail("WORD_NOT_AVAILABLE", "词条不存在或已下架");
    return word;
  }

  private List<Map<String, Object>> pending(String owner, String mode) {
    if ("daily".equals(mode))
      return studyMapper.selectDailyPending(owner, java.sql.Date.valueOf(today()));
    if ("consolidation".equals(mode))
      return studyMapper.selectConsolidationPending(owner, java.sql.Date.valueOf(today()));
    return studyMapper.selectPending(
        owner,
        java.sql.Date.valueOf(today()),
        books.selectActiveBookId(owner),
        "review".equals(mode));
  }

  boolean ready(Map<String, Object> s, long position) {
    return position >= lng(s, "eligible_position")
        || s.get("eligible_at") == null
        || !now().isBefore(databaseInstant(s.get("eligible_at")));
  }

  private Map<String, Object> view(String owner, String mode) {
    return view(owner, mode, null, null);
  }

  private Map<String, Object> view(String owner, String mode, String preferred) {
    return view(owner, mode, preferred, null);
  }

  private Map<String, Object> view(
      String owner, String mode, String preferred, String appliedMode) {
    List<Map<String, Object>> rows = pending(owner, mode);
    long pos = position(owner);
    Map<String, Object> current = null;
    int fresh = 0, consolidation = 0, review = 0;
    long wait = Long.MAX_VALUE;
    boolean continuous = "daily".equals(mode);
    for (Map<String, Object> s : rows) {
      boolean isReview = "review".equals(str(s, "source_kind"));
      if (isReview && s.get("recovery") == null) review++;
      else if (isReview) consolidation++;
      else if (num(s, "exposed") == 0) fresh++;
      else consolidation++;
      if ((continuous || ready(s, pos))
          && (current == null || str(s, "content_id").equals(preferred))) current = s;
      if (s.get("eligible_at") != null)
        wait =
            Math.min(
                wait,
                Math.max(
                    0,
                    Duration.between(now(), databaseInstant(s.get("eligible_at"))).getSeconds()));
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("businessDate", today().toString());
    result.put("intervalMode", appliedMode == null ? preferences.intervalMode(owner) : appliedMode);
    result.put("newCount", fresh);
    result.put("consolidationCount", consolidation);
    result.put("reviewCount", review);
    result.put("remaining", rows.size());
    result.put("waitSeconds", !continuous && current == null && wait != Long.MAX_VALUE ? wait : 0);
    result.put("practiceMode", continuous ? "continuous" : "spaced");
    if ("daily".equals(mode) || "consolidation".equals(mode)) {
      Map<String, Object> progress =
          continuous
              ? studyMapper.selectDailyProgress(owner, java.sql.Date.valueOf(today()))
              : null;
      int unfinished =
          continuous
              ? num(progress, "unfinished_count")
              : studyMapper.countConsolidation(owner, java.sql.Date.valueOf(today()));
      int unavailable =
          continuous ? num(progress, "unavailable_count") : Math.max(0, unfinished - rows.size());
      if (continuous) {
        result.put("dailyTotal", num(progress, "total_count"));
        result.put("dailyRecognized", num(progress, "recognized_count"));
      }
      result.put("unfinishedCount", unfinished);
      result.put("unavailableCount", unavailable);
      result.put("allCompleted", unfinished == 0);
    }
    if (current != null) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("contentId", str(current, "content_id"));
      item.put("wordTerm", str(current, "word_term"));
      item.put("version", num(current, "version_no"));
      item.put("streak", num(current, "streak"));
      item.put("kind", str(current, "source_kind"));
      result.put("current", item);
    }
    return result;
  }

  /** Persist feedback, schedule, task completion and the replay receipt in the same transaction. */
  @Transactional
  public Map<String, Object> feedback(String owner, String id, Map<String, Object> request) {
    lock(owner);
    String attempt = String.valueOf(request.getOrDefault("attemptId", ""));
    String feedback = String.valueOf(request.get("feedback"));
    String mode =
        "review".equals(request.get("mode"))
            ? "review"
            : "daily".equals(request.get("mode"))
                ? "daily"
                : "consolidation".equals(request.get("mode")) ? "consolidation" : "all";
    if (attempt.isEmpty()
        || attempt.length() > 96
        || !Arrays.asList("remember", "fuzzy", "unclear").contains(feedback))
      throw fail("WORD_FEEDBACK_INVALID", "缺少反馈或请求标识");
    String encoded = encode(new TreeMap<String, Object>(request));
    List<Map<String, Object>> receipts = studyMapper.selectAttemptReceipt(owner, attempt);
    if (!receipts.isEmpty()) {
      Map<String, Object> receipt = receipts.get(0);
      if (!id.equals(str(receipt, "content_id")) || !encoded.equals(str(receipt, "request_json")))
        throw fail("WORD_ATTEMPT_REUSED", "请求标识已用于另一反馈");
      LOG.debug("Replaying a persisted word feedback receipt");
      return decode(str(receipt, "result_json"));
    }
    Map<String, Object> s = state(owner, id);
    if ("daily".equals(mode)
        && studyMapper.countDailyPendingContent(owner, id, java.sql.Date.valueOf(today())) == 0)
      throw fail("WORD_OUTSIDE_DAILY_SELECTION", "该单词不在今日待学范围内，请刷新学习队列");
    if ("consolidation".equals(mode)
        && studyMapper.selectConsolidationPending(owner, java.sql.Date.valueOf(today())).stream()
            .noneMatch(row -> id.equals(str(row, "content_id"))))
      throw fail("WORD_OUTSIDE_CONSOLIDATION", "该单词不在待巩固范围内，请刷新队列");
    if (s == null || num(s, "pending") == 0) throw fail("WORD_STUDY_REQUIRED", "请刷新学习队列");
    if (!today().toString().equals(String.valueOf(request.get("businessDate")))
        || !today().equals(day(s, "business_date")))
      throw fail("WORD_DAY_CHANGED", "日期已变化，请刷新学习队列");
    if (!(request.get("expectedVersion") instanceof Number)
        || num(s, "version_no") != ((Number) request.get("expectedVersion")).intValue())
      throw fail("WORD_VERSION_CONFLICT", "学习进度已变化，请刷新");
    long cursor = position(owner);
    if (!"daily".equals(mode) && !ready(s, cursor))
      throw fail("WORD_RECALL_TOO_SOON", "先学习其他词，或稍后再回忆这个词");
    ContentDetailView word = required(owner, id);
    boolean review = "review".equals(str(s, "source_kind"));
    String recovery = str(s, "recovery");
    if (review
        && studyMapper.countDueActiveSchedule(
                owner, str(s, "schedule_id"), java.sql.Date.valueOf(today()))
            == 0) throw fail("WORD_REVIEW_CHANGED", "复习计划已变化，请刷新队列");
    int streak = "remember".equals(feedback) ? num(s, "streak") + 1 : 0;
    if (!"remember".equals(feedback))
      recovery = "unclear".equals(feedback) || "unclear".equals(recovery) ? "unclear" : "fuzzy";
    // A single remember ends this business day's work; earlier failures still determine recovery
    // scheduling.
    boolean complete = "remember".equals(feedback);
    long position = cursor + 1;
    studyMapper.advanceCursor(position, owner);
    Instant moment = now();
    String studyTask = str(s, "task_id");
    if (!review && studyTask != null) {
      List<Map<String, Object>> taskRows = studyMapper.selectTaskForStart(studyTask, owner);
      if (!taskRows.isEmpty() && "TODO".equals(str(taskRows.get(0), "status"))) {
        com.zhixing.dto.TaskEventRequest start = new com.zhixing.dto.TaskEventRequest();
        start.setEventType("start");
        start.setExpectedVersion(num(taskRows.get(0), "version_no"));
        tasks.event(owner, studyTask, start);
      }
    }

    studyMapper.updateFeedbackState(
        streak,
        recovery,
        complete ? 0 : 1,
        position + 4,
        "daily".equals(mode) ? null : Timestamp.from(moment.plusSeconds(120)),
        owner,
        id);
    String status = complete ? "understood" : "learning";
    int familiarity = complete ? 85 : ("fuzzy".equals(feedback) ? 50 : 0);
    contents.upsertWordFeedback(
        CryptoUtils.randomId(),
        owner,
        id,
        "word:" + id,
        word.getVersionId(),
        status,
        familiarity,
        moment);
    contents.insertLearningEvent(
        CryptoUtils.randomId(),
        owner,
        contents.selectLearningRecordId(owner, id),
        contents.selectLearningRecordVersion(owner, id),
        str(s, "task_id"),
        feedback,
        today(),
        moment);
    history.record(
        owner, id, review ? BookStudyService.KIND_REVIEW : BookStudyService.KIND_STUDY, moment);
    String appliedMode = preferences.intervalMode(owner);
    int beforeStage = stage(owner, id);
    if (complete) complete(owner, id, s, recovery, review, moment, appliedMode, word);
    log(owner, id, attempt, feedback, s, complete, streak, beforeStage, moment, appliedMode, word);
    Map<String, Object> result = view(owner, mode, null, appliedMode);
    result.put("completed", complete);
    result.put("feedback", feedback);
    studyMapper.insertAttemptReceipt(owner, attempt, id, encoded, encode(result));
    // No owner, word, attempt identifier or request body is included in operational diagnostics.
    LOG.debug(
        "Word feedback prepared: completed={}, mode={}, remaining={}",
        complete,
        appliedMode,
        result.get("remaining"));
    return decode(encode(result));
  }

  /**
   * Apply the configured interval to the actual completion day and finish both current and origin
   * tasks.
   */
  private void complete(
      String owner,
      String id,
      Map<String, Object> s,
      String recovery,
      boolean review,
      Instant moment,
      String mode,
      ContentDetailView word) {
    String knowledge = contents.selectContentKnowledgeId(owner, id);
    if (knowledge == null) {
      knowledge = CryptoUtils.randomId();
      contents.insertContentKnowledge(knowledge, owner, id, word.getVersionId(), word.getTitle());
    }
    List<Map<String, Object>> schedules = studyMapper.selectScheduleSnapshot(owner, knowledge);
    int stage = schedules.isEmpty() ? 0 : num(schedules.get(0), "stage");
    java.sql.Date priorDue =
        schedules.isEmpty() ? null : (java.sql.Date) schedules.get(0).get("due_date");
    int oldInterval = num(s, "scheduled_interval");
    int interval = nextInterval(mode, review, recovery, oldInterval);
    if (review
        && recovery == null
        && "fixed".equals(mode)
        && "fixed".equals(str(s, "schedule_mode")))
      interval = new int[] {1, 3, 7, 14, 30, 60}[Math.min(5, stage + 1)];
    // Fixed stage is inferred from actual interval; flexible -> fixed uses the next larger ladder
    // step.
    int nextStage =
        review && recovery == null
            ? ("flexible".equals(mode) ? floorStage(interval) : fixedStage(interval))
            : ("unclear".equals(recovery) || !review ? 0 : stage);
    if (review
        && "fuzzy".equals(recovery)
        && "fixed".equals(mode)
        && "flexible".equals(str(s, "schedule_mode"))) nextStage = floorStage(oldInterval);
    LocalDate due = today().plusDays(interval);
    String scheduleId = schedules.isEmpty() ? CryptoUtils.randomId() : str(schedules.get(0), "id");
    if (schedules.isEmpty())
      contents.insertFeedbackSchedule(scheduleId, owner, knowledge, nextStage, due);
    else contents.updateFeedbackSchedule(owner, knowledge, nextStage, due);
    studyMapper.updateCompletedState(
        interval, mode, java.sql.Date.valueOf(today()), scheduleId, owner, id);
    if (review) {
      String feedbackId = CryptoUtils.randomId();
      // The recall queue permits only one completed review per word/business day.
      studyMapper.insertReviewFeedback(
          feedbackId,
          owner,
          knowledge,
          scheduleId,
          java.sql.Date.valueOf(today()),
          recovery == null ? "remember" : recovery,
          stage,
          priorDue,
          nextStage,
          java.sql.Date.valueOf(due),
          str(s, "task_id"),
          Timestamp.from(moment));
      studyMapper.bindLastFeedback(feedbackId, scheduleId);
    }
    String origin = str(s, "origin_task_id");
    if (origin != null && !origin.equals(str(s, "task_id"))) {
      List<String> originalStatus = studyMapper.selectOriginalTaskStatus(origin, owner);
      if (!originalStatus.isEmpty()
          && Arrays.asList("TODO", "DOING").contains(originalStatus.get(0)))
        tasks.completeContentTask(owner, origin, id, null);
    }
    String task = str(s, "task_id");
    if (task != null) {
      List<String> statuses = studyMapper.selectTaskStatus(task, owner);
      if (!statuses.isEmpty() && Arrays.asList("TODO", "DOING").contains(statuses.get(0))) {
        if (review) tasks.completeReviewTask(owner, task, knowledge);
        else tasks.completeContentTask(owner, task, id, null);
      }
    }
  }

  static int nextInterval(String mode, boolean review, String recovery, int old) {
    if (!review || "unclear".equals(recovery)) return 1;
    if ("flexible".equals(mode))
      return "fuzzy".equals(recovery)
          ? Math.max(1, (old + 1) / 2)
          : Math.min(60, Math.max(1, old) * 2);
    if (recovery != null) return 1;
    for (int days : new int[] {3, 7, 14, 30, 60}) if (days > old) return days;
    return 60;
  }

  private static int floorStage(int interval) {
    int stage = 0;
    int[] days = {1, 3, 7, 14, 30, 60};
    for (int i = 0; i < days.length; i++) if (interval >= days[i]) stage = i;
    return stage;
  }

  private static int fixedStage(int interval) {
    int[] days = {1, 3, 7, 14, 30, 60};
    for (int i = 0; i < days.length; i++) if (interval <= days[i]) return i;
    return 5;
  }

  private int stage(String owner, String id) {
    List<Integer> stages = studyMapper.selectStage(owner, id);
    return stages.isEmpty() ? 0 : stages.get(0);
  }

  private void log(
      String owner,
      String id,
      String attempt,
      String feedback,
      Map<String, Object> before,
      boolean complete,
      int streak,
      int beforeStage,
      Instant moment,
      String mode,
      ContentDetailView word) {
    Map<String, Object> after = state(owner, id);
    String event = CryptoUtils.randomId();
    List<Map<String, Object>> schedules = studyMapper.selectAuditSchedule(owner, id);
    studyMapper.insertStudyEvent(
        event,
        owner,
        id,
        attempt,
        feedback,
        str(before, "source_kind"),
        word.getWordTerm(),
        mode,
        beforeStage,
        schedules.isEmpty() ? 0 : num(schedules.get(0), "stage"),
        num(before, "scheduled_interval"),
        num(after, "scheduled_interval"),
        schedules.isEmpty() ? null : (java.sql.Date) schedules.get(0).get("due_date"),
        streak,
        complete ? 1 : 0,
        Timestamp.from(moment));
    // Capture round identity at the action, never reinterpret historical events after a book
    // switch.
    for (String round : studyMapper.selectCurrentRounds(owner, id))
      studyMapper.insertEventRound(event, round);
  }

  private int legacyInterval(int stage) {
    return new int[] {1, 3, 7, 14, 30, 60}[Math.max(0, Math.min(5, stage))];
  }

  @Transactional
  public void reset(String owner, String book) {
    lock(owner);
    studyMapper.deleteBookStates(owner, book);
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> decode(String value) {
    try {
      return json.readValue(value, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String str(Map<String, Object> row, String key) {
    Object v = row.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static int num(Map<String, Object> row, String key) {
    Object v = row.get(key);
    return v == null ? 0 : ((Number) v).intValue();
  }

  private static long lng(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }
  // Connector/J returns MySQL DATETIME as LocalDateTime; H2 returns Timestamp.
  // DATETIME is stored in the configured Asia/Shanghai database timezone.
  static Instant databaseInstant(Object value) {
    if (value instanceof Timestamp) return ((Timestamp) value).toInstant();
    if (value instanceof LocalDateTime) return ((LocalDateTime) value).atZone(ZONE).toInstant();
    if (value instanceof Instant) return (Instant) value;
    if (value instanceof OffsetDateTime) return ((OffsetDateTime) value).toInstant();
    throw new IllegalArgumentException(
        "Unsupported database time type: " + value.getClass().getName());
  }

  private static LocalDate day(Map<String, Object> row, String key) {
    Object v = row.get(key);
    return v == null
        ? null
        : v instanceof LocalDate ? (LocalDate) v : ((java.sql.Date) v).toLocalDate();
  }

  private ApiException fail(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }
}
