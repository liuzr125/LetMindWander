package com.zhixing.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Shared persistence boundary for administrator actions; caller supplies business semantics. */
@Mapper
public interface AdminAuditMapper {
  int insertAudit(
      @Param("id") String id,
      @Param("adminId") String adminId,
      @Param("actionCode") String actionCode,
      @Param("targetType") String targetType,
      @Param("targetId") String targetId,
      @Param("resultCode") String resultCode,
      @Param("metadataJson") String metadataJson,
      @Param("expiresAt") Instant expiresAt);
}
