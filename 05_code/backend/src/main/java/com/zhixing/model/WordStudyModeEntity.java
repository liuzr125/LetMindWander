package com.zhixing.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("word_study_mode")
public class WordStudyModeEntity {
  @TableId private Integer settingId;
  private String intervalMode;

  public Integer getSettingId() {
    return settingId;
  }

  public void setSettingId(Integer value) {
    settingId = value;
  }

  public String getIntervalMode() {
    return intervalMode;
  }

  public void setIntervalMode(String value) {
    intervalMode = value;
  }
}
