package com.zhixing.model;

public class AdminVocabularyWordView {
    private String contentId,wordTerm,phonetic,meaning,stage,contentState,reviewStatus,sourceLevel,sourceRef;
    private Integer sortNo,importance;
    private Boolean core;
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public String getStage(){return stage;} public void setStage(String v){stage=v;}
    public String getContentState(){return contentState;} public void setContentState(String v){contentState=v;}
    public String getReviewStatus(){return reviewStatus;} public void setReviewStatus(String v){reviewStatus=v;}
    public String getSourceLevel(){return sourceLevel;} public void setSourceLevel(String v){sourceLevel=v;}
    public String getSourceRef(){return sourceRef;} public void setSourceRef(String v){sourceRef=v;}
    public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
    public Integer getImportance(){return importance;} public void setImportance(Integer v){importance=v;}
    public Boolean getCore(){return core;} public void setCore(Boolean v){core=v;}
}
