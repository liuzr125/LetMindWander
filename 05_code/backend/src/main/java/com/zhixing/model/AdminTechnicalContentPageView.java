package com.zhixing.model;

import java.util.Collections;
import java.util.List;

public class AdminTechnicalContentPageView {
    private Integer total,page,pageSize,totalPages;
    private String topic,keyword;
    private List<AdminTechnicalContentView> items=Collections.emptyList();
    public Integer getTotal(){return total;} public void setTotal(Integer v){total=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Integer getTotalPages(){return totalPages;} public void setTotalPages(Integer v){totalPages=v;}
    public String getTopic(){return topic;} public void setTopic(String v){topic=v;}
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public List<AdminTechnicalContentView> getItems(){return items;} public void setItems(List<AdminTechnicalContentView> v){items=v;}
}
