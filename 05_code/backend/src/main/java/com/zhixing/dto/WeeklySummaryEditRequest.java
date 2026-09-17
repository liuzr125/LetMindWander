package com.zhixing.dto;

public class WeeklySummaryEditRequest {
    private String body;
    private Integer expectedVersion;
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
