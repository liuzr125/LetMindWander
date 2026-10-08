package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** Primary key mapping for follow_recording; richer projections are explicit Mapper statements. */
@TableName("follow_recording")
public class FollowRecordingPersistenceEntity {
  @TableId private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
