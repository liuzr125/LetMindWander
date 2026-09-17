package com.zhixing.entity;
import java.time.Instant;
public class FriendRequestEntity {
 private String id,relationId,senderId,receiverId,remark,state; private Integer versionNo; private Instant createdAt,decidedAt;
 public String getId(){return id;} public void setId(String v){id=v;} public String getRelationId(){return relationId;} public void setRelationId(String v){relationId=v;}
 public String getSenderId(){return senderId;} public void setSenderId(String v){senderId=v;} public String getReceiverId(){return receiverId;} public void setReceiverId(String v){receiverId=v;}
 public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public String getState(){return state;} public void setState(String v){state=v;}
 public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
 public Instant getDecidedAt(){return decidedAt;} public void setDecidedAt(Instant v){decidedAt=v;}
}
