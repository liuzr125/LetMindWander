package com.zhixing.model;

import java.util.List;

/** 管理端「词书学习记录」详情里的词条分页。 */
public class AdminStudyWordPageView {
    private Integer total,page,pageSize,totalPages;
    private String status,scope;
    private List<AdminStudyWordView> items;
    public Integer getTotal(){return total;} public void setTotal(Integer v){total=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Integer getTotalPages(){return totalPages;} public void setTotalPages(Integer v){totalPages=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getScope(){return scope;} public void setScope(String v){scope=v;}
    public List<AdminStudyWordView> getItems(){return items;} public void setItems(List<AdminStudyWordView> v){items=v;}
}
