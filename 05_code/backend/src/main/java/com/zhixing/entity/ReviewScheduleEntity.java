package com.zhixing.entity;

import java.time.LocalDate;

public class ReviewScheduleEntity {
    private String id,ownerId,knowledgeId,state,lastFeedbackId;
    private Integer stage,versionNo;
    private LocalDate dueDate;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getKnowledgeId(){return knowledgeId;} public void setKnowledgeId(String v){knowledgeId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getLastFeedbackId(){return lastFeedbackId;} public void setLastFeedbackId(String v){lastFeedbackId=v;}
    public Integer getStage(){return stage;} public void setStage(Integer v){stage=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;}
}
