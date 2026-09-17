package com.zhixing.model;

import java.time.LocalDate;

public class JournalHighlightView {
    private String journalId,learnedText,blockerText;
    private Integer revisionNo;
    private LocalDate businessDate;
    public String getJournalId(){return journalId;} public void setJournalId(String v){journalId=v;}
    public String getLearnedText(){return learnedText;} public void setLearnedText(String v){learnedText=v;}
    public String getBlockerText(){return blockerText;} public void setBlockerText(String v){blockerText=v;}
    public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer v){revisionNo=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
}
