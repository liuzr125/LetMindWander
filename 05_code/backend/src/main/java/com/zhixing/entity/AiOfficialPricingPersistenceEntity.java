package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** Primary key mapping for ai_model_price; richer projections are explicit Mapper statements. */
@TableName("ai_model_price")
public class AiOfficialPricingPersistenceEntity {
  @TableId private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
