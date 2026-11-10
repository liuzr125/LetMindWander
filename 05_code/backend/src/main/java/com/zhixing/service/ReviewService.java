package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.ReviewFeedbackRequest;
import com.zhixing.entity.ReviewFeedbackEntity;
import com.zhixing.entity.ReviewScheduleEntity;
import com.zhixing.mapper.ReviewMapper;
import com.zhixing.model.ReviewItemView;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final com.zhixing.mapper.WordStudyMapper studyMapper;
  private final ReviewMapper reviews;
  private final DailyTaskService dailyTasks;

  public ReviewService(
      ReviewMapper reviews,
      DailyTaskService dailyTasks,
      com.zhixing.mapper.WordStudyMapper studyMapper) {
    this.studyMapper = studyMapper;
    this.reviews = reviews;
    this.dailyTasks = dailyTasks;
  }

  public List<ReviewItemView> queue(String ownerId, LocalDate date) {
    if (date == null) date = LocalDate.now(BUSINESS_ZONE);
    return reviews.selectQueue(ownerId, date);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(String ownerId, LocalDate date) {
    if (date == null) date = LocalDate.now(BUSINESS_ZONE);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("businessDate", date.toString());
    result.put("items", queue(ownerId, date));
    result.put("completedCount", reviews.countCompletedOn(ownerId, date));
    return result;
  }

  @Transactional
  public List<ReviewItemView> feedback(
      String ownerId, String scheduleId, ReviewFeedbackRequest request) {
    String value = request == null ? null : request.getFeedback();
    if (!Arrays.asList("remember", "fuzzy", "unclear").contains(value))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_REVIEW_FEEDBACK", "复习反馈仅支持 remember、fuzzy 或 unclear");
    ReviewScheduleEntity schedule = reviews.selectScheduleForUpdate(ownerId, scheduleId);
    if (schedule == null || !"active".equals(schedule.getState()))
      throw new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "复习项不存在或已结束");
    if (studyMapper.countWordKnowledge(schedule.getKnowledgeId()) > 0)
      throw new ApiException(HttpStatus.CONFLICT, "WORD_STUDY_REQUIRED", "单词请使用回忆与补练队列");
    LocalDate today = LocalDate.now(BUSINESS_ZONE);
    ReviewFeedbackEntity record =
        reviews.selectFeedbackForUpdate(ownerId, schedule.getKnowledgeId(), today);
    int beforeStage = record == null ? schedule.getStage() : record.getBeforeStage();
    String beforeState = record == null ? schedule.getState() : record.getBeforeState();
    LocalDate beforeDue = record == null ? schedule.getDueDate() : record.getBeforeDueDate();
    Outcome outcome = outcome(value, beforeStage, today);
    Instant now = Instant.now();
    if (record == null) {
      record = new ReviewFeedbackEntity();
      record.setId(CryptoUtils.randomId());
      record.setOwnerId(ownerId);
      record.setKnowledgeId(schedule.getKnowledgeId());
      record.setScheduleId(scheduleId);
      record.setBusinessDate(today);
      record.setBeforeStage(beforeStage);
      record.setBeforeState(beforeState);
      record.setBeforeDueDate(beforeDue);
      record.setRevisionNo(1);
    } else record.setRevisionNo(record.getRevisionNo() + 1);
    record.setFeedback(value);
    record.setAfterStage(outcome.stage);
    record.setAfterState(outcome.state);
    record.setAfterDueDate(outcome.dueDate);
    record.setTaskId(request.getTaskId());
    record.setEffectiveAt(now);
    if (record.getRevisionNo() == 1) reviews.insertFeedback(record);
    else reviews.updateFeedback(record);
    reviews.updateSchedule(
        scheduleId, ownerId, outcome.state, outcome.stage, outcome.dueDate, record.getId(), now);
    if ("unclear".equals(value)) reviews.markLearning(ownerId, schedule.getKnowledgeId());
    if (request.getTaskId() != null)
      dailyTasks.completeReviewTask(ownerId, request.getTaskId(), schedule.getKnowledgeId());
    return queue(ownerId, today);
  }

  private Outcome outcome(String feedback, int stage, LocalDate date) {
    if ("fuzzy".equals(feedback)) return new Outcome(stage, "active", date.plusDays(1));
    if ("unclear".equals(feedback)) return new Outcome(0, "active", date.plusDays(1));
    if (stage >= 3) return new Outcome(3, "completed", null);
    int next = stage + 1;
    int[] intervals = {1, 3, 7, 14};
    return new Outcome(next, "active", date.plusDays(intervals[next]));
  }

  private static class Outcome {
    final int stage;
    final String state;
    final LocalDate dueDate;

    Outcome(int stage, String state, LocalDate dueDate) {
      this.stage = stage;
      this.state = state;
      this.dueDate = dueDate;
    }
  }
}
