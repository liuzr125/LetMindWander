package com.zhixing.model;

public class WordNotebookSummaryView {
    private Integer totalCount,dueCount;
    public WordNotebookSummaryView(){}
    public WordNotebookSummaryView(Integer totalCount,Integer dueCount){this.totalCount=totalCount;this.dueCount=dueCount;}
    public Integer getTotalCount(){return totalCount;} public void setTotalCount(Integer v){totalCount=v;}
    public Integer getDueCount(){return dueCount;} public void setDueCount(Integer v){dueCount=v;}
}
