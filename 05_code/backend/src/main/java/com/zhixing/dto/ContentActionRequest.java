package com.zhixing.dto;

/** 内容页动作；taskId 可空，只有从今日任务进入时才联动完成任务。 */
public class ContentActionRequest {
    private Boolean active;
    private String taskId;
    private Integer expectedVersion;
    public Boolean getActive() { return active; } public void setActive(Boolean active) { this.active = active; }
    public String getTaskId() { return taskId; } public void setTaskId(String taskId) { this.taskId = taskId; }
    public Integer getExpectedVersion() { return expectedVersion; } public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
}
