package com.zhixing.model;

import java.time.Instant;

public class AdminUserView {
    private String userId,shortId,nickname,mobileMasked,status;
    private Integer seqNo;
    private Boolean aiConsented;
    private Instant lastLoginAt,createdAt;
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public String getShortId(){return shortId;} public void setShortId(String v){shortId=v;}
    public String getNickname(){return nickname;} public void setNickname(String v){nickname=v;}
    public String getMobileMasked(){return mobileMasked;} public void setMobileMasked(String v){mobileMasked=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getSeqNo(){return seqNo;} public void setSeqNo(Integer v){seqNo=v;}
    public Boolean getAiConsented(){return aiConsented;} public void setAiConsented(Boolean v){aiConsented=v;}
    public Instant getLastLoginAt(){return lastLoginAt;} public void setLastLoginAt(Instant v){lastLoginAt=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}
