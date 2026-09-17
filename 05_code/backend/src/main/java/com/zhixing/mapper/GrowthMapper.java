package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.LearningPlanEntity;
import com.zhixing.entity.WeeklyActionEntity;
import com.zhixing.entity.WeeklyRevisionEntity;
import com.zhixing.entity.WeeklySummaryEntity;
import com.zhixing.model.ActionView;
import com.zhixing.model.GrowthMetricRow;
import com.zhixing.model.JournalHighlightView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 成长模块的查询和持久化全部集中在 Mapper；Service 只负责周口径、状态机和事务。 */
@Mapper
public interface GrowthMapper extends BaseMapper<WeeklySummaryEntity> {
    @Select("SELECT p.business_date,COUNT(t.id) AS planned_count," +
            "COALESCE(SUM(CASE WHEN t.status='DONE' THEN 1 ELSE 0 END),0) AS done_count " +
            "FROM daily_package p LEFT JOIN daily_task t ON t.package_id=p.id AND t.status<>'CANCELLED' " +
            "WHERE p.owner_id=#{ownerId} AND p.business_date BETWEEN #{start} AND #{end} " +
            "GROUP BY p.business_date ORDER BY p.business_date")
    List<GrowthMetricRow> selectDailyMetrics(@Param("ownerId") String ownerId,@Param("start") LocalDate start,@Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM (" +
            "SELECT p.business_date AS growth_date FROM daily_task t JOIN daily_package p ON p.id=t.package_id " +
            "WHERE t.owner_id=#{ownerId} AND t.status='DONE' GROUP BY p.business_date UNION " +
            "SELECT business_date FROM daily_journal WHERE owner_id=#{ownerId} AND state='submitted' UNION " +
            "SELECT business_date FROM review_feedback WHERE owner_id=#{ownerId}) growth_days")
    int selectAccumulatedGrowthDays(@Param("ownerId") String ownerId);

    @Select("SELECT COUNT(*) FROM daily_task t JOIN daily_package p ON p.id=t.package_id " +
            "WHERE t.owner_id=#{ownerId} AND t.status='DONE' AND p.business_date BETWEEN #{start} AND #{end}")
    int selectCompletedCount(@Param("ownerId") String ownerId,@Param("start") LocalDate start,@Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM daily_task t JOIN daily_package p ON p.id=t.package_id " +
            "WHERE t.owner_id=#{ownerId} AND t.status='DONE' AND t.task_type IN ('tech','word') " +
            "AND p.business_date BETWEEN #{start} AND #{end}")
    int selectLearningCount(@Param("ownerId") String ownerId,@Param("start") LocalDate start,@Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM review_feedback WHERE owner_id=#{ownerId} AND business_date BETWEEN #{start} AND #{end}")
    int selectReviewFeedbackCount(@Param("ownerId") String ownerId,@Param("start") LocalDate start,@Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM daily_journal WHERE owner_id=#{ownerId} AND state='submitted' AND business_date BETWEEN #{start} AND #{end}")
    int selectJournalDayCount(@Param("ownerId") String ownerId,@Param("start") LocalDate start,@Param("end") LocalDate end);

    @Select("SELECT COUNT(*) FROM review_schedule WHERE owner_id=#{ownerId} AND state='active' AND due_date<=#{date}")
    int selectDueReviewCount(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM review_schedule WHERE owner_id=#{ownerId} AND state='active' AND due_date<#{date}")
    int selectReviewBacklogCount(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Select("SELECT dj.id AS journal_id,dj.business_date,jr.revision_no,jr.learned_text,jr.blocker_text " +
            "FROM daily_journal dj JOIN journal_revision jr ON jr.id=COALESCE(dj.submitted_revision_id,dj.current_revision_id) " +
            "WHERE dj.owner_id=#{ownerId} AND dj.business_date BETWEEN #{start} AND #{end} " +
            "ORDER BY dj.business_date DESC")
    List<JournalHighlightView> selectJournalHighlights(@Param("ownerId") String ownerId,@Param("start") LocalDate start,@Param("end") LocalDate end);

    @Select("SELECT * FROM weekly_summary WHERE owner_id=#{ownerId} AND week_start=#{weekStart}")
    WeeklySummaryEntity selectSummary(@Param("ownerId") String ownerId,@Param("weekStart") LocalDate weekStart);

    @Select("SELECT * FROM weekly_summary WHERE owner_id=#{ownerId} AND week_start=#{weekStart} FOR UPDATE")
    WeeklySummaryEntity selectSummaryForUpdate(@Param("ownerId") String ownerId,@Param("weekStart") LocalDate weekStart);

    @Select("SELECT * FROM weekly_revision WHERE id=#{id} AND owner_id=#{ownerId}")
    WeeklyRevisionEntity selectRevision(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT COALESCE(MAX(revision_no),0) FROM weekly_revision WHERE summary_id=#{summaryId}")
    int selectMaxRevision(@Param("summaryId") String summaryId);

    @Insert("INSERT INTO weekly_revision (id,owner_id,summary_id,revision_no,metrics_json,body,sources_json,source_fingerprint,ai_job_id,confirmed_at,invalidated_at) " +
            "VALUES (#{id},#{ownerId},#{summaryId},#{revisionNo},#{metricsJson},#{body},#{sourcesJson},#{sourceFingerprint},#{aiJobId},#{confirmedAt},#{invalidatedAt})")
    int insertRevision(WeeklyRevisionEntity revision);

    @Update("UPDATE weekly_revision SET confirmed_at=#{now},updated_at=#{now} WHERE id=#{revisionId} AND owner_id=#{ownerId} AND invalidated_at IS NULL")
    int confirmRevision(@Param("ownerId") String ownerId,@Param("revisionId") String revisionId,@Param("now") Instant now);

    @Update("UPDATE weekly_summary SET current_revision_id=#{revisionId},confirmed_revision_id=#{revisionId},is_stale=0," +
            "version_no=version_no+1,updated_at=#{now} WHERE id=#{id} AND owner_id=#{ownerId} " +
            "AND current_revision_id=#{revisionId} AND version_no=#{expectedVersion}")
    int confirmSummary(@Param("ownerId") String ownerId,@Param("id") String id,@Param("revisionId") String revisionId,
                       @Param("expectedVersion") Integer expectedVersion,@Param("now") Instant now);

    @Update("UPDATE weekly_summary SET is_stale=1,updated_at=#{now} WHERE id=#{id} AND owner_id=#{ownerId} AND is_stale=0")
    int markSummaryStale(@Param("ownerId") String ownerId,@Param("id") String id,@Param("now") Instant now);

    @Select("SELECT id,summary_id,action_no,title,note,scheduled_date,estimated_minutes,state,confirmed_at,version_no " +
            "FROM weekly_action WHERE owner_id=#{ownerId} AND summary_id=#{summaryId} AND state<>'cancelled' ORDER BY action_no")
    List<ActionView> selectActions(@Param("ownerId") String ownerId,@Param("summaryId") String summaryId);

    @Select("SELECT * FROM weekly_action WHERE owner_id=#{ownerId} AND summary_id=#{summaryId} ORDER BY action_no FOR UPDATE")
    List<WeeklyActionEntity> selectAllActionsForUpdate(@Param("ownerId") String ownerId,@Param("summaryId") String summaryId);

    @Select("SELECT * FROM weekly_action WHERE id=#{id} AND owner_id=#{ownerId} FOR UPDATE")
    WeeklyActionEntity selectActionForUpdate(@Param("ownerId") String ownerId,@Param("id") String id);

    @Insert("INSERT INTO weekly_action (id,owner_id,summary_id,action_no,title,note,scheduled_date,estimated_minutes,state,confirmed_at,version_no) " +
            "VALUES (#{id},#{ownerId},#{summaryId},#{actionNo},#{title},#{note},#{scheduledDate},#{estimatedMinutes},#{state},#{confirmedAt},#{versionNo})")
    int insertAction(WeeklyActionEntity action);

    @Update("UPDATE weekly_action SET title=#{title},note=#{note},scheduled_date=#{scheduledDate},estimated_minutes=#{estimatedMinutes}," +
            "state='draft',confirmed_at=NULL,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP(3) " +
            "WHERE id=#{id} AND owner_id=#{ownerId} AND summary_id=#{summaryId} AND state IN ('draft','cancelled') AND version_no=#{versionNo}")
    int saveDraftAction(WeeklyActionEntity action);

    @Update("UPDATE weekly_action SET state='cancelled',version_no=version_no+1,updated_at=CURRENT_TIMESTAMP(3) " +
            "WHERE id=#{id} AND owner_id=#{ownerId} AND state='draft' AND version_no=#{versionNo}")
    int cancelDraftAction(@Param("ownerId") String ownerId,@Param("id") String id,@Param("versionNo") Integer versionNo);

    @Update("UPDATE weekly_action SET state='confirmed',confirmed_at=#{now},version_no=version_no+1,updated_at=#{now} " +
            "WHERE id=#{id} AND owner_id=#{ownerId} AND state='draft' AND version_no=#{versionNo}")
    int confirmAction(@Param("ownerId") String ownerId,@Param("id") String id,@Param("versionNo") Integer versionNo,@Param("now") Instant now);

    @Select("SELECT * FROM learning_plan WHERE owner_id=#{ownerId} AND effective_date<=#{date} ORDER BY effective_date DESC,version_no DESC LIMIT 1")
    LearningPlanEntity selectEffectivePlan(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Select("SELECT COALESCE(SUM(estimated_minutes),0) FROM weekly_action WHERE owner_id=#{ownerId} AND scheduled_date=#{date} " +
            "AND state='confirmed' AND summary_id<>#{summaryId}")
    int selectOtherConfirmedMinutes(@Param("ownerId") String ownerId,@Param("date") LocalDate date,@Param("summaryId") String summaryId);

    @Select("SELECT id FROM daily_package WHERE owner_id=#{ownerId} AND business_date=#{date}")
    String selectPackageId(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM daily_task WHERE owner_id=#{ownerId} AND action_id=#{actionId} AND status<>'CANCELLED'")
    int selectActionTaskCount(@Param("ownerId") String ownerId,@Param("actionId") String actionId);

    @Select("SELECT COALESCE(MAX(sort_no),0) FROM daily_task WHERE package_id=#{packageId}")
    int selectMaxTaskSort(@Param("packageId") String packageId);

    @Select("SELECT budget_seconds-COALESCE(SUM(CASE WHEN t.status<>'CANCELLED' THEN t.estimated_seconds ELSE 0 END),0) " +
            "FROM daily_package p LEFT JOIN daily_task t ON t.package_id=p.id WHERE p.id=#{packageId} GROUP BY p.budget_seconds")
    int selectPackageRemainingSeconds(@Param("packageId") String packageId);

    @Insert("INSERT INTO daily_task (id,owner_id,package_id,task_type,title_snapshot,target_key,action_id,status,estimated_seconds,sort_no,version_no) " +
            "VALUES (#{id},#{ownerId},#{packageId},'action',#{title},#{targetKey},#{actionId},'TODO',#{estimatedSeconds},#{sortNo},1)")
    int insertActionTask(@Param("id") String id,@Param("ownerId") String ownerId,@Param("packageId") String packageId,
                         @Param("title") String title,@Param("targetKey") String targetKey,@Param("actionId") String actionId,
                         @Param("estimatedSeconds") Integer estimatedSeconds,@Param("sortNo") Integer sortNo);

    @Update("UPDATE daily_package SET current_count=(SELECT COUNT(*) FROM daily_task WHERE package_id=#{packageId} AND status<>'CANCELLED')," +
            "updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{packageId}")
    int refreshPackageCount(@Param("packageId") String packageId);
}
