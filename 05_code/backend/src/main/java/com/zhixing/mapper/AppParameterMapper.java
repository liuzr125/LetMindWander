package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AppParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 参数读取只允许访问 active 项；SQL 不散落在业务服务中。 */
@Mapper
public interface AppParameterMapper extends BaseMapper<AppParameterEntity> {
    @Select("SELECT id, param_key, param_value, is_secret, description, state, del_is, version_no FROM app_parameter WHERE param_key=#{key} AND state='active' AND del_is=0 LIMIT 1")
    AppParameterEntity selectActiveByKey(@Param("key") String key);

    @Select("SELECT id,param_key,param_value,is_secret,description,state,del_is,version_no FROM app_parameter WHERE state='active' AND del_is=0 AND param_key LIKE CONCAT(#{prefix},'%') ORDER BY param_key")
    List<AppParameterEntity> selectActiveByPrefix(@Param("prefix") String prefix);

    @Select("SELECT id,param_key,param_value,is_secret,description,state,del_is,version_no FROM app_parameter WHERE param_key=#{key} LIMIT 1")
    AppParameterEntity selectByKeyIncludingDeleted(@Param("key") String key);

    @Select("SELECT id,param_key,param_value,is_secret,description,state,del_is,version_no FROM app_parameter WHERE del_is=0 AND (#{keyword}='' OR param_key LIKE CONCAT('%',#{keyword},'%') OR description LIKE CONCAT('%',#{keyword},'%')) ORDER BY param_key")
    List<AppParameterEntity> selectVisibleForAdmin(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM app_parameter WHERE del_is=0 AND (#{keyword}='' OR param_key LIKE CONCAT('%',#{keyword},'%') OR description LIKE CONCAT('%',#{keyword},'%'))")
    int countVisibleForAdmin(@Param("keyword") String keyword);

    @Select("SELECT id,param_key,param_value,is_secret,description,state,del_is,version_no FROM app_parameter WHERE del_is=0 AND (#{keyword}='' OR param_key LIKE CONCAT('%',#{keyword},'%') OR description LIKE CONCAT('%',#{keyword},'%')) ORDER BY param_key LIMIT #{offset},#{limit}")
    List<AppParameterEntity> selectVisiblePageForAdmin(@Param("keyword") String keyword,@Param("offset") int offset,@Param("limit") int limit);
}
