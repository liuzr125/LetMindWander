package com.zhixing.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端「用户反馈」分页视图，summary 给出待处理 / 已处理 / 总数。 */
public class AdminFeedbackPageView {
    private Integer total,page,pageSize,totalPages;
    private String state,keyword;
    private Map<String,Object> summary=new LinkedHashMap<String,Object>();
    private List<AdminFeedbackView> items=Collections.emptyList();
    public Integer getTotal(){return total;} public void setTotal(Integer v){total=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Integer getTotalPages(){return totalPages;} public void setTotalPages(Integer v){totalPages=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public Map<String,Object> getSummary(){return summary;} public void setSummary(Map<String,Object> v){summary=v;}
    public List<AdminFeedbackView> getItems(){return items;} public void setItems(List<AdminFeedbackView> v){items=v;}
}
