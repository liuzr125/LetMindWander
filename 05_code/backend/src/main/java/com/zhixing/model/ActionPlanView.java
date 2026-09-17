package com.zhixing.model;

import java.time.LocalDate;
import java.util.List;

public class ActionPlanView {
    private String summaryId;
    private LocalDate weekStart,weekEnd;
    private Integer maxActions,plannedMinutes,dailyBudgetMinutes;
    private Boolean containsNonActiveDay;
    private List<ActionView> actions;
    public String getSummaryId(){return summaryId;} public void setSummaryId(String v){summaryId=v;}
    public LocalDate getWeekStart(){return weekStart;} public void setWeekStart(LocalDate v){weekStart=v;}
    public LocalDate getWeekEnd(){return weekEnd;} public void setWeekEnd(LocalDate v){weekEnd=v;}
    public Integer getMaxActions(){return maxActions;} public void setMaxActions(Integer v){maxActions=v;}
    public Integer getPlannedMinutes(){return plannedMinutes;} public void setPlannedMinutes(Integer v){plannedMinutes=v;}
    public Integer getDailyBudgetMinutes(){return dailyBudgetMinutes;} public void setDailyBudgetMinutes(Integer v){dailyBudgetMinutes=v;}
    public Boolean getContainsNonActiveDay(){return containsNonActiveDay;} public void setContainsNonActiveDay(Boolean v){containsNonActiveDay=v;}
    public List<ActionView> getActions(){return actions;} public void setActions(List<ActionView> v){actions=v;}
}
