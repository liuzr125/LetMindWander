package com.zhixing.model;

import java.util.List;
import java.util.Map;

/** 管理端「词书学习记录」分页结果。 */
public class AdminStudyRecordPageView {
    private Integer total,page,pageSize,totalPages;
    private String keyword,bookId,dateFrom,dateTo;
    private Map<String,Object> summary;
    private List<AdminStudyRecordView> items;
    public Integer getTotal(){return total;} public void setTotal(Integer v){total=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Integer getTotalPages(){return totalPages;} public void setTotalPages(Integer v){totalPages=v;}
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public String getDateFrom(){return dateFrom;} public void setDateFrom(String v){dateFrom=v;}
    public String getDateTo(){return dateTo;} public void setDateTo(String v){dateTo=v;}
    public Map<String,Object> getSummary(){return summary;} public void setSummary(Map<String,Object> v){summary=v;}
    public List<AdminStudyRecordView> getItems(){return items;} public void setItems(List<AdminStudyRecordView> v){items=v;}
}
