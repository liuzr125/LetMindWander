package com.zhixing.dto;

import java.util.List;

/** 客户端必须回传读取到的 versionNo，防止旧页面覆盖较新的计划版本。 */
public class UpdatePlanRequest {
    private Integer versionNo;
    private Integer dailyBudgetMin;
    private Integer weekdaysMask;
    private List<String> topicIds;
    private String difficulty;
    private Integer techCount;
    private Integer newWordCount;
    private Boolean journalEnabled;
    private Boolean reviewEnabled;
    private Integer reviewLimit;
    private Boolean paused;
    private String pauseUntil;
    private String changeReason;

    public Integer getVersionNo() { return versionNo; } public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Integer getDailyBudgetMin() { return dailyBudgetMin; } public void setDailyBudgetMin(Integer dailyBudgetMin) { this.dailyBudgetMin = dailyBudgetMin; }
    public Integer getWeekdaysMask() { return weekdaysMask; } public void setWeekdaysMask(Integer weekdaysMask) { this.weekdaysMask = weekdaysMask; }
    public List<String> getTopicIds() { return topicIds; } public void setTopicIds(List<String> topicIds) { this.topicIds = topicIds; }
    public String getDifficulty() { return difficulty; } public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Integer getTechCount() { return techCount; } public void setTechCount(Integer techCount) { this.techCount = techCount; }
    public Integer getNewWordCount() { return newWordCount; } public void setNewWordCount(Integer newWordCount) { this.newWordCount = newWordCount; }
    public Boolean getJournalEnabled() { return journalEnabled; } public void setJournalEnabled(Boolean journalEnabled) { this.journalEnabled = journalEnabled; }
    public Boolean getReviewEnabled() { return reviewEnabled; } public void setReviewEnabled(Boolean reviewEnabled) { this.reviewEnabled = reviewEnabled; }
    public Integer getReviewLimit() { return reviewLimit; } public void setReviewLimit(Integer reviewLimit) { this.reviewLimit = reviewLimit; }
    public Boolean getPaused() { return paused; } public void setPaused(Boolean paused) { this.paused = paused; }
    public String getPauseUntil() { return pauseUntil; } public void setPauseUntil(String pauseUntil) { this.pauseUntil = pauseUntil; }
    public String getChangeReason() { return changeReason; } public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
