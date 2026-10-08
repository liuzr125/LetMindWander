package com.zhixing.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CollectionScheduleSettingsMapper {
  int updateCron(@Param("cron") String cron, @Param("nextRunAt") Instant nextRunAt);
}
