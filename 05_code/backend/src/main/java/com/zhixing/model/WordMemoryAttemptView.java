package com.zhixing.model;

public class WordMemoryAttemptView {
    private String attemptId,episodeId,result;
    private Integer attemptNo,sessionVersion;
    private Boolean correct,firstAttempt,canRetry,nextEpisodeAvailable;
    public String getAttemptId(){return attemptId;} public void setAttemptId(String v){attemptId=v;}
    public String getEpisodeId(){return episodeId;} public void setEpisodeId(String v){episodeId=v;}
    public String getResult(){return result;} public void setResult(String v){result=v;}
    public Integer getAttemptNo(){return attemptNo;} public void setAttemptNo(Integer v){attemptNo=v;}
    public Integer getSessionVersion(){return sessionVersion;} public void setSessionVersion(Integer v){sessionVersion=v;}
    public Boolean getCorrect(){return correct;} public void setCorrect(Boolean v){correct=v;}
    public Boolean getFirstAttempt(){return firstAttempt;} public void setFirstAttempt(Boolean v){firstAttempt=v;}
    public Boolean getCanRetry(){return canRetry;} public void setCanRetry(Boolean v){canRetry=v;}
    public Boolean getNextEpisodeAvailable(){return nextEpisodeAvailable;} public void setNextEpisodeAvailable(Boolean v){nextEpisodeAvailable=v;}
}
