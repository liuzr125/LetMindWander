package com.zhixing.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
@TableName("friend_relation")
public class FriendRelationEntity {
 @TableId private String id;
 @TableField("user_low_id") private String userLowId;
 @TableField("user_high_id") private String userHighId;
 private String state; private Integer generation;
 @TableField("version_no") private Integer versionNo;
 @TableField("accepted_at") private Instant acceptedAt;
 @TableField("removed_at") private Instant removedAt;
 public String getId(){return id;} public void setId(String v){id=v;}
 public String getUserLowId(){return userLowId;} public void setUserLowId(String v){userLowId=v;}
 public String getUserHighId(){return userHighId;} public void setUserHighId(String v){userHighId=v;}
 public String getState(){return state;} public void setState(String v){state=v;}
 public Integer getGeneration(){return generation;} public void setGeneration(Integer v){generation=v;}
 public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
 public Instant getAcceptedAt(){return acceptedAt;} public void setAcceptedAt(Instant v){acceptedAt=v;}
 public Instant getRemovedAt(){return removedAt;} public void setRemovedAt(Instant v){removedAt=v;}
}
