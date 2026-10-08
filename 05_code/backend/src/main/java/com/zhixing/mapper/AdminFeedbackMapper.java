package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminFeedbackMapper {
  int countFilteredFeedback(@Param("state") String state, @Param("keyword") String keyword);

  List<Map<String, Object>> selectFeedbackPage(
      @Param("state") String state,
      @Param("keyword") String keyword,
      @Param("offset") int offset,
      @Param("limit") int limit);

  List<Map<String, Object>> selectFeedbackById(@Param("id") String id);

  int updateFeedbackState(@Param("state") String state, @Param("id") String id);

  int countFeedbackByState(@Param("state") String state);

  int insertFeedbackAudit(
      @Param("id") String id,
      @Param("adminId") String adminId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetId") String targetId,
      @Param("metadata") String metadata,
      @Param("expiresAt") java.sql.Timestamp expiresAt);
}
