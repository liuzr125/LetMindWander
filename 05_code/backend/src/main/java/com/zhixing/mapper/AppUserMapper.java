package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AppUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** app_user 数据访问；SQL 集中在 Mapper 层，业务规则在 Service 层。 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUserEntity> {

    @Select("SELECT id, nickname, mobile, avatar_url, status, current_plan_id, short_id " +
            "FROM app_user WHERE wx_app_id = #{appId} AND wx_open_id = #{openId}")
    AppUserEntity selectByWechat(@Param("appId") String appId, @Param("openId") String openId);

    @Select("SELECT id, nickname, mobile, avatar_url, status, current_plan_id, short_id " +
            "FROM app_user WHERE id = #{id}")
    AppUserEntity selectUserById(@Param("id") String id);

    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM app_user")
    Long selectMaxSeqNo();
}
