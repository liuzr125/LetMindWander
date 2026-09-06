package com.zhixing.model;

import java.time.LocalDate;

public class ActionView {
    private String id, title, note, state;
    private LocalDate scheduledDate;
    private Integer estimatedMinutes, versionNo;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public LocalDate getScheduledDate(){return scheduledDate;} public void setScheduledDate(LocalDate v){scheduledDate=v;}
    public Integer getEstimatedMinutes(){return estimatedMinutes;} public void setEstimatedMinutes(Integer v){estimatedMinutes=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
}
