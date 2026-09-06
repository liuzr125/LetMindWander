package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.InviteCodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** SQL access for the authentication/admission workflow. Business rules stay in AuthService. */
@Mapper
public interface AuthMapper extends BaseMapper<InviteCodeEntity> {
    @Update("UPDATE auth_session SET revoked_at = #{now}, updated_at = #{now} "
            + "WHERE principal_type = 'user' AND principal_id = #{userId} AND revoked_at IS NULL")
    int revokeActiveUserSessions(@Param("userId") String userId, @Param("now") Timestamp now);

    @Select("SELECT status, expires_at FROM invite_code WHERE code_hash = #{hash}")
    List<Map<String, Object>> selectInviteByHash(@Param("hash") byte[] hash);

    @Select("SELECT invited_limit, invited_used FROM admission_counter WHERE scope_code = 'trial'")
    Map<String, Object> selectTrialAdmission();
}
