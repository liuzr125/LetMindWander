package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AppUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** app_user 数据访问；SQL 集中在 Mapper 层，业务规则在 Service 层。 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUserEntity> {

    @Select("SELECT id, nickname, mobile, avatar_url, status, current_plan_id, short_id " +
            "FROM app_user WHERE wx_app_id = #{appId} AND wx_open_id = #{openId}")
    AppUserEntity selectByWechat(@Param("appId") String appId, @Param("openId") String openId);

    @Select("SELECT id,nickname,mobile,avatar_url,status,current_plan_id,short_id,real_name,english_name," +
            "birthday,gender,hobbies_json,introduction,profile_visibility,row_version,ai_consent_version,ai_consented_at " +
            "FROM app_user WHERE id = #{id}")
    AppUserEntity selectUserById(@Param("id") String id);

    @Select("SELECT id,nickname,mobile,avatar_url,status,current_plan_id,short_id,real_name,english_name," +
            "birthday,gender,hobbies_json,introduction,profile_visibility,row_version,ai_consent_version,ai_consented_at " +
            "FROM app_user WHERE id=#{id} FOR UPDATE")
    AppUserEntity selectProfileForUpdate(@Param("id") String id);

    @Update("UPDATE app_user SET nickname=#{nickname},real_name=#{realName},english_name=#{englishName},birthday=#{birthday}," +
            "gender=#{gender},hobbies_json=#{hobbiesJson},introduction=#{introduction},profile_visibility=#{profileVisibility}," +
            "avatar_url=#{avatarUrl},row_version=row_version+1,updated_at=CURRENT_TIMESTAMP " +
            "WHERE id=#{id} AND row_version=#{expectedVersion}")
    int updateProfile(@Param("id") String id,@Param("nickname") String nickname,@Param("realName") String realName,
                      @Param("englishName") String englishName,@Param("birthday") java.sql.Date birthday,
                      @Param("gender") Integer gender,@Param("hobbiesJson") String hobbiesJson,
                      @Param("introduction") String introduction,@Param("profileVisibility") String profileVisibility,
                      @Param("avatarUrl") String avatarUrl,@Param("expectedVersion") Integer expectedVersion);

    @Select("SELECT COUNT(*) FROM friend_relation WHERE user_low_id=#{lowId} AND user_high_id=#{highId} AND state='active'")
    int countActiveFriend(@Param("lowId") String lowId,@Param("highId") String highId);

    @Update("UPDATE app_user SET ai_consent_version=#{version},ai_consented_at=#{consentedAt},updated_at=CURRENT_TIMESTAMP WHERE id=#{userId}")
    int updateAiConsent(@Param("userId") String userId,@Param("version") String version,@Param("consentedAt") java.time.Instant consentedAt);

    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM app_user")
    Long selectMaxSeqNo();
}
