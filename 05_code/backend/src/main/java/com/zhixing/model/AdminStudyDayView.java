package com.zhixing.model;

/** 管理端「词书学习记录」详情里的每日明细：某天在这本书上学了多少、复习了多少、新学几个词。 */
public class AdminStudyDayView {
    private String businessDate;
    private Integer newWordCount,studyCount,reviewedCount;
    public String getBusinessDate(){return businessDate;} public void setBusinessDate(String v){businessDate=v;}
    public Integer getNewWordCount(){return newWordCount;} public void setNewWordCount(Integer v){newWordCount=v;}
    public Integer getStudyCount(){return studyCount;} public void setStudyCount(Integer v){studyCount=v;}
    public Integer getReviewedCount(){return reviewedCount;} public void setReviewedCount(Integer v){reviewedCount=v;}
}
