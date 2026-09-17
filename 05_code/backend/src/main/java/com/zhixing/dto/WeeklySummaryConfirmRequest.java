package com.zhixing.dto;

public class WeeklySummaryConfirmRequest {
    private String revisionId;
    private Integer expectedVersion;
    public String getRevisionId(){return revisionId;} public void setRevisionId(String v){revisionId=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
