package com.zhixing.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.UserConsentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface UserConsentMapper extends BaseMapper<UserConsentEntity> {
    @Select("SELECT decision FROM user_consent WHERE owner_id=#{ownerId} AND purpose=#{purpose} ORDER BY occurred_at DESC,id DESC LIMIT 1")
    String latestDecision(@Param("ownerId") String ownerId, @Param("purpose") String purpose);
}
