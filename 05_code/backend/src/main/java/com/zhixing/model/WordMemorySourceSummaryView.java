package com.zhixing.model;

import java.time.LocalDate;

public class WordMemorySourceSummaryView {
    private LocalDate businessDate;
    private String currentBookId,currentBookName;
    private Integer todayLearnedCount,currentBookLearnedCount,maxBatchSize;
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public String getCurrentBookId(){return currentBookId;} public void setCurrentBookId(String v){currentBookId=v;}
    public String getCurrentBookName(){return currentBookName;} public void setCurrentBookName(String v){currentBookName=v;}
    public Integer getTodayLearnedCount(){return todayLearnedCount;} public void setTodayLearnedCount(Integer v){todayLearnedCount=v;}
    public Integer getCurrentBookLearnedCount(){return currentBookLearnedCount;} public void setCurrentBookLearnedCount(Integer v){currentBookLearnedCount=v;}
    public Integer getMaxBatchSize(){return maxBatchSize;} public void setMaxBatchSize(Integer v){maxBatchSize=v;}
}
