package com.zhixing.dto;

public class WordMemoryHintRequest {
    private String hintType;
    private Integer expectedVersion;
    public String getHintType(){return hintType;} public void setHintType(String v){hintType=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
