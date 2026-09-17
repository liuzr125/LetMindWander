package com.zhixing.mapper;

import com.zhixing.model.*;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WordMemoryMapper {
    @Select("SELECT h.id,h.content_version_id,h.sense_id,h.method_type,h.hint_body AS body,h.level_code,h.source_type,h.hint_version AS version " +
            "FROM word_memory_hint h JOIN learning_content lc ON lc.published_version_id=h.content_version_id " +
            "WHERE lc.id=#{contentId} AND lc.content_type='word' AND lc.state='published' AND h.state='published' AND h.withdrawn_at IS NULL " +
            "AND (#{senseId} IS NULL OR h.sense_id=#{senseId}) ORDER BY h.sense_id,h.method_type,h.hint_version DESC")
    List<WordMemoryHintView> selectHints(@Param("contentId") String contentId,@Param("senseId") String senseId);

    @Select("SELECT q.id AS question_id,q.content_version_id,q.sense_id,q.dimension,q.prompt_text,q.expected_answer," +
            "q.accepted_answers_json,q.answer_policy,q.hint_text,q.question_version,lc.id AS content_id,cv.word_term,cv.meaning " +
            "FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id " +
            "JOIN word_memory_question q ON q.content_version_id=cv.id " +
            "WHERE lc.id=#{contentId} AND lc.content_type='word' AND lc.state='published' AND q.dimension=#{dimension} " +
            "AND q.state='published' AND q.withdrawn_at IS NULL ORDER BY q.question_version DESC,q.id LIMIT 1")
    WordMemoryEpisodeRow selectQuestion(@Param("contentId") String contentId,@Param("dimension") String dimension);

    @Select("SELECT COUNT(*) FROM daily_task WHERE id=#{taskId} AND owner_id=#{ownerId} AND content_id=#{contentId} " +
            "AND task_type='word' AND status NOT IN ('CANCELLED','DONE')")
    int countOwnedWordTask(@Param("ownerId") String ownerId,@Param("taskId") String taskId,@Param("contentId") String contentId);

    @Insert("INSERT INTO word_memory_session (id,owner_id,source_type,return_to,task_id,business_date,target_count,required_dimensions," +
            "add_to_review,state,version_no,idempotency_key,created_at,updated_at) VALUES (#{id},#{ownerId},#{sourceType},#{returnTo},#{taskId}," +
            "#{businessDate},#{targetCount},#{requiredDimensions},#{addToReview},'active',1,#{idempotencyKey},#{createdAt},#{updatedAt})")
    int insertSession(WordMemorySessionRow row);

    @Select("SELECT * FROM word_memory_session WHERE owner_id=#{ownerId} AND idempotency_key=#{key}")
    WordMemorySessionRow selectSessionByIdempotency(@Param("ownerId") String ownerId,@Param("key") String key);

    @Select("SELECT * FROM word_memory_session WHERE id=#{id} AND owner_id=#{ownerId}")
    WordMemorySessionRow selectSession(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT * FROM word_memory_session WHERE id=#{id} AND owner_id=#{ownerId} FOR UPDATE")
    WordMemorySessionRow selectSessionForUpdate(@Param("ownerId") String ownerId,@Param("id") String id);

    @Insert("INSERT INTO word_memory_episode (id,session_id,content_id,content_version_id,sense_id,question_id,question_version,dimension," +
            "position_no,state,hint_used,answer_revealed,attempt_count,created_at,updated_at) VALUES (#{id},#{sessionId},#{contentId}," +
            "#{contentVersionId},#{senseId},#{questionId},#{questionVersion},#{dimension},#{positionNo},'pending',0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
    int insertEpisode(WordMemoryEpisodeRow row);

    @Select("SELECT e.*,q.prompt_text,q.expected_answer,q.accepted_answers_json,q.answer_policy,q.hint_text,cv.word_term,cv.meaning " +
            "FROM word_memory_episode e JOIN word_memory_question q ON q.id=e.question_id JOIN content_version cv ON cv.id=e.content_version_id " +
            "WHERE e.session_id=#{sessionId} AND e.state IN ('pending','retry_pending') " +
            "ORDER BY CASE WHEN e.attempt_count=0 THEN 0 ELSE 1 END,e.position_no LIMIT 1")
    WordMemoryEpisodeRow selectNextEpisode(@Param("sessionId") String sessionId);

    @Select("SELECT e.*,q.prompt_text,q.expected_answer,q.accepted_answers_json,q.answer_policy,q.hint_text,cv.word_term,cv.meaning " +
            "FROM word_memory_episode e JOIN word_memory_question q ON q.id=e.question_id JOIN content_version cv ON cv.id=e.content_version_id " +
            "WHERE e.id=#{episodeId} AND e.session_id=#{sessionId} FOR UPDATE")
    WordMemoryEpisodeRow selectEpisodeForUpdate(@Param("sessionId") String sessionId,@Param("episodeId") String episodeId);

    @Select("SELECT e.*,q.prompt_text,q.expected_answer,q.accepted_answers_json,q.answer_policy,q.hint_text,cv.word_term,cv.meaning " +
            "FROM word_memory_episode e JOIN word_memory_question q ON q.id=e.question_id JOIN content_version cv ON cv.id=e.content_version_id " +
            "WHERE e.session_id=#{sessionId} ORDER BY e.position_no")
    List<WordMemoryEpisodeRow> selectEpisodes(@Param("sessionId") String sessionId);

    @Update("UPDATE word_memory_episode SET hint_used=1,answer_revealed=CASE WHEN #{answerRevealed}=1 THEN 1 ELSE answer_revealed END," +
            "updated_at=#{now} WHERE id=#{episodeId} AND session_id=#{sessionId}")
    int markHint(@Param("sessionId") String sessionId,@Param("episodeId") String episodeId,
                 @Param("answerRevealed") int answerRevealed,@Param("now") Instant now);

    @Insert("INSERT INTO word_memory_hint_event (id,owner_id,session_id,episode_id,hint_type,answer_revealed,occurred_at) " +
            "VALUES (#{id},#{ownerId},#{sessionId},#{episodeId},#{hintType},#{answerRevealed},#{now})")
    int insertHintEvent(@Param("id") String id,@Param("ownerId") String ownerId,@Param("sessionId") String sessionId,
                        @Param("episodeId") String episodeId,@Param("hintType") String hintType,
                        @Param("answerRevealed") int answerRevealed,@Param("now") Instant now);

    @Select("SELECT * FROM word_memory_attempt WHERE owner_id=#{ownerId} AND idempotency_key=#{key}")
    WordMemoryAttemptRow selectAttemptByIdempotency(@Param("ownerId") String ownerId,@Param("key") String key);

    @Insert("INSERT INTO word_memory_attempt (id,owner_id,session_id,episode_id,attempt_no,answer_text,verdict,result_type,hint_used," +
            "first_attempt,duration_ms,idempotency_key,submitted_at) VALUES (#{id},#{ownerId},#{sessionId},#{episodeId},#{attemptNo}," +
            "#{answer},#{verdict},#{resultType},#{hintUsed},#{firstAttempt},#{durationMs},#{idempotencyKey},#{submittedAt})")
    int insertAttempt(@Param("id") String id,@Param("ownerId") String ownerId,@Param("sessionId") String sessionId,
                      @Param("episodeId") String episodeId,@Param("attemptNo") int attemptNo,@Param("answer") String answer,
                      @Param("verdict") String verdict,@Param("resultType") String resultType,@Param("hintUsed") int hintUsed,
                      @Param("firstAttempt") int firstAttempt,@Param("durationMs") Integer durationMs,
                      @Param("idempotencyKey") String idempotencyKey,@Param("submittedAt") Instant submittedAt);

    @Update("UPDATE word_memory_episode SET state=#{state},attempt_count=#{attemptCount}," +
            "first_result=CASE WHEN first_result IS NULL THEN #{resultType} ELSE first_result END,final_result=#{resultType},updated_at=#{now} " +
            "WHERE id=#{episodeId} AND session_id=#{sessionId}")
    int updateEpisodeAfterAttempt(@Param("sessionId") String sessionId,@Param("episodeId") String episodeId,
                                  @Param("state") String state,@Param("attemptCount") int attemptCount,
                                  @Param("resultType") String resultType,@Param("now") Instant now);

    @Update("UPDATE word_memory_session SET state=#{state},version_no=version_no+1,completed_at=#{completedAt},updated_at=#{now} " +
            "WHERE id=#{id} AND owner_id=#{ownerId} AND version_no=#{expectedVersion}")
    int advanceSession(@Param("ownerId") String ownerId,@Param("id") String id,@Param("expectedVersion") int expectedVersion,
                       @Param("state") String state,@Param("completedAt") Instant completedAt,@Param("now") Instant now);

    @Select("SELECT DISTINCT content_id FROM word_memory_episode WHERE session_id=#{sessionId}")
    List<String> selectSessionContentIds(@Param("sessionId") String sessionId);

    @Select("SELECT DISTINCT e.content_id,cv.word_term,COALESCE(rs.state,'not_enrolled') AS state,rs.due_date " +
            "FROM word_memory_episode e JOIN content_version cv ON cv.id=e.content_version_id " +
            "JOIN word_memory_session s ON s.id=e.session_id " +
            "LEFT JOIN knowledge_item k ON k.owner_id=s.owner_id AND k.bookmark_content_id=e.content_id AND k.state<>'deleted' " +
            "LEFT JOIN review_schedule rs ON rs.owner_id=s.owner_id AND rs.knowledge_id=k.id " +
            "WHERE e.session_id=#{sessionId} AND s.owner_id=#{ownerId} ORDER BY cv.word_term")
    List<WordMemoryReviewPlanView> selectReviewPlans(@Param("ownerId") String ownerId,@Param("sessionId") String sessionId);

    @Insert("INSERT INTO word_memory_evidence (id,owner_id,content_id,sense_id,dimension,business_date,episode_id,first_attempt_id," +
            "first_result,hint_used,rule_version) SELECT #{id},s.owner_id,e.content_id,e.sense_id,e.dimension,s.business_date,e.id,a.id," +
            "e.first_result,e.hint_used,#{ruleVersion} FROM word_memory_episode e JOIN word_memory_session s ON s.id=e.session_id " +
            "JOIN word_memory_attempt a ON a.episode_id=e.id AND a.first_attempt=1 WHERE e.id=#{episodeId} AND e.first_result IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM word_memory_evidence x WHERE x.episode_id=e.id)")
    int insertEvidence(@Param("id") String id,@Param("episodeId") String episodeId,@Param("ruleVersion") String ruleVersion);
}
