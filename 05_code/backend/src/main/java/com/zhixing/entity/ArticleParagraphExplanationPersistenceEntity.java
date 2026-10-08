package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Primary key mapping for article_paragraph_explanation; richer projections are explicit Mapper
 * statements.
 */
@TableName("article_paragraph_explanation")
public class ArticleParagraphExplanationPersistenceEntity {
  @TableId private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
