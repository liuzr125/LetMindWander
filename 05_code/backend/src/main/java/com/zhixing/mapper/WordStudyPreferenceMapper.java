package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordStudyPreferenceMapper {
  @Select(
      "SELECT default_answer_mode,auto_play_enabled,auto_play_accent,auto_play_count,auto_play_interval_ms,row_version FROM user_word_study_preference WHERE owner_id=#{owner} AND del_is=0")
  @Results({
    @Result(column = "default_answer_mode", property = "default_answer_mode"),
    @Result(column = "auto_play_enabled", property = "auto_play_enabled"),
    @Result(column = "auto_play_accent", property = "auto_play_accent"),
    @Result(column = "auto_play_count", property = "auto_play_count"),
    @Result(column = "auto_play_interval_ms", property = "auto_play_interval_ms"),
    @Result(column = "row_version", property = "row_version")
  })
  List<Map<String, Object>> selectActive(@Param("owner") String owner);

  @Update(
      "UPDATE user_word_study_preference SET default_answer_mode=#{mode},auto_play_enabled=#{enabled},auto_play_accent=#{accent},auto_play_count=#{count},auto_play_interval_ms=#{interval},row_version=row_version+1,del_is=0,updated_at=CURRENT_TIMESTAMP WHERE owner_id=#{owner}")
  int updatePreferences(
      @Param("owner") String owner,
      @Param("mode") String mode,
      @Param("enabled") int enabled,
      @Param("accent") String accent,
      @Param("count") int count,
      @Param("interval") int interval);

  @Insert(
      "INSERT INTO user_word_study_preference(owner_id,default_answer_mode,auto_play_enabled,auto_play_accent,auto_play_count,auto_play_interval_ms,row_version,del_is) VALUES(#{owner},#{mode},#{enabled},#{accent},#{count},#{interval},1,0)")
  int insertPreferences(
      @Param("owner") String owner,
      @Param("mode") String mode,
      @Param("enabled") int enabled,
      @Param("accent") String accent,
      @Param("count") int count,
      @Param("interval") int interval);
}
