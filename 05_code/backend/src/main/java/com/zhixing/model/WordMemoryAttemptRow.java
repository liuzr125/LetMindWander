package com.zhixing.model;

public class WordMemoryAttemptRow {
    private String id,ownerId,sessionId,episodeId,verdict,resultType,idempotencyKey;
    private Integer attemptNo,hintUsed,firstAttempt,durationMs;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;}
    public String getEpisodeId(){return episodeId;} public void setEpisodeId(String v){episodeId=v;}
    public String getVerdict(){return verdict;} public void setVerdict(String v){verdict=v;}
    public String getResultType(){return resultType;} public void setResultType(String v){resultType=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public Integer getAttemptNo(){return attemptNo;} public void setAttemptNo(Integer v){attemptNo=v;}
    public Integer getHintUsed(){return hintUsed;} public void setHintUsed(Integer v){hintUsed=v;}
    public Integer getFirstAttempt(){return firstAttempt;} public void setFirstAttempt(Integer v){firstAttempt=v;}
    public Integer getDurationMs(){return durationMs;} public void setDurationMs(Integer v){durationMs=v;}
}
