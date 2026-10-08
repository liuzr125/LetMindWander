package com.zhixing.entity;

/** F04 状态机：DONE/CANCELLED 为终态；调整今日只能取消 TODO，不能改写已开始或已完成事实。 */
public enum DailyTaskStatus implements com.baomidou.mybatisplus.annotation.IEnum<String> {
  TODO,
  DOING,
  DONE,
  SKIPPED,
  CANCELLED;

  /** Persist the existing VARCHAR status code rather than serializing the Java enum. */
  @Override
  public String getValue() {
    return name();
  }
}
