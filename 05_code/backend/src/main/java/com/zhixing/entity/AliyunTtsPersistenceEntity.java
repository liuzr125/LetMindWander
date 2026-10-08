package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** Primary key mapping for tts_usage_log; richer projections are explicit Mapper statements. */
@TableName("tts_usage_log")
public class AliyunTtsPersistenceEntity {
  @TableId private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
