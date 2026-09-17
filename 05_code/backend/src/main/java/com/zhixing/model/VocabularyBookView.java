package com.zhixing.model;

public class VocabularyBookView {
    private String id,bookCode,bookName,bookType,levelCode,description,selectionState;
    private Integer wordCount,dailyNewLimit,rowVersion;
    private Boolean recommended,selected;

    public String getId(){return id;} public void setId(String v){id=v;}
    public String getBookCode(){return bookCode;} public void setBookCode(String v){bookCode=v;}
    public String getBookName(){return bookName;} public void setBookName(String v){bookName=v;}
    public String getBookType(){return bookType;} public void setBookType(String v){bookType=v;}
    public String getLevelCode(){return levelCode;} public void setLevelCode(String v){levelCode=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getSelectionState(){return selectionState;} public void setSelectionState(String v){selectionState=v;}
    public Integer getWordCount(){return wordCount;} public void setWordCount(Integer v){wordCount=v;}
    public Integer getDailyNewLimit(){return dailyNewLimit;} public void setDailyNewLimit(Integer v){dailyNewLimit=v;}
    public Integer getRowVersion(){return rowVersion;} public void setRowVersion(Integer v){rowVersion=v;}
    public Boolean getRecommended(){return recommended;} public void setRecommended(Boolean v){recommended=v;}
    public Boolean getSelected(){return selected;} public void setSelected(Boolean v){selected=v;}
}
