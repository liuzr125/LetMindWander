package com.zhixing.model;

import java.time.Instant;

public class CollectionRunView {
    private String id, taskName, triggerKey, state, errorCode, errorMessage;
    private int fetchedCount, insertedCount, skippedCount;
    private Instant startedAt, finishedAt, createdAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTaskName(){return taskName;} public void setTaskName(String v){taskName=v;}
    public String getTriggerKey(){return triggerKey;} public void setTriggerKey(String v){triggerKey=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public int getFetchedCount(){return fetchedCount;} public void setFetchedCount(int v){fetchedCount=v;}
    public int getInsertedCount(){return insertedCount;} public void setInsertedCount(int v){insertedCount=v;}
    public int getSkippedCount(){return skippedCount;} public void setSkippedCount(int v){skippedCount=v;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;}
    public Instant getFinishedAt(){return finishedAt;} public void setFinishedAt(Instant v){finishedAt=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}
