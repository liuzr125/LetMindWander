package com.zhixing.model;

import java.time.Instant;
import java.time.LocalDate;

public class WordMemorySessionRow {
    private String id,ownerId,sourceType,returnTo,taskId,requiredDimensions,state,idempotencyKey;
    private LocalDate businessDate;
    private Integer targetCount,addToReview,versionNo;
    private Instant completedAt,createdAt,updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public String getReturnTo(){return returnTo;} public void setReturnTo(String v){returnTo=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getRequiredDimensions(){return requiredDimensions;} public void setRequiredDimensions(String v){requiredDimensions=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public Integer getTargetCount(){return targetCount;} public void setTargetCount(Integer v){targetCount=v;}
    public Integer getAddToReview(){return addToReview;} public void setAddToReview(Integer v){addToReview=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
