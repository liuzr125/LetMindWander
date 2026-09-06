package com.zhixing.model;

/** 今日页只接收可导航的稳定 ID，不从 targetKey 反向解析业务对象。 */
public class DailyTaskView {
    private String id, taskType, title, targetKey, status;
    private String contentId, contentVersionId, knowledgeId, journalId, actionId;
    private Integer estimatedSeconds, sortNo, versionNo;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTaskType(){return taskType;} public void setTaskType(String v){taskType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getTargetKey(){return targetKey;} public void setTargetKey(String v){targetKey=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getContentVersionId(){return contentVersionId;} public void setContentVersionId(String v){contentVersionId=v;}
    public String getKnowledgeId(){return knowledgeId;} public void setKnowledgeId(String v){knowledgeId=v;}
    public String getJournalId(){return journalId;} public void setJournalId(String v){journalId=v;}
    public String getActionId(){return actionId;} public void setActionId(String v){actionId=v;}
    public Integer getEstimatedSeconds(){return estimatedSeconds;} public void setEstimatedSeconds(Integer v){estimatedSeconds=v;}
    public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
}
