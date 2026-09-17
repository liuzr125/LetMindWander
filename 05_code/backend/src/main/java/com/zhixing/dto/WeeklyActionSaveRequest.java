package com.zhixing.dto;

import java.time.LocalDate;

public class WeeklyActionSaveRequest {
    private String id,title,note;
    private LocalDate scheduledDate;
    private Integer estimatedMinutes,expectedVersion;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public LocalDate getScheduledDate(){return scheduledDate;} public void setScheduledDate(LocalDate v){scheduledDate=v;}
    public Integer getEstimatedMinutes(){return estimatedMinutes;} public void setEstimatedMinutes(Integer v){estimatedMinutes=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
