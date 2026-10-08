package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("announcement")
public class AnnouncementEntity {
  @TableId(type = IdType.INPUT)
  private String id;

  private String title;
  private String body;
  private String state;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Instant publishAt;

  private String createdBy;
  private String updatedBy;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String value) {
    body = value;
  }

  public String getState() {
    return state;
  }

  public void setState(String value) {
    state = value;
  }

  public Instant getPublishAt() {
    return publishAt;
  }

  public void setPublishAt(Instant value) {
    publishAt = value;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String value) {
    createdBy = value;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String value) {
    updatedBy = value;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    createdAt = value;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant value) {
    updatedAt = value;
  }
}
