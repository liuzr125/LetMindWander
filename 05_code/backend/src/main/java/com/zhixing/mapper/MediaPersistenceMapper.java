package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.MediaPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface MediaPersistenceMapper extends BaseMapper<MediaPersistenceEntity> {
  @Insert(
      "INSERT INTO media_asset (id, owner_id, purpose, object_key, mime_type, byte_size, sha256, state, created_at, updated_at) VALUES (#{id}, #{ownerId}, 'avatar', #{objectKey}, #{actualMime}, #{byteslength}, #{sha256.bytes,jdbcType=BINARY}, 'ready', #{now}, #{now8})")
  int insertAvatarAsset(
      @Param("id") Object id,
      @Param("ownerId") Object ownerId,
      @Param("objectKey") Object objectKey,
      @Param("actualMime") Object actualMime,
      @Param("byteslength") Object byteslength,
      @Param("sha256") com.zhixing.mapper.type.BinaryHash sha256,
      @Param("now") Object now,
      @Param("now8") Object now8);

  @Insert(
      "INSERT INTO media_asset (id,owner_id,purpose,object_key,mime_type,byte_size,sha256,state,created_at,updated_at) VALUES (#{id},#{ownerId},'follow_recording',#{objectKey},'audio/mpeg',#{byteslength},#{sha256bytes.bytes,jdbcType=BINARY},'ready',#{now},#{now7})")
  int insertFollowRecordingAsset(
      @Param("id") Object id,
      @Param("ownerId") Object ownerId,
      @Param("objectKey") Object objectKey,
      @Param("byteslength") Object byteslength,
      @Param("sha256bytes") com.zhixing.mapper.type.BinaryHash sha256bytes,
      @Param("now") Object now,
      @Param("now7") Object now7);

  @Select("SELECT object_key, state, owner_id, purpose FROM media_asset WHERE id = #{id}")
  List<Map<String, Object>> selectAssetAccessDetails(@Param("id") Object id);

  @Select("SELECT COUNT(*) FROM app_user WHERE avatar_url = #{avatarUrl} AND status = 'active'")
  Integer countActiveAvatarReferences(@Param("avatarUrl") Object avatarUrl);

  @Insert(
      "INSERT INTO media_asset(id,owner_id,purpose,object_key,mime_type,byte_size,sha256,state,origin_url,license_note,created_at,updated_at) VALUES(#{id},NULL,#{purpose},#{objectKey},#{mimeType},#{byteSize},#{sha256.bytes,jdbcType=BINARY},'ready',#{originUrl},#{licenseNote},#{createdAt},#{updatedAt})")
  int insertPublicAsset(
      @Param("id") Object id,
      @Param("purpose") Object purpose,
      @Param("objectKey") Object objectKey,
      @Param("mimeType") Object mimeType,
      @Param("byteSize") Object byteSize,
      @Param("sha256") com.zhixing.mapper.type.BinaryHash sha256,
      @Param("originUrl") Object originUrl,
      @Param("licenseNote") Object licenseNote,
      @Param("createdAt") java.sql.Timestamp createdAt,
      @Param("updatedAt") java.sql.Timestamp updatedAt);

  @Select("SELECT id FROM media_asset WHERE object_key=#{objectKey} AND state='ready' LIMIT 1")
  List<String> selectReadyAssetIdsByObjectKey(@Param("objectKey") Object objectKey);

  @Select(
      "SELECT COUNT(*) FROM media_asset WHERE id = #{id} AND owner_id = #{ownerId} AND purpose = 'avatar' AND state = 'ready'")
  Integer countOwnedReadyAvatar(@Param("id") Object id, @Param("ownerId") Object ownerId);

  @Select(
      "SELECT object_key FROM media_asset WHERE id=#{id} AND owner_id=#{ownerId} AND purpose=#{purpose} AND state='ready'")
  List<Map<String, Object>> selectOwnedReadyAsset(
      @Param("id") Object id, @Param("ownerId") Object ownerId, @Param("purpose") Object purpose);

  @Update(
      "UPDATE media_asset SET state='deleted',updated_at=#{updatedAt} WHERE id=#{id} AND owner_id=#{ownerId} AND state='ready'")
  int markOwnedReadyAssetDeleted(
      @Param("updatedAt") java.sql.Timestamp updatedAt,
      @Param("id") Object id,
      @Param("ownerId") Object ownerId);
}
