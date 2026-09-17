package com.zhixing.model;

import java.util.Collections;
import java.util.List;

public class AdminUserPageView {
    private Integer total,page,pageSize,totalPages;
    private String keyword,status;
    private List<AdminUserView> items=Collections.emptyList();
    public Integer getTotal(){return total;} public void setTotal(Integer v){total=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Integer getTotalPages(){return totalPages;} public void setTotalPages(Integer v){totalPages=v;}
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public List<AdminUserView> getItems(){return items;} public void setItems(List<AdminUserView> v){items=v;}
}
