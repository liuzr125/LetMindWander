package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.FollowRecordingPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface FollowRecordingPersistenceMapper
    extends BaseMapper<FollowRecordingPersistenceEntity> {
  @Select(
      "SELECT id,asset_id,duration_ms,updated_at FROM follow_recording WHERE owner_id=#{ownerId} AND content_id=#{contentId} AND state='active' LIMIT 1")
  List<Map<String, Object>> selectActiveRecording(
      @Param("ownerId") Object ownerId, @Param("contentId") Object contentId);

  @Select(
      "SELECT asset_id FROM follow_recording WHERE owner_id=#{ownerId} AND content_id=#{contentId} AND state='active'")
  List<String> selectActiveAssets(
      @Param("ownerId") Object ownerId, @Param("contentId") Object contentId);

  @Insert(
      "INSERT INTO follow_recording(id,owner_id,content_id,content_version_id,asset_id,duration_ms,state,created_at,updated_at) VALUES(#{id},#{ownerId},#{contentId},#{contentVersionId},#{assetId},#{durationMs},'active',#{createdAt},#{updatedAt}) ON DUPLICATE KEY UPDATE id=VALUES(id),content_version_id=VALUES(content_version_id),asset_id=VALUES(asset_id),duration_ms=VALUES(duration_ms),state='active',deleted_at=NULL,updated_at=VALUES(updated_at)")
  int upsertActiveRecording(
      @Param("id") Object id,
      @Param("ownerId") Object ownerId,
      @Param("contentId") Object contentId,
      @Param("contentVersionId") Object contentVersionId,
      @Param("assetId") Object assetId,
      @Param("durationMs") Object durationMs,
      @Param("createdAt") java.sql.Timestamp createdAt,
      @Param("updatedAt") java.sql.Timestamp updatedAt);

  @Update(
      "UPDATE follow_recording SET state='deleted',deleted_at=#{deletedAt},updated_at=#{updatedAt} WHERE owner_id=#{ownerId} AND content_id=#{contentId} AND state='active'")
  int markActiveRecordingDeleted(
      @Param("deletedAt") java.sql.Timestamp deletedAt,
      @Param("updatedAt") java.sql.Timestamp updatedAt,
      @Param("ownerId") Object ownerId,
      @Param("contentId") Object contentId);
}
