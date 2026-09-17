package com.zhixing.mapper;

import com.zhixing.entity.ReviewFeedbackEntity;
import com.zhixing.entity.ReviewScheduleEntity;
import com.zhixing.model.ReviewItemView;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReviewMapper {
    @Select("SELECT rs.id AS schedule_id,k.id AS knowledge_id,t.id AS task_id,t.version_no AS task_version,k.item_type,k.title," +
            "COALESCE(NULLIF(k.body,''),cv.summary,cv.body,'') AS body,cv.word_term,cv.meaning,cv.example_text,rs.stage " +
            "FROM review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id AND k.owner_id=rs.owner_id " +
            "LEFT JOIN daily_package p ON p.owner_id=rs.owner_id AND p.business_date=#{date} " +
            "LEFT JOIN daily_task t ON t.package_id=p.id AND t.knowledge_id=k.id AND t.task_type='review' AND t.status NOT IN ('DONE','CANCELLED') " +
            "LEFT JOIN learning_content lc ON lc.id=k.source_content_id LEFT JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE rs.owner_id=#{ownerId} AND rs.state='active' AND rs.due_date<=#{date} " +
            "ORDER BY rs.due_date,COALESCE(t.sort_no,32767),rs.id")
    List<ReviewItemView> selectQueue(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Select("SELECT * FROM review_schedule WHERE id=#{id} AND owner_id=#{ownerId} FOR UPDATE")
    ReviewScheduleEntity selectScheduleForUpdate(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT * FROM review_feedback WHERE owner_id=#{ownerId} AND knowledge_id=#{knowledgeId} AND business_date=#{date} FOR UPDATE")
    ReviewFeedbackEntity selectFeedbackForUpdate(@Param("ownerId") String ownerId,@Param("knowledgeId") String knowledgeId,@Param("date") LocalDate date);

    @Insert("INSERT INTO review_feedback (id,owner_id,knowledge_id,schedule_id,business_date,feedback,before_stage,before_state,before_due_date," +
            "after_stage,after_state,after_due_date,revision_no,task_id,effective_at) VALUES (#{id},#{ownerId},#{knowledgeId},#{scheduleId},#{businessDate}," +
            "#{feedback},#{beforeStage},#{beforeState},#{beforeDueDate},#{afterStage},#{afterState},#{afterDueDate},#{revisionNo},#{taskId},#{effectiveAt})")
    int insertFeedback(ReviewFeedbackEntity feedback);

    @Update("UPDATE review_feedback SET feedback=#{feedback},after_stage=#{afterStage},after_state=#{afterState},after_due_date=#{afterDueDate}," +
            "revision_no=#{revisionNo},task_id=#{taskId},effective_at=#{effectiveAt} WHERE id=#{id}")
    int updateFeedback(ReviewFeedbackEntity feedback);

    @Update("UPDATE review_schedule SET state=#{state},stage=#{stage},due_date=#{dueDate},last_feedback_id=#{feedbackId}," +
            "version_no=version_no+1,updated_at=#{now} WHERE id=#{id} AND owner_id=#{ownerId}")
    int updateSchedule(@Param("id") String id,@Param("ownerId") String ownerId,@Param("state") String state,
                       @Param("stage") Integer stage,@Param("dueDate") LocalDate dueDate,
                       @Param("feedbackId") String feedbackId,@Param("now") Instant now);

    @Update("UPDATE knowledge_item SET learning_status='learning',version_no=version_no+1 WHERE id=#{knowledgeId} AND owner_id=#{ownerId}")
    int markLearning(@Param("ownerId") String ownerId,@Param("knowledgeId") String knowledgeId);
}
