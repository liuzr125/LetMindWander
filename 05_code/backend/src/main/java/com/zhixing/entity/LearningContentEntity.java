package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** MyBatis-Plus mapping for the stable public learning-content row. */
@TableName("learning_content")
public class LearningContentEntity {
    @TableId private String id;
    @TableField("content_type") private String contentType;
    private String stage;
    private String state;
    @TableField("published_version_id") private String publishedVersionId;
    @TableField("published_at") private Instant publishedAt;

    public String getId(){return id;} public void setId(String v){id=v;}
    public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
    public String getStage(){return stage;} public void setStage(String v){stage=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getPublishedVersionId(){return publishedVersionId;} public void setPublishedVersionId(String v){publishedVersionId=v;}
    public Instant getPublishedAt(){return publishedAt;} public void setPublishedAt(Instant v){publishedAt=v;}
}
