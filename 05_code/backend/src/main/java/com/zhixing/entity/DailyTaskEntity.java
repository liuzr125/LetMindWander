package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 每日任务为事实记录；标题和预计耗时均保存快照，避免源内容变化影响历史。 */
@TableName("daily_task")
public class DailyTaskEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("package_id") private String packageId;
    @TableField("task_type") private String taskType;
    @TableField("title_snapshot") private String titleSnapshot;
    @TableField("target_key") private String targetKey;
    @TableField("content_id") private String contentId;
    @TableField("content_version_id") private String contentVersionId;
    @TableField("knowledge_id") private String knowledgeId;
    @TableField("journal_id") private String journalId;
    @TableField("action_id") private String actionId;
    private DailyTaskStatus status;
    @TableField("estimated_seconds") private Integer estimatedSeconds;
    @TableField("sort_no") private Integer sortNo;
    @TableField("cancel_reason") private String cancelReason;
    @TableField("started_at") private Instant startedAt;
    @TableField("completed_at") private Instant completedAt;
    @TableField("version_no") private Integer versionNo;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; } public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getPackageId() { return packageId; } public void setPackageId(String packageId) { this.packageId = packageId; }
    public String getTaskType() { return taskType; } public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTitleSnapshot() { return titleSnapshot; } public void setTitleSnapshot(String titleSnapshot) { this.titleSnapshot = titleSnapshot; }
    public String getTargetKey() { return targetKey; } public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
    public String getContentId() { return contentId; } public void setContentId(String contentId) { this.contentId = contentId; }
    public String getContentVersionId() { return contentVersionId; } public void setContentVersionId(String contentVersionId) { this.contentVersionId = contentVersionId; }
    public String getKnowledgeId() { return knowledgeId; } public void setKnowledgeId(String knowledgeId) { this.knowledgeId = knowledgeId; }
    public String getJournalId() { return journalId; } public void setJournalId(String journalId) { this.journalId = journalId; }
    public String getActionId() { return actionId; } public void setActionId(String actionId) { this.actionId = actionId; }
    public DailyTaskStatus getStatus() { return status; } public void setStatus(DailyTaskStatus status) { this.status = status; }
    public Integer getEstimatedSeconds() { return estimatedSeconds; } public void setEstimatedSeconds(Integer estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }
    public Integer getSortNo() { return sortNo; } public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getCancelReason() { return cancelReason; } public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Integer getVersionNo() { return versionNo; } public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
}
