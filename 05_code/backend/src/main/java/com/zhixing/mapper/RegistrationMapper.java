package com.zhixing.mapper;

import com.zhixing.mapper.type.BinaryHash;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RegistrationMapper {
  List<Map<String, Object>> lockTrialAdmission();

  List<Map<String, Object>> lockInviteByCodeHash(@Param("codeHash") BinaryHash codeHash);

  int insertRegisteredUser(
      @Param("id") String id,
      @Param("appId") String appId,
      @Param("openId") String openId,
      @Param("nickname") String nickname,
      @Param("mobile") String mobile,
      @Param("avatarUrl") String avatarUrl,
      @Param("planId") String planId,
      @Param("seqNo") long seqNo,
      @Param("shortId") String shortId,
      @Param("lastLoginAt") Timestamp lastLoginAt,
      @Param("mobileBoundAt") Timestamp mobileBoundAt,
      @Param("createdAt") Timestamp createdAt,
      @Param("updatedAt") Timestamp updatedAt);

  int insertInitialPlan(
      @Param("id") String id,
      @Param("ownerId") String ownerId,
      @Param("effectiveDate") java.sql.Date effectiveDate,
      @Param("createdAt") Timestamp createdAt);

  int redeemInvite(
      @Param("userId") String userId,
      @Param("redeemedAt") Timestamp redeemedAt,
      @Param("updatedAt") Timestamp updatedAt,
      @Param("inviteId") String inviteId);

  int occupyAdmission(
      @Param("updatedAt") Timestamp updatedAt, @Param("counterId") String counterId);

  int insertConsent(
      @Param("id") String id,
      @Param("ownerId") String ownerId,
      @Param("purpose") String purpose,
      @Param("version") String version,
      @Param("decision") String decision,
      @Param("occurredAt") Timestamp occurredAt,
      @Param("createdAt") Timestamp createdAt);
}
