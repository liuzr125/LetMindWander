package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;

/** MyBatis-Plus mapping for the current knowledge snapshot. */
@TableName("knowledge_item")
public class KnowledgeItemEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("item_type") private String itemType;
    private String title;
    private String body;
    @TableField("problem_json") private String problemJson;
    @TableField("search_text") private String searchText;
    @TableField("learning_status") private String learningStatus;
    @TableField("verification_status") private String verificationStatus;
    @TableField("last_verified_date") private LocalDate lastVerifiedDate;
    @TableField("source_content_id") private String sourceContentId;
    @TableField("source_content_version_id") private String sourceContentVersionId;
    @TableField("bookmark_content_id") private String bookmarkContentId;
    @TableField("note_parent_id") private String noteParentId;
    @TableField("version_no") private Integer versionNo;
    private String visibility;
    private String state;
    @TableField("created_at") private Instant createdAt;
    @TableField("updated_at") private Instant updatedAt;

    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getItemType(){return itemType;} public void setItemType(String v){itemType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getProblemJson(){return problemJson;} public void setProblemJson(String v){problemJson=v;}
    public String getSearchText(){return searchText;} public void setSearchText(String v){searchText=v;}
    public String getLearningStatus(){return learningStatus;} public void setLearningStatus(String v){learningStatus=v;}
    public String getVerificationStatus(){return verificationStatus;} public void setVerificationStatus(String v){verificationStatus=v;}
    public LocalDate getLastVerifiedDate(){return lastVerifiedDate;} public void setLastVerifiedDate(LocalDate v){lastVerifiedDate=v;}
    public String getSourceContentId(){return sourceContentId;} public void setSourceContentId(String v){sourceContentId=v;}
    public String getSourceContentVersionId(){return sourceContentVersionId;} public void setSourceContentVersionId(String v){sourceContentVersionId=v;}
    public String getBookmarkContentId(){return bookmarkContentId;} public void setBookmarkContentId(String v){bookmarkContentId=v;}
    public String getNoteParentId(){return noteParentId;} public void setNoteParentId(String v){noteParentId=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public String getVisibility(){return visibility;} public void setVisibility(String v){visibility=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
