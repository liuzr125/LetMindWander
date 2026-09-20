package com.zhixing.model;

import java.time.Instant;
import java.time.LocalDate;

public class ArticleGenerationRunView {
    private String id, triggerKey, state, errorMessage;
    private LocalDate runDate;
    private int bookCount, generatedCount, skippedCount, failedCount;
    private Instant startedAt, finishedAt, createdAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTriggerKey(){return triggerKey;} public void setTriggerKey(String v){triggerKey=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public LocalDate getRunDate(){return runDate;} public void setRunDate(LocalDate v){runDate=v;}
    public int getBookCount(){return bookCount;} public void setBookCount(int v){bookCount=v;}
    public int getGeneratedCount(){return generatedCount;} public void setGeneratedCount(int v){generatedCount=v;}
    public int getSkippedCount(){return skippedCount;} public void setSkippedCount(int v){skippedCount=v;}
    public int getFailedCount(){return failedCount;} public void setFailedCount(int v){failedCount=v;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;}
    public Instant getFinishedAt(){return finishedAt;} public void setFinishedAt(Instant v){finishedAt=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}
