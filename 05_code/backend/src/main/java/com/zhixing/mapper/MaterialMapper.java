package com.zhixing.mapper;

import com.zhixing.model.MaterialCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

/** 今日任务素材查询集中在 Mapper，并排除该用户已完成过的同一素材。 */
@Mapper
public interface MaterialMapper {
    /** 技术素材：标题/难度/预估耗时都在 content_version，经 published_version_id 关联。 */
    @Select("SELECT lc.id, cv.id AS version_id, cv.title, cv.estimated_seconds FROM learning_content lc " +
            "JOIN content_version cv ON cv.id = lc.published_version_id " +
            "WHERE lc.content_type=#{type} AND lc.state='published' AND cv.difficulty=#{difficulty} " +
            "AND (#{type}='word' OR NOT EXISTS (SELECT 1 FROM learning_plan_topic lpt WHERE lpt.plan_id=#{planId}) " +
            "OR EXISTS (SELECT 1 FROM content_topic ct JOIN learning_plan_topic lpt ON lpt.topic_id=ct.topic_id " +
            "WHERE ct.content_version_id=cv.id AND lpt.plan_id=#{planId})) " +
            "AND NOT EXISTS (SELECT 1 FROM daily_task t WHERE t.owner_id=#{ownerId} AND t.content_id=lc.id AND t.status='DONE') " +
            "ORDER BY lc.published_at, lc.id LIMIT #{limit}")
    List<MaterialCandidate> selectContent(@Param("ownerId") String ownerId, @Param("planId") String planId,
                                          @Param("type") String type, @Param("difficulty") String difficulty,
                                          @Param("limit") int limit);

    /** 新词只能从用户当前选定的词书中产生，不使用全库兜底。计划难度优先，同书其它难度在不足时补齐（换到高难度词书也能排满）。 */
    @Select("SELECT lc.id,cv.id AS version_id,cv.title,cv.estimated_seconds FROM vocabulary_book_word vbw " +
            "JOIN learning_content lc ON lc.id=vbw.content_id " +
            "JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE vbw.book_id=#{bookId} AND lc.content_type='word' AND lc.state='published' " +
            "AND cv.review_status='approved' " +
            "AND NOT EXISTS (SELECT 1 FROM learning_record lr WHERE lr.owner_id=#{ownerId} AND lr.content_id=lc.id AND lr.learning_status IN ('understood','mastered')) " +
            "AND NOT EXISTS (SELECT 1 FROM daily_task t WHERE t.owner_id=#{ownerId} AND t.content_id=lc.id AND t.status='DONE') " +
            "ORDER BY CASE WHEN cv.difficulty=#{difficulty} THEN 0 ELSE 1 END,vbw.sort_no,vbw.importance DESC,lc.published_at,lc.id LIMIT #{limit}")
    List<MaterialCandidate> selectWordContent(@Param("ownerId") String ownerId,@Param("bookId") String bookId,
                                              @Param("difficulty") String difficulty,@Param("limit") int limit);

    /** 复习素材：V3.0 复习队列在 review_schedule（state=active 且 due_date 到期），标题取 knowledge_item。 */
    @Select("SELECT k.id, k.title, 30 AS estimated_seconds FROM review_schedule rs " +
            "JOIN knowledge_item k ON k.id = rs.knowledge_id " +
            "WHERE rs.owner_id=#{ownerId} AND rs.state='active' AND rs.due_date<=#{date} " +
            "AND NOT EXISTS (SELECT 1 FROM daily_task t WHERE t.owner_id=#{ownerId} AND t.knowledge_id=k.id AND t.status='DONE') " +
            "ORDER BY rs.due_date, k.id LIMIT #{limit}")
    List<MaterialCandidate> selectDueReviews(@Param("ownerId") String ownerId, @Param("date") LocalDate date, @Param("limit") int limit);

    /** 行动素材：V3.0 周行动，取本人已确认(confirmed)且未完成的，预估耗时 estimated_minutes 转秒。 */
    @Select("SELECT a.id, a.title, a.estimated_minutes*60 AS estimated_seconds FROM weekly_action a " +
            "WHERE a.owner_id=#{ownerId} AND a.state='confirmed' AND a.scheduled_date=#{date} " +
            "AND NOT EXISTS (SELECT 1 FROM daily_task t WHERE t.owner_id=#{ownerId} AND t.action_id=a.id AND t.status='DONE') " +
            "ORDER BY a.scheduled_date, a.action_no, a.id LIMIT #{limit}")
    List<MaterialCandidate> selectActions(@Param("ownerId") String ownerId, @Param("date") LocalDate date, @Param("limit") int limit);
}
