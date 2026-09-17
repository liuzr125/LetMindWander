package com.zhixing.dto;

public class WordMemoryAttemptRequest {
    private String answer;
    private Integer durationMs, expectedVersion;
    public String getAnswer(){return answer;} public void setAnswer(String v){answer=v;}
    public Integer getDurationMs(){return durationMs;} public void setDurationMs(Integer v){durationMs=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
