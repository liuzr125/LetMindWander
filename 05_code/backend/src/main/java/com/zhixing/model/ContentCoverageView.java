package com.zhixing.model;

import java.util.List;

public class ContentCoverageView {
    private Integer publishedTotal,sourceCount;
    private List<CoverageItem> technicalTopics,wordStages;
    public Integer getPublishedTotal(){return publishedTotal;} public void setPublishedTotal(Integer v){publishedTotal=v;}
    public Integer getSourceCount(){return sourceCount;} public void setSourceCount(Integer v){sourceCount=v;}
    public List<CoverageItem> getTechnicalTopics(){return technicalTopics;} public void setTechnicalTopics(List<CoverageItem> v){technicalTopics=v;}
    public List<CoverageItem> getWordStages(){return wordStages;} public void setWordStages(List<CoverageItem> v){wordStages=v;}
    public static class CoverageItem { private String name; private Integer itemCount; public String getName(){return name;} public void setName(String v){name=v;} public Integer getItemCount(){return itemCount;} public void setItemCount(Integer v){itemCount=v;} }
}
