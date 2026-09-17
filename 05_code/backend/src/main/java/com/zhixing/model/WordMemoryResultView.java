package com.zhixing.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WordMemoryResultView {
    private String sessionId,state,returnTo;
    private LocalDate businessDate;
    private Integer targetWordCount,practicedWordCount,totalEpisodes,completedEpisodes,validObjectiveCount;
    private Integer independentCorrectCount,hintedCorrectCount,retryCorrectCount,firstIncorrectCount,untestedCount,weakWordCount,sessionVersion;
    private List<WordMemoryReviewPlanView> reviewPlans=new ArrayList<WordMemoryReviewPlanView>();
    public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getReturnTo(){return returnTo;} public void setReturnTo(String v){returnTo=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;}
    public Integer getTargetWordCount(){return targetWordCount;} public void setTargetWordCount(Integer v){targetWordCount=v;}
    public Integer getPracticedWordCount(){return practicedWordCount;} public void setPracticedWordCount(Integer v){practicedWordCount=v;}
    public Integer getTotalEpisodes(){return totalEpisodes;} public void setTotalEpisodes(Integer v){totalEpisodes=v;}
    public Integer getCompletedEpisodes(){return completedEpisodes;} public void setCompletedEpisodes(Integer v){completedEpisodes=v;}
    public Integer getValidObjectiveCount(){return validObjectiveCount;} public void setValidObjectiveCount(Integer v){validObjectiveCount=v;}
    public Integer getIndependentCorrectCount(){return independentCorrectCount;} public void setIndependentCorrectCount(Integer v){independentCorrectCount=v;}
    public Integer getHintedCorrectCount(){return hintedCorrectCount;} public void setHintedCorrectCount(Integer v){hintedCorrectCount=v;}
    public Integer getRetryCorrectCount(){return retryCorrectCount;} public void setRetryCorrectCount(Integer v){retryCorrectCount=v;}
    public Integer getFirstIncorrectCount(){return firstIncorrectCount;} public void setFirstIncorrectCount(Integer v){firstIncorrectCount=v;}
    public Integer getUntestedCount(){return untestedCount;} public void setUntestedCount(Integer v){untestedCount=v;}
    public Integer getWeakWordCount(){return weakWordCount;} public void setWeakWordCount(Integer v){weakWordCount=v;}
    public Integer getSessionVersion(){return sessionVersion;} public void setSessionVersion(Integer v){sessionVersion=v;}
    public List<WordMemoryReviewPlanView> getReviewPlans(){return reviewPlans;} public void setReviewPlans(List<WordMemoryReviewPlanView> v){reviewPlans=v;}
}
