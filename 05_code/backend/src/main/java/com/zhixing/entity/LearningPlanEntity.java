package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

@TableName("learning_plan")
public class LearningPlanEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("version_no") private Integer versionNo;
    @TableField("effective_date") private LocalDate effectiveDate;
    @TableField("daily_budget_min") private Integer dailyBudgetMin;
    @TableField("weekdays_mask") private Integer weekdaysMask;
    /** R1 主题位图仅用于读取迁移到系统 AI 主题，V3 不再写入或过滤。 */
    @TableField("topic_mask") private Integer topicMask;
    @TableField("difficulty") private String difficulty;
    @TableField("tech_count") private Integer techCount;
    @TableField("new_word_count") private Integer newWordCount;
    @TableField("journal_enabled") private Integer journalEnabled;
    @TableField("review_enabled") private Integer reviewEnabled;
    @TableField("review_limit") private Integer reviewLimit;
    @TableField("is_paused") private Integer isPaused;
    @TableField("pause_until") private LocalDate pauseUntil;

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; } public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Integer getVersionNo() { return versionNo; } public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public LocalDate getEffectiveDate() { return effectiveDate; } public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public Integer getDailyBudgetMin() { return dailyBudgetMin; } public void setDailyBudgetMin(Integer dailyBudgetMin) { this.dailyBudgetMin = dailyBudgetMin; }
    public Integer getWeekdaysMask() { return weekdaysMask; } public void setWeekdaysMask(Integer weekdaysMask) { this.weekdaysMask = weekdaysMask; }
    public Integer getTopicMask() { return topicMask; } public void setTopicMask(Integer topicMask) { this.topicMask = topicMask; }
    public String getDifficulty() { return difficulty; } public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Integer getTechCount() { return techCount; } public void setTechCount(Integer techCount) { this.techCount = techCount; }
    public Integer getNewWordCount() { return newWordCount; } public void setNewWordCount(Integer newWordCount) { this.newWordCount = newWordCount; }
    public Integer getJournalEnabled() { return journalEnabled; } public void setJournalEnabled(Integer journalEnabled) { this.journalEnabled = journalEnabled; }
    public Integer getReviewEnabled() { return reviewEnabled; } public void setReviewEnabled(Integer reviewEnabled) { this.reviewEnabled = reviewEnabled; }
    public Integer getReviewLimit() { return reviewLimit; } public void setReviewLimit(Integer reviewLimit) { this.reviewLimit = reviewLimit; }
    public Integer getIsPaused() { return isPaused; } public void setIsPaused(Integer isPaused) { this.isPaused = isPaused; }
    public LocalDate getPauseUntil() { return pauseUntil; } public void setPauseUntil(LocalDate pauseUntil) { this.pauseUntil = pauseUntil; }
}
