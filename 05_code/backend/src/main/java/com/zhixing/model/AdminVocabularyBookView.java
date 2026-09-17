package com.zhixing.model;

public class AdminVocabularyBookView {
    private String bookId,bookCode,bookName,bookType,levelCode,description;
    private Integer declaredWordCount,memberCount,availableCount;
    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public String getBookCode(){return bookCode;} public void setBookCode(String v){bookCode=v;}
    public String getBookName(){return bookName;} public void setBookName(String v){bookName=v;}
    public String getBookType(){return bookType;} public void setBookType(String v){bookType=v;}
    public String getLevelCode(){return levelCode;} public void setLevelCode(String v){levelCode=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Integer getDeclaredWordCount(){return declaredWordCount;} public void setDeclaredWordCount(Integer v){declaredWordCount=v;}
    public Integer getMemberCount(){return memberCount;} public void setMemberCount(Integer v){memberCount=v;}
    public Integer getAvailableCount(){return availableCount;} public void setAvailableCount(Integer v){availableCount=v;}
    public boolean isCountMismatch(){return declaredWordCount!=null&&memberCount!=null&&!declaredWordCount.equals(memberCount);}
}
