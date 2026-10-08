package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AppParameterPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface AppParameterPersistenceMapper extends BaseMapper<AppParameterPersistenceEntity> {
  @Update(
      "UPDATE ai_daily_quota SET limit_count=#{limitCount},updated_at=CURRENT_TIMESTAMP WHERE quota_date=#{quotaDate} AND scope_key NOT IN (#{updatedAtValue3},#{sYSTEM_SCOPE})")
  int updatePersonalDailyLimits(
      @Param("limitCount") Object limitCount,
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("updatedAtValue3") Object updatedAtValue3,
      @Param("sYSTEM_SCOPE") Object sYSTEM_SCOPE);

  @Update(
      "UPDATE ai_daily_quota SET limit_count=#{limitCount},updated_at=CURRENT_TIMESTAMP WHERE quota_date=#{quotaDate} AND scope_key IN (#{updatedAtValue3},#{sYSTEM_SCOPE})")
  int updateSystemDailyLimits(
      @Param("limitCount") Object limitCount,
      @Param("quotaDate") java.time.LocalDate quotaDate,
      @Param("updatedAtValue3") Object updatedAtValue3,
      @Param("sYSTEM_SCOPE") Object sYSTEM_SCOPE);
}
