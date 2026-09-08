package com.zhixing.dto;

public class KnowledgeVerificationRequest {
    private Integer expectedVersion;
    private String status, note;
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
}
