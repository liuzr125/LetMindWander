package com.zhixing.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AppUserEntity;
import com.zhixing.model.FavoriteView;
import com.zhixing.model.MineOverviewView;
import com.zhixing.model.PrivacyView;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MineMapper extends BaseMapper<AppUserEntity> {
 @Select("SELECT COALESCE(lp.daily_budget_min,10) planMinutes,COALESCE(lp.weekdays_mask,31) weekdaysMask,"+
   "COALESCE(lp.difficulty,'intro') planDifficulty,COALESCE((SELECT q.used_count FROM ai_daily_quota q WHERE q.scope_key=#{ownerId} AND q.quota_date=#{today}),0) aiUsed,"+
   "COALESCE((SELECT q.limit_count FROM ai_daily_quota q WHERE q.scope_key=#{ownerId} AND q.quota_date=#{today}),10) aiLimit,"+
   "(SELECT COUNT(*) FROM friend_request r WHERE r.receiver_id=#{ownerId} AND r.state='pending') pendingFriendRequests,"+
   "(SELECT COUNT(*) FROM resource_schedule s WHERE s.owner_id=#{ownerId} AND s.state='active') activeSchedules,"+
   "(SELECT COALESCE(SUM(rr.result_count),0) FROM resource_run rr WHERE rr.owner_id=#{ownerId} AND rr.created_at>=#{dayStart}) todayUpdates " +
   "FROM app_user u LEFT JOIN learning_plan lp ON lp.id=u.current_plan_id WHERE u.id=#{ownerId}")
 MineOverviewView selectOverview(@Param("ownerId")String ownerId,@Param("today")LocalDate today,@Param("dayStart")Instant dayStart);

 @Select({"<script>","SELECT f.target_type targetType,f.target_id targetId,f.title_snapshot title,cv.summary summary,f.favorited_at favoritedAt ",
   "FROM user_favorite f LEFT JOIN learning_content lc ON lc.id=f.target_id LEFT JOIN content_version cv ON cv.id=lc.published_version_id ",
   "WHERE f.owner_id=#{ownerId} AND f.state='active' ","<if test=\"type != null and type != '' and type != 'all'\">AND f.target_type=#{type} </if>",
   "<if test=\"query != null and query != ''\">AND LOWER(f.title_snapshot) LIKE CONCAT('%',LOWER(#{query}),'%') </if>",
   "ORDER BY f.favorited_at DESC,f.id DESC LIMIT 100","</script>"})
 List<FavoriteView> selectFavorites(@Param("ownerId")String ownerId,@Param("type")String type,@Param("query")String query);

 @Select("SELECT CASE WHEN u.ai_consent_version IS NULL THEN FALSE ELSE TRUE END aiConsent,"+
   "COALESCE((SELECT CASE WHEN c.decision='grant' THEN TRUE ELSE FALSE END FROM user_consent c WHERE c.owner_id=#{ownerId} AND c.purpose='follow_recording_upload' ORDER BY c.occurred_at DESC,c.id DESC LIMIT 1),FALSE) followRecordingConsent,"+
   "(SELECT e.id FROM data_export e WHERE e.owner_id=#{ownerId} ORDER BY e.created_at DESC LIMIT 1) exportId,"+
   "(SELECT e.state FROM data_export e WHERE e.owner_id=#{ownerId} ORDER BY e.created_at DESC LIMIT 1) exportState,"+
   "(SELECT e.expires_at FROM data_export e WHERE e.owner_id=#{ownerId} ORDER BY e.created_at DESC LIMIT 1) exportExpiresAt,"+
   "(SELECT d.id FROM data_deletion d WHERE d.owner_id=#{ownerId} AND d.scope_type='account' ORDER BY d.created_at DESC LIMIT 1) deletionId,"+
   "(SELECT d.state FROM data_deletion d WHERE d.owner_id=#{ownerId} AND d.scope_type='account' ORDER BY d.created_at DESC LIMIT 1) deletionState FROM app_user u WHERE u.id=#{ownerId}")
 PrivacyView selectPrivacy(@Param("ownerId")String ownerId);

 @Select("SELECT COUNT(*) FROM data_export WHERE owner_id=#{ownerId} AND state IN ('queued','running','ready') AND (expires_at IS NULL OR expires_at>#{now})") int countActiveExports(@Param("ownerId")String ownerId,@Param("now")Instant now);
 @Insert("INSERT INTO data_export(id,owner_id,state,created_at,updated_at) VALUES(#{id},#{ownerId},'queued',#{now},#{now})") int insertExport(@Param("id")String id,@Param("ownerId")String ownerId,@Param("now")Instant now);
 @Select("SELECT COUNT(*) FROM data_deletion WHERE owner_id=#{ownerId} AND scope_type='account' AND state IN ('requested','blocked','cleaning','waiting_backup')") int countActiveDeletions(@Param("ownerId")String ownerId);
 @Insert("INSERT INTO data_deletion(id,owner_id,scope_type,receipt_hash,state,primary_deadline_at,backup_clear_after,receipt_expires_at,created_at,updated_at) VALUES(#{id},#{ownerId},'account',#{receiptHash},'requested',#{primaryDeadline},#{backupAfter},#{receiptExpires},#{now},#{now})") int insertDeletion(@Param("id")String id,@Param("ownerId")String ownerId,@Param("receiptHash")byte[] receiptHash,@Param("primaryDeadline")Instant primaryDeadline,@Param("backupAfter")Instant backupAfter,@Param("receiptExpires")Instant receiptExpires,@Param("now")Instant now);
 @Insert("INSERT INTO user_feedback(id,owner_id,category,content_id,body,state,request_id,created_at,updated_at) VALUES(#{id},#{ownerId},#{category},#{contentId},#{body},'open',#{requestId},#{now},#{now})") int insertFeedback(@Param("id")String id,@Param("ownerId")String ownerId,@Param("category")String category,@Param("contentId")String contentId,@Param("body")String body,@Param("requestId")String requestId,@Param("now")Instant now);
}
