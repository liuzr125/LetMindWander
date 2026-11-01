package com.zhixing.mapper;

import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TechnicalCollectionQuotaMapper {
  @Insert("INSERT INTO content_collection_day(schedule_id,business_date,target_count) VALUES(#{schedule},#{date},10) ON DUPLICATE KEY UPDATE schedule_id=VALUES(schedule_id)")
  int ensureDay(@Param("schedule") String schedule, @Param("date") LocalDate date);

  @Update("UPDATE content_collection_day SET lease_owner=#{run},lease_until=#{until},auto_attempts=auto_attempts+#{automatic},next_retry_at=NULL WHERE schedule_id=#{schedule} AND business_date=#{date} AND (lease_owner IS NULL OR lease_until<#{now}) AND (#{automatic}=0 OR auto_attempts<3)")
  int claim(@Param("schedule") String schedule, @Param("date") LocalDate date, @Param("run") String run, @Param("now") Instant now, @Param("until") Instant until, @Param("automatic") int automatic);

  @Update("UPDATE content_collection_day SET lease_until=#{until} WHERE schedule_id=#{schedule} AND business_date=#{date} AND lease_owner=#{run}")
  int heartbeat(@Param("schedule") String schedule, @Param("date") LocalDate date, @Param("run") String run, @Param("until") Instant until);

  @Select("SELECT lease_owner FROM content_collection_day WHERE schedule_id=#{schedule} AND business_date=#{date} FOR UPDATE")
  String lockOwner(@Param("schedule") String schedule, @Param("date") LocalDate date);

  @Update("UPDATE content_collection_day SET lease_owner=NULL,lease_until=NULL,next_retry_at=CASE WHEN auto_attempts<3 THEN #{retry} ELSE NULL END WHERE schedule_id=#{schedule} AND business_date=#{date} AND lease_owner=#{run}")
  int release(@Param("schedule") String schedule, @Param("date") LocalDate date, @Param("run") String run, @Param("retry") Instant retry);

  @Select("SELECT COUNT(*) FROM content_collection_day WHERE schedule_id=#{schedule} AND business_date=#{date} AND auto_attempts<3 AND ((lease_owner IS NULL AND next_retry_at<=#{now}) OR (lease_owner IS NOT NULL AND lease_until<#{now}))")
  int due(@Param("schedule") String schedule, @Param("date") LocalDate date, @Param("now") Instant now);

  @Select("SELECT COUNT(*) FROM learning_content lc JOIN content_version cv ON cv.id=lc.current_version_id AND cv.content_id=lc.id WHERE lc.content_type='tech' AND lc.source_id IN ('00000000000000000000000000000081','00000000000000000000000000000082','00000000000000000000000000000083','00000000000000000000000000000084','00000000000000000000000000000085') AND lc.created_at>=#{start} AND lc.created_at<#{end}")
  int countInserted(@Param("start") Instant start, @Param("end") Instant end);

  @Select("SELECT lc.source_id,cv.title,cv.summary,cv.origin_url FROM learning_content lc JOIN content_version cv ON cv.id=lc.current_version_id WHERE lc.content_type='tech' AND lc.source_id IN ('00000000000000000000000000000081','00000000000000000000000000000082','00000000000000000000000000000083','00000000000000000000000000000084') ORDER BY lc.created_at DESC,lc.id DESC LIMIT 80")
  List<Map<String,Object>> seeds();

  @Select("SELECT cv.title FROM learning_content lc JOIN content_version cv ON cv.id=lc.current_version_id WHERE lc.content_type='tech' ORDER BY lc.created_at DESC,lc.id DESC LIMIT 80")
  List<String> recentTitles();

  @Select("SELECT cv.body FROM learning_content lc JOIN content_version cv ON cv.id=lc.current_version_id WHERE lc.content_type='tech' AND lc.source_id='00000000000000000000000000000085' ORDER BY lc.created_at DESC,lc.id DESC LIMIT 40")
  List<String> recentOriginalBodies();

  @Select("SELECT COUNT(*) FROM content_version cv JOIN learning_content lc ON lc.id=cv.content_id WHERE lc.content_type='tech' AND (LOWER(TRIM(cv.title))=#{title} OR cv.body_hash=UNHEX(#{bodyHash}))")
  int countGeneratedDuplicate(@Param("title") String title, @Param("bodyHash") String bodyHash);

  @Insert("INSERT INTO content_source(id,name,source_type,license_note,enabled) SELECT '00000000000000000000000000000085','官方资料原创技术解读','ai_original','AI 根据官方 RSS 选题原创，非官方原文或新公告，需人工核验后发布。',1 WHERE NOT EXISTS (SELECT 1 FROM content_source WHERE id='00000000000000000000000000000085')")
  int ensureOriginalSource();

  @Select("SELECT p.provider_code,p.model_code,COUNT(*) call_count FROM ai_job j JOIN ai_attempt a ON a.job_id=j.id JOIN ai_model_price p ON p.id=a.price_id WHERE j.action_code='generate_tech_materials' AND j.source_type='content_collection' AND j.source_id=#{run} GROUP BY p.provider_code,p.model_code")
  List<Map<String,Object>> modelUsage(@Param("run") String run);
}
