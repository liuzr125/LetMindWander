package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** Primary key mapping for media_asset; richer projections are explicit Mapper statements. */
@TableName("media_asset")
public class MediaPersistenceEntity {
  @TableId private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
