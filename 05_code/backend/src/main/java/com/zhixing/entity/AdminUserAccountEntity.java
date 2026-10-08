package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("app_user")
public class AdminUserAccountEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private Integer seqNo;
  private String shortId;
  private String nickname;
  private String mobile;
  private String status;
  private String aiConsentVersion;
  private Instant lastLoginAt;
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  public Integer getSeqNo() {
    return seqNo;
  }

  public void setSeqNo(Integer v) {
    seqNo = v;
  }

  public String getShortId() {
    return shortId;
  }

  public void setShortId(String v) {
    shortId = v;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String v) {
    nickname = v;
  }

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String v) {
    mobile = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getAiConsentVersion() {
    return aiConsentVersion;
  }

  public void setAiConsentVersion(String v) {
    aiConsentVersion = v;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant v) {
    lastLoginAt = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant v) {
    createdAt = v;
  }
}
