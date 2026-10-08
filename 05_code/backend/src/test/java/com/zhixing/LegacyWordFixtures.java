package com.zhixing;

import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.service.BookStudyService;
import java.sql.Timestamp;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Historical compatibility fixtures only. Recreates pre-consolidation persisted data; deliberately
 * does not call old one-click endpoints, which must now reject words.
 */
final class LegacyWordFixtures {
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  static void learn(
      JdbcTemplate jdbc,
      ContentMapper mapper,
      BookStudyService history,
      String owner,
      String content,
      String feedback) {
    Instant now = Instant.now();
    LocalDate today = now.atZone(ZONE).toLocalDate();
    String version =
        jdbc.queryForObject(
            "SELECT published_version_id FROM learning_content WHERE id=?", String.class, content);
    if ("understood".equals(feedback))
      mapper.markUnderstood(
          CryptoUtils.randomId(), owner, content, "word:" + content, version, now);
    else {
      boolean remembered = "remember".equals(feedback);
      mapper.upsertWordFeedback(
          CryptoUtils.randomId(),
          owner,
          content,
          "word:" + content,
          version,
          remembered ? "understood" : "learning",
          remembered ? 85 : "fuzzy".equals(feedback) ? 50 : 0,
          now);
      String knowledge = mapper.selectContentKnowledgeId(owner, content);
      if (knowledge == null) {
        knowledge = CryptoUtils.randomId();
        mapper.insertContentKnowledge(
            knowledge,
            owner,
            content,
            version,
            jdbc.queryForObject(
                "SELECT title FROM content_version WHERE id=?", String.class, version));
      }
      if (mapper.updateFeedbackSchedule(
              owner, knowledge, remembered ? 1 : 0, today.plusDays(remembered ? 3 : 1))
          == 0)
        mapper.insertFeedbackSchedule(
            CryptoUtils.randomId(),
            owner,
            knowledge,
            remembered ? 1 : 0,
            today.plusDays(remembered ? 3 : 1));
    }
    mapper.insertLearningEvent(
        CryptoUtils.randomId(),
        owner,
        mapper.selectLearningRecordId(owner, content),
        mapper.selectLearningRecordVersion(owner, content),
        null,
        feedback,
        today,
        now);
    history.record(owner, content, BookStudyService.KIND_STUDY, now);
  }

  static void reviewed(JdbcTemplate jdbc, BookStudyService history, String owner, String schedule) {
    Instant now = Instant.now();
    LocalDate today = now.atZone(ZONE).toLocalDate();
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT knowledge_id,stage,state,due_date FROM review_schedule WHERE id=? AND owner_id=?",
            schedule,
            owner);
    String id = CryptoUtils.randomId();
    int stage = ((Number) row.get("stage")).intValue(), next = stage + 1;
    LocalDate due = today.plusDays(new int[] {1, 3, 7, 14}[next]);
    jdbc.update(
        "INSERT INTO review_feedback(id,owner_id,knowledge_id,schedule_id,business_date,feedback,before_stage,before_state,before_due_date,after_stage,after_state,after_due_date,effective_at) VALUES(?,?,?,?,?,'remember',?,?,?,?,'active',?,?)",
        id,
        owner,
        row.get("knowledge_id"),
        schedule,
        today,
        stage,
        row.get("state"),
        row.get("due_date"),
        next,
        due,
        Timestamp.from(now));
    jdbc.update(
        "UPDATE review_schedule SET stage=?,due_date=?,last_feedback_id=?,updated_at=?,version_no=version_no+1 WHERE id=?",
        next,
        due,
        id,
        Timestamp.from(now),
        schedule);
    history.recordReview(owner, schedule, now);
  }
}
