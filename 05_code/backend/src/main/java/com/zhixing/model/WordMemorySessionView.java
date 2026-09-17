package com.zhixing.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WordMemorySessionView {
    private String sessionId,source,returnTo,taskId,state;
    private LocalDate businessDate;
    private Integer targetCount,version;
    private Boolean addToReview;
    private List<String> requiredDimensions=new ArrayList<String>();
    private WordMemoryEpisodeView currentEpisode;
    public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public String getReturnTo(){return returnTo;} public void setReturnTo(String v){returnTo=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public Integer getTargetCount(){return targetCount;} public void setTargetCount(Integer v){targetCount=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Boolean getAddToReview(){return addToReview;} public void setAddToReview(Boolean v){addToReview=v;}
    public List<String> getRequiredDimensions(){return requiredDimensions;} public void setRequiredDimensions(List<String> v){requiredDimensions=v;}
    public WordMemoryEpisodeView getCurrentEpisode(){return currentEpisode;} public void setCurrentEpisode(WordMemoryEpisodeView v){currentEpisode=v;}
}
