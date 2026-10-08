package com.zhixing.mapper;

import com.zhixing.mapper.type.BinaryHash;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {
  int insertUserSession(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("tokenHash") BinaryHash tokenHash,
      @Param("expiresAt") Timestamp expiresAt,
      @Param("lastSeenAt") Timestamp lastSeenAt);

  int updateUserLogin(
      @Param("lastLoginAt") Timestamp lastLoginAt,
      @Param("updatedAt") Timestamp updatedAt,
      @Param("userId") String userId);

  List<Map<String, Object>> selectUserSessionByTokenHash(@Param("tokenHash") BinaryHash tokenHash);

  int revokeUserSessions(
      @Param("revokedAt") Timestamp revokedAt,
      @Param("updatedAt") Timestamp updatedAt,
      @Param("userId") String userId);

  int touchSession(
      @Param("lastSeenAt") Timestamp lastSeenAt,
      @Param("updatedAt") Timestamp updatedAt,
      @Param("sessionId") String sessionId);

  int revokeSession(
      @Param("revokedAt") Timestamp revokedAt,
      @Param("updatedAt") Timestamp updatedAt,
      @Param("sessionId") String sessionId);
}
