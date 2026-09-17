package com.zhixing.model;

import java.time.LocalDate;

public class GrowthMetricRow {
    private LocalDate businessDate;
    private Integer plannedCount,doneCount;
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public Integer getPlannedCount(){return plannedCount;} public void setPlannedCount(Integer v){plannedCount=v;}
    public Integer getDoneCount(){return doneCount;} public void setDoneCount(Integer v){doneCount=v;}
}
