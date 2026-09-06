package com.zhixing.dto;

public class UpdateActionRequest {
    private String title, note;
    private Integer estimatedMinutes, expectedVersion;
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public Integer getEstimatedMinutes(){return estimatedMinutes;} public void setEstimatedMinutes(Integer v){estimatedMinutes=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
