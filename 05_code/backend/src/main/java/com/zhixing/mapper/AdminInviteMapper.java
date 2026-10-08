package com.zhixing.mapper;

import com.zhixing.mapper.type.BinaryHash;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminInviteMapper {
  int insertInvite(
      @Param("id") String id,
      @Param("hash") BinaryHash hash,
      @Param("expiresAt") java.sql.Timestamp expiresAt,
      @Param("creator") String creator,
      @Param("createdAt") java.sql.Timestamp createdAt,
      @Param("updatedAt") java.sql.Timestamp updatedAt);

  int revokeAvailableInvite(
      @Param("updatedAt") java.sql.Timestamp updatedAt,
      @Param("id") String id,
      @Param("now") java.sql.Timestamp now);

  int deleteUnredeemedInvite(@Param("id") String id);

  Map<String, Object> selectTrialAdmission();

  Map<String, Object> selectTrialAdmissionForUpdate();

  int updateTrialAdmissionLimit(
      @Param("limit") int limit, @Param("updatedAt") java.sql.Timestamp updatedAt);

  int countInviteHash(@Param("hash") BinaryHash hash);

  Long countInvites(@Param("status") String status);

  List<Map<String, Object>> selectInvitePage(
      @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

  long countAvailableInvites();

  long countRedeemedInvites();

  long countRevokedInvites();

  long countExpiredInvites();
}
