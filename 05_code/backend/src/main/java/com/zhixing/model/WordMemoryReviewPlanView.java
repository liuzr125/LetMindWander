package com.zhixing.model;

import java.time.LocalDate;

public class WordMemoryReviewPlanView {
    private String contentId,wordTerm,state;
    private LocalDate dueDate;
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;}
}
