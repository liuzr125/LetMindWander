package com.zhixing.entity;

public class JournalRevisionEntity {
    private String id, ownerId, journalId, doneText, blockerText, learnedText, nextStepText, saveKind;
    private Integer revisionNo;
    private byte[] contentHash;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getJournalId(){return journalId;} public void setJournalId(String v){journalId=v;}
    public String getDoneText(){return doneText;} public void setDoneText(String v){doneText=v;}
    public String getBlockerText(){return blockerText;} public void setBlockerText(String v){blockerText=v;}
    public String getLearnedText(){return learnedText;} public void setLearnedText(String v){learnedText=v;}
    public String getNextStepText(){return nextStepText;} public void setNextStepText(String v){nextStepText=v;}
    public String getSaveKind(){return saveKind;} public void setSaveKind(String v){saveKind=v;}
    public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer v){revisionNo=v;}
    public byte[] getContentHash(){return contentHash;} public void setContentHash(byte[] v){contentHash=v;}
}
