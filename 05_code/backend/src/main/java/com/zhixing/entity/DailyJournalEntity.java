package com.zhixing.entity;

import java.time.Instant;
import java.time.LocalDate;

public class DailyJournalEntity {
    private String id, ownerId, currentRevisionId, submittedRevisionId, state;
    private LocalDate businessDate;
    private Integer versionNo;
    private Instant firstSubmittedAt, lastSubmittedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getCurrentRevisionId(){return currentRevisionId;} public void setCurrentRevisionId(String v){currentRevisionId=v;}
    public String getSubmittedRevisionId(){return submittedRevisionId;} public void setSubmittedRevisionId(String v){submittedRevisionId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Instant getFirstSubmittedAt(){return firstSubmittedAt;} public void setFirstSubmittedAt(Instant v){firstSubmittedAt=v;}
    public Instant getLastSubmittedAt(){return lastSubmittedAt;} public void setLastSubmittedAt(Instant v){lastSubmittedAt=v;}
}
