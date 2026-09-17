package com.zhixing.model;

import java.time.Instant;

public class LearningListItemView {
    private String contentId,versionId,contentType,title,summary,difficulty,stage,wordTerm,phonetic,meaning,exampleText,topicName,learningStatus;
    private Integer estimatedSeconds,familiarityPercent,wordCount;
    private Boolean understood,inReview,inWordBook;
    private Instant publishedAt;
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getVersionId(){return versionId;} public void setVersionId(String v){versionId=v;}
    public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getDifficulty(){return difficulty;} public void setDifficulty(String v){difficulty=v;}
    public String getStage(){return stage;} public void setStage(String v){stage=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public String getExampleText(){return exampleText;} public void setExampleText(String v){exampleText=v;}
    public String getTopicName(){return topicName;} public void setTopicName(String v){topicName=v;}
    public String getLearningStatus(){return learningStatus;} public void setLearningStatus(String v){learningStatus=v;}
    public Integer getEstimatedSeconds(){return estimatedSeconds;} public void setEstimatedSeconds(Integer v){estimatedSeconds=v;}
    public Integer getFamiliarityPercent(){return familiarityPercent;} public void setFamiliarityPercent(Integer v){familiarityPercent=v;}
    public Integer getWordCount(){return wordCount;} public void setWordCount(Integer v){wordCount=v;}
    public Boolean getUnderstood(){return understood;} public void setUnderstood(Boolean v){understood=v;}
    public Boolean getInReview(){return inReview;} public void setInReview(Boolean v){inReview=v;}
    public Boolean getInWordBook(){return inWordBook;} public void setInWordBook(Boolean v){inWordBook=v;}
    public Instant getPublishedAt(){return publishedAt;} public void setPublishedAt(Instant v){publishedAt=v;}
}
