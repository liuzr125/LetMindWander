package com.zhixing.model;

import java.time.Instant;
import java.time.LocalDate;

public class JournalView {
    private String journalId, state, doneText, blockerText, learnedText, nextStepText;
    private LocalDate businessDate;
    private Integer versionNo;
    private Instant savedAt, submittedAt;
    public String getJournalId(){return journalId;} public void setJournalId(String v){journalId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getDoneText(){return doneText;} public void setDoneText(String v){doneText=v;}
    public String getBlockerText(){return blockerText;} public void setBlockerText(String v){blockerText=v;}
    public String getLearnedText(){return learnedText;} public void setLearnedText(String v){learnedText=v;}
    public String getNextStepText(){return nextStepText;} public void setNextStepText(String v){nextStepText=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Instant getSavedAt(){return savedAt;} public void setSavedAt(Instant v){savedAt=v;}
    public Instant getSubmittedAt(){return submittedAt;} public void setSubmittedAt(Instant v){submittedAt=v;}
}
