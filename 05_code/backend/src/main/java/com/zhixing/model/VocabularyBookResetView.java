package com.zhixing.model;

/** 「重新学习」结果：把某本词书已学的词条划回未学。 */
public class VocabularyBookResetView {
    private String bookId,bookName;
    private Integer resetCount,pausedReviewCount;
    private Integer learnedCount,remainingCount,totalCount;
    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public String getBookName(){return bookName;} public void setBookName(String v){bookName=v;}
    public Integer getResetCount(){return resetCount;} public void setResetCount(Integer v){resetCount=v;}
    public Integer getPausedReviewCount(){return pausedReviewCount;} public void setPausedReviewCount(Integer v){pausedReviewCount=v;}
    public Integer getLearnedCount(){return learnedCount;} public void setLearnedCount(Integer v){learnedCount=v;}
    public Integer getRemainingCount(){return remainingCount;} public void setRemainingCount(Integer v){remainingCount=v;}
    public Integer getTotalCount(){return totalCount;} public void setTotalCount(Integer v){totalCount=v;}
}
