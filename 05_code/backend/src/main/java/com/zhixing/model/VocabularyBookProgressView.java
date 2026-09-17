package com.zhixing.model;

import java.util.ArrayList;
import java.util.List;

public class VocabularyBookProgressView {
    private String bookId,bookName,bookCode,status;
    private Integer totalCount,learnedCount,remainingCount,dailyNewCount,estimatedRemainingDays,page,pageSize;
    private Double completionRate;
    private Boolean hasMore;
    private List<VocabularyBookProgressItemView> items=new ArrayList<VocabularyBookProgressItemView>();
    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public String getBookName(){return bookName;} public void setBookName(String v){bookName=v;}
    public String getBookCode(){return bookCode;} public void setBookCode(String v){bookCode=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getTotalCount(){return totalCount;} public void setTotalCount(Integer v){totalCount=v;}
    public Integer getLearnedCount(){return learnedCount;} public void setLearnedCount(Integer v){learnedCount=v;}
    public Integer getRemainingCount(){return remainingCount;} public void setRemainingCount(Integer v){remainingCount=v;}
    public Integer getDailyNewCount(){return dailyNewCount;} public void setDailyNewCount(Integer v){dailyNewCount=v;}
    public Integer getEstimatedRemainingDays(){return estimatedRemainingDays;} public void setEstimatedRemainingDays(Integer v){estimatedRemainingDays=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Double getCompletionRate(){return completionRate;} public void setCompletionRate(Double v){completionRate=v;}
    public Boolean getHasMore(){return hasMore;} public void setHasMore(Boolean v){hasMore=v;}
    public List<VocabularyBookProgressItemView> getItems(){return items;} public void setItems(List<VocabularyBookProgressItemView> v){items=v;}
}
