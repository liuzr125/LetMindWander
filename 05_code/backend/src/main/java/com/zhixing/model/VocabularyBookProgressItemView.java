package com.zhixing.model;

import java.time.Instant;

public class VocabularyBookProgressItemView {
    private String contentId,wordTerm,phonetic,meaning;
    private Boolean learned;
    private Instant learnedAt;
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public Boolean getLearned(){return learned;} public void setLearned(Boolean v){learned=v;}
    public Instant getLearnedAt(){return learnedAt;} public void setLearnedAt(Instant v){learnedAt=v;}
}
