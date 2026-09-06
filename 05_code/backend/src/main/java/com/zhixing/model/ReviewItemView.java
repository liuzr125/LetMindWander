package com.zhixing.model;

public class ReviewItemView {
    private String scheduleId,knowledgeId,taskId,itemType,title,body,wordTerm,meaning,exampleText;
    private Integer stage,taskVersion;
    public String getScheduleId(){return scheduleId;} public void setScheduleId(String v){scheduleId=v;}
    public String getKnowledgeId(){return knowledgeId;} public void setKnowledgeId(String v){knowledgeId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getItemType(){return itemType;} public void setItemType(String v){itemType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public String getExampleText(){return exampleText;} public void setExampleText(String v){exampleText=v;}
    public Integer getStage(){return stage;} public void setStage(Integer v){stage=v;}
    public Integer getTaskVersion(){return taskVersion;} public void setTaskVersion(Integer v){taskVersion=v;}
}
