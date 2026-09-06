package com.zhixing.entity;

import java.time.Instant;
import java.time.LocalDate;

public class ReviewFeedbackEntity {
    private String id,ownerId,knowledgeId,scheduleId,feedback,beforeState,afterState,taskId;
    private Integer beforeStage,afterStage,revisionNo;
    private LocalDate businessDate,beforeDueDate,afterDueDate;
    private Instant effectiveAt;
    public String getId(){return id;} public void setId(String v){id=v;} public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getKnowledgeId(){return knowledgeId;} public void setKnowledgeId(String v){knowledgeId=v;} public String getScheduleId(){return scheduleId;} public void setScheduleId(String v){scheduleId=v;}
    public String getFeedback(){return feedback;} public void setFeedback(String v){feedback=v;} public String getBeforeState(){return beforeState;} public void setBeforeState(String v){beforeState=v;}
    public String getAfterState(){return afterState;} public void setAfterState(String v){afterState=v;} public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public Integer getBeforeStage(){return beforeStage;} public void setBeforeStage(Integer v){beforeStage=v;} public Integer getAfterStage(){return afterStage;} public void setAfterStage(Integer v){afterStage=v;}
    public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer v){revisionNo=v;} public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public LocalDate getBeforeDueDate(){return beforeDueDate;} public void setBeforeDueDate(LocalDate v){beforeDueDate=v;} public LocalDate getAfterDueDate(){return afterDueDate;} public void setAfterDueDate(LocalDate v){afterDueDate=v;}
    public Instant getEffectiveAt(){return effectiveAt;} public void setEffectiveAt(Instant v){effectiveAt=v;}
}
