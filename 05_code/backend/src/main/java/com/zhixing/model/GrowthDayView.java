package com.zhixing.model;

import java.time.LocalDate;

public class GrowthDayView {
    private LocalDate date;
    private String weekday;
    private Integer plannedCount,doneCount,heightPercent;
    public LocalDate getDate(){return date;} public void setDate(LocalDate v){date=v;}
    public String getWeekday(){return weekday;} public void setWeekday(String v){weekday=v;}
    public Integer getPlannedCount(){return plannedCount;} public void setPlannedCount(Integer v){plannedCount=v;}
    public Integer getDoneCount(){return doneCount;} public void setDoneCount(Integer v){doneCount=v;}
    public Integer getHeightPercent(){return heightPercent;} public void setHeightPercent(Integer v){heightPercent=v;}
}
