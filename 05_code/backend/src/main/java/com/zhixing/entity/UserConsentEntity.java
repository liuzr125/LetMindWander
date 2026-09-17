package com.zhixing.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
@TableName("user_consent")
public class UserConsentEntity {
 @TableId private String id;
 @TableField("owner_id") private String ownerId;
 private String purpose;
 @TableField("document_version") private String documentVersion;
 private String decision;
 @TableField("occurred_at") private Instant occurredAt;
 @TableField("created_at") private Instant createdAt;
 public String getId(){return id;} public void setId(String v){id=v;}
 public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
 public String getPurpose(){return purpose;} public void setPurpose(String v){purpose=v;}
 public String getDocumentVersion(){return documentVersion;} public void setDocumentVersion(String v){documentVersion=v;}
 public String getDecision(){return decision;} public void setDecision(String v){decision=v;}
 public Instant getOccurredAt(){return occurredAt;} public void setOccurredAt(Instant v){occurredAt=v;}
 public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}
