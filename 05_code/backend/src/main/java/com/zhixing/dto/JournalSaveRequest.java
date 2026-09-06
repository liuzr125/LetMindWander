package com.zhixing.dto;

public class JournalSaveRequest {
    private Integer expectedVersion;
    private String doneText, blockerText, learnedText, nextStepText;
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
    public String getDoneText(){return doneText;} public void setDoneText(String v){doneText=v;}
    public String getBlockerText(){return blockerText;} public void setBlockerText(String v){blockerText=v;}
    public String getLearnedText(){return learnedText;} public void setLearnedText(String v){learnedText=v;}
    public String getNextStepText(){return nextStepText;} public void setNextStepText(String v){nextStepText=v;}
}
