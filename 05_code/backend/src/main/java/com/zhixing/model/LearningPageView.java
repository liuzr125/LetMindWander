package com.zhixing.model;

import java.util.ArrayList;
import java.util.List;

public class LearningPageView {
    private Integer page,pageSize;
    private Boolean hasMore;
    private List<LearningListItemView> items=new ArrayList<LearningListItemView>();
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Boolean getHasMore(){return hasMore;} public void setHasMore(Boolean v){hasMore=v;}
    public List<LearningListItemView> getItems(){return items;} public void setItems(List<LearningListItemView> v){items=v;}
}
