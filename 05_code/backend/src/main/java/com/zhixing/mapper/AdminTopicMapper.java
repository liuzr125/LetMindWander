package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminTopicMapper {
  List<Map<String, Object>> selectSystemTopics();

  String selectTopicType(@Param("id") String id);

  int countTechnicalReferences(@Param("id") String id);

  int countPlanReferences(@Param("id") String id);

  int countDuplicateNames(
      @Param("name") String name,
      @Param("normalizedName") String normalizedName,
      @Param("excludedId") String excludedId);

  int insertSystemTopic(
      @Param("id") String id,
      @Param("name") String name,
      @Param("normalizedName") String normalizedName,
      @Param("type") String type);

  int updateSystemTopic(
      @Param("name") String name, @Param("type") String type, @Param("id") String id);

  int countPublishedTechnicalReferences(@Param("id") String id);

  int countPlanReferencesForDeletion(@Param("id") String id);

  int softDeleteSystemTopic(@Param("id") String id);

  List<Map<String, Object>> selectSystemTopicForUpdate(@Param("id") String id);

  int insertTopicAudit(
      @Param("id") String id,
      @Param("actor") String actor,
      @Param("action") String action,
      @Param("topicId") String topicId,
      @Param("expiresAt") java.sql.Timestamp expiresAt);
}
