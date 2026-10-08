package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.model.WordStudyModeEntity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordStudyModeMapper extends BaseMapper<WordStudyModeEntity> {
  @Insert(
      "INSERT INTO word_study_mode(setting_id,interval_mode) VALUES(1,'fixed') ON DUPLICATE KEY UPDATE setting_id=1")
  int initializeGlobalMode();

  @Select("SELECT interval_mode FROM word_study_mode WHERE setting_id=1 FOR UPDATE")
  String lockGlobalMode();

  @Update("UPDATE word_study_mode SET interval_mode=#{mode} WHERE setting_id=1")
  int updateGlobalMode(@Param("mode") String mode);
}
