package com.zhixing.model;

import java.time.Instant;

/** 管理端「词书学习记录」列表行：一个用户在一本英语词书上的学习情况。 */
public class AdminStudyRecordView {
    private String id,ownerId,nickname,shortId,mobile,bookId,bookName,bookType,levelCode,levelLabel;
    private Integer roundNo,carriedLearnedCount;
    private Integer totalWords,learnedWords,masteredWords,learningWords,completionPercent,studyDayCount,studyCount,reviewedCount;
    private Boolean currentBook,currentRound,ongoing;
    private Instant selectedAt,endedAt,firstStudiedAt,lastStudiedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public Integer getRoundNo(){return roundNo;} public void setRoundNo(Integer v){roundNo=v;}
    public Integer getCarriedLearnedCount(){return carriedLearnedCount;} public void setCarriedLearnedCount(Integer v){carriedLearnedCount=v;}
    public Boolean getCurrentRound(){return currentRound;} public void setCurrentRound(Boolean v){currentRound=v;}
    public Boolean getOngoing(){return ongoing;} public void setOngoing(Boolean v){ongoing=v;}
    public Instant getSelectedAt(){return selectedAt;} public void setSelectedAt(Instant v){selectedAt=v;}
    public Instant getEndedAt(){return endedAt;} public void setEndedAt(Instant v){endedAt=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getNickname(){return nickname;} public void setNickname(String v){nickname=v;}
    public String getShortId(){return shortId;} public void setShortId(String v){shortId=v;}
    public String getMobile(){return mobile;} public void setMobile(String v){mobile=v;}
    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public String getBookName(){return bookName;} public void setBookName(String v){bookName=v;}
    public String getBookType(){return bookType;} public void setBookType(String v){bookType=v;}
    public String getLevelCode(){return levelCode;} public void setLevelCode(String v){levelCode=v;}
    public String getLevelLabel(){return levelLabel;} public void setLevelLabel(String v){levelLabel=v;}
    public Integer getTotalWords(){return totalWords;} public void setTotalWords(Integer v){totalWords=v;}
    public Integer getLearnedWords(){return learnedWords;} public void setLearnedWords(Integer v){learnedWords=v;}
    public Integer getMasteredWords(){return masteredWords;} public void setMasteredWords(Integer v){masteredWords=v;}
    public Integer getLearningWords(){return learningWords;} public void setLearningWords(Integer v){learningWords=v;}
    public Integer getCompletionPercent(){return completionPercent;} public void setCompletionPercent(Integer v){completionPercent=v;}
    public Integer getStudyDayCount(){return studyDayCount;} public void setStudyDayCount(Integer v){studyDayCount=v;}
    public Integer getStudyCount(){return studyCount;} public void setStudyCount(Integer v){studyCount=v;}
    public Integer getReviewedCount(){return reviewedCount;} public void setReviewedCount(Integer v){reviewedCount=v;}
    public Boolean getCurrentBook(){return currentBook;} public void setCurrentBook(Boolean v){currentBook=v;}
    public Instant getFirstStudiedAt(){return firstStudiedAt;} public void setFirstStudiedAt(Instant v){firstStudiedAt=v;}
    public Instant getLastStudiedAt(){return lastStudiedAt;} public void setLastStudiedAt(Instant v){lastStudiedAt=v;}
}
