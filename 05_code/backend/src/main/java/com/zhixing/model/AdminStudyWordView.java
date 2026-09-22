package com.zhixing.model;

import java.time.Instant;

/** 管理端「词书学习记录」详情里已学/学习中词条。 */
public class AdminStudyWordView {
    private String contentId,word,meaning,phonetic,difficulty,difficultyLabel,learningStatus,statusLabel;
    private Integer familiarityPercent;
    private Boolean inNotebook;
    private Instant firstCompletedAt,lastFeedbackAt;
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getWord(){return word;} public void setWord(String v){word=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getDifficulty(){return difficulty;} public void setDifficulty(String v){difficulty=v;}
    public String getDifficultyLabel(){return difficultyLabel;} public void setDifficultyLabel(String v){difficultyLabel=v;}
    public String getLearningStatus(){return learningStatus;} public void setLearningStatus(String v){learningStatus=v;}
    public String getStatusLabel(){return statusLabel;} public void setStatusLabel(String v){statusLabel=v;}
    public Integer getFamiliarityPercent(){return familiarityPercent;} public void setFamiliarityPercent(Integer v){familiarityPercent=v;}
    public Boolean getInNotebook(){return inNotebook;} public void setInNotebook(Boolean v){inNotebook=v;}
    public Instant getFirstCompletedAt(){return firstCompletedAt;} public void setFirstCompletedAt(Instant v){firstCompletedAt=v;}
    public Instant getLastFeedbackAt(){return lastFeedbackAt;} public void setLastFeedbackAt(Instant v){lastFeedbackAt=v;}
}
