package com.zhixing.model;
import java.time.Instant;
public class FriendRequestView {
 private String id,direction,state,remark,userId,shortId,nickname,avatarUrl; private Integer versionNo; private Instant createdAt,decidedAt;
 public String getId(){return id;} public void setId(String v){id=v;} public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
 public String getState(){return state;} public void setState(String v){state=v;} public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
 public String getUserId(){return userId;} public void setUserId(String v){userId=v;} public String getShortId(){return shortId;} public void setShortId(String v){shortId=v;}
 public String getNickname(){return nickname;} public void setNickname(String v){nickname=v;} public String getAvatarUrl(){return avatarUrl;} public void setAvatarUrl(String v){avatarUrl=v;}
 public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
 public Instant getDecidedAt(){return decidedAt;} public void setDecidedAt(Instant v){decidedAt=v;}
}
