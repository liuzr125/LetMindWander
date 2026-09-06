package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AppParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 参数读取只允许访问 active 项；SQL 不散落在业务服务中。 */
@Mapper
public interface AppParameterMapper extends BaseMapper<AppParameterEntity> {
    @Select("SELECT id, param_key, param_value FROM app_parameter WHERE param_key=#{key} AND state='active' LIMIT 1")
    AppParameterEntity selectActiveByKey(@Param("key") String key);
}
