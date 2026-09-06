package com.zhixing.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PlanView {
    private String id;
    private Integer versionNo;
    private LocalDate effectiveDate;
    private Integer dailyBudgetMin;
    private Integer weekdaysMask;
    private List<TopicView> topics = new ArrayList<TopicView>();
    private String difficulty;
    private Integer techCount;
    private Integer newWordCount;
    private Boolean journalEnabled;
    private Boolean reviewEnabled;
    private Integer reviewLimit;
    private Boolean paused;
    private LocalDate pauseUntil;
    private boolean activeTomorrow;

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public Integer getVersionNo() { return versionNo; } public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public LocalDate getEffectiveDate() { return effectiveDate; } public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public Integer getDailyBudgetMin() { return dailyBudgetMin; } public void setDailyBudgetMin(Integer dailyBudgetMin) { this.dailyBudgetMin = dailyBudgetMin; }
    public Integer getWeekdaysMask() { return weekdaysMask; } public void setWeekdaysMask(Integer weekdaysMask) { this.weekdaysMask = weekdaysMask; }
    public List<TopicView> getTopics() { return topics; } public void setTopics(List<TopicView> topics) { this.topics = topics; }
    public String getDifficulty() { return difficulty; } public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public Integer getTechCount() { return techCount; } public void setTechCount(Integer techCount) { this.techCount = techCount; }
    public Integer getNewWordCount() { return newWordCount; } public void setNewWordCount(Integer newWordCount) { this.newWordCount = newWordCount; }
    public Boolean getJournalEnabled() { return journalEnabled; } public void setJournalEnabled(Boolean journalEnabled) { this.journalEnabled = journalEnabled; }
    public Boolean getReviewEnabled() { return reviewEnabled; } public void setReviewEnabled(Boolean reviewEnabled) { this.reviewEnabled = reviewEnabled; }
    public Integer getReviewLimit() { return reviewLimit; } public void setReviewLimit(Integer reviewLimit) { this.reviewLimit = reviewLimit; }
    public Boolean getPaused() { return paused; } public void setPaused(Boolean paused) { this.paused = paused; }
    public LocalDate getPauseUntil() { return pauseUntil; } public void setPauseUntil(LocalDate pauseUntil) { this.pauseUntil = pauseUntil; }
    public boolean isActiveTomorrow() { return activeTomorrow; } public void setActiveTomorrow(boolean activeTomorrow) { this.activeTomorrow = activeTomorrow; }

    public static class TopicView {
        private String id; private String name; private boolean system;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
        public boolean isSystem() { return system; } public void setSystem(boolean system) { this.system = system; }
    }
}
