package com.zhixing.model;

public class UserVocabularyBookRow {
    private String id,ownerId,bookId,state;
    private Integer dailyNewLimit,rowVersion;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public Integer getDailyNewLimit(){return dailyNewLimit;} public void setDailyNewLimit(Integer v){dailyNewLimit=v;}
    public Integer getRowVersion(){return rowVersion;} public void setRowVersion(Integer v){rowVersion=v;}
}
