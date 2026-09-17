package com.zhixing.model;

import java.time.LocalDate;
import java.util.List;

public class GrowthOverviewView {
    private LocalDate weekStart,weekEnd;
    private String weekLabel,weekStatus;
    private Integer accumulatedGrowthDays,weekCompleted,reviewFeedbackCount,dueReviewCount,reviewBacklogCount;
    private List<GrowthDayView> days;
    private WeeklySummaryView previousSummary;
    public LocalDate getWeekStart(){return weekStart;} public void setWeekStart(LocalDate v){weekStart=v;}
    public LocalDate getWeekEnd(){return weekEnd;} public void setWeekEnd(LocalDate v){weekEnd=v;}
    public String getWeekLabel(){return weekLabel;} public void setWeekLabel(String v){weekLabel=v;}
    public String getWeekStatus(){return weekStatus;} public void setWeekStatus(String v){weekStatus=v;}
    public Integer getAccumulatedGrowthDays(){return accumulatedGrowthDays;} public void setAccumulatedGrowthDays(Integer v){accumulatedGrowthDays=v;}
    public Integer getWeekCompleted(){return weekCompleted;} public void setWeekCompleted(Integer v){weekCompleted=v;}
    public Integer getReviewFeedbackCount(){return reviewFeedbackCount;} public void setReviewFeedbackCount(Integer v){reviewFeedbackCount=v;}
    public Integer getDueReviewCount(){return dueReviewCount;} public void setDueReviewCount(Integer v){dueReviewCount=v;}
    public Integer getReviewBacklogCount(){return reviewBacklogCount;} public void setReviewBacklogCount(Integer v){reviewBacklogCount=v;}
    public List<GrowthDayView> getDays(){return days;} public void setDays(List<GrowthDayView> v){days=v;}
    public WeeklySummaryView getPreviousSummary(){return previousSummary;} public void setPreviousSummary(WeeklySummaryView v){previousSummary=v;}
}
