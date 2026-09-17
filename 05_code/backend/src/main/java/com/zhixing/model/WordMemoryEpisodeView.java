package com.zhixing.model;

import java.util.ArrayList;
import java.util.List;

public class WordMemoryEpisodeView {
    private String id,contentId,dimension,prompt,wordTerm,meaning,state,firstResult,finalResult;
    private Integer position,attemptCount;
    private Boolean hintUsed,answerRevealed;
    private List<String> availableHints=new ArrayList<String>();
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getDimension(){return dimension;} public void setDimension(String v){dimension=v;}
    public String getPrompt(){return prompt;} public void setPrompt(String v){prompt=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getFirstResult(){return firstResult;} public void setFirstResult(String v){firstResult=v;}
    public String getFinalResult(){return finalResult;} public void setFinalResult(String v){finalResult=v;}
    public Integer getPosition(){return position;} public void setPosition(Integer v){position=v;}
    public Integer getAttemptCount(){return attemptCount;} public void setAttemptCount(Integer v){attemptCount=v;}
    public Boolean getHintUsed(){return hintUsed;} public void setHintUsed(Boolean v){hintUsed=v;}
    public Boolean getAnswerRevealed(){return answerRevealed;} public void setAnswerRevealed(Boolean v){answerRevealed=v;}
    public List<String> getAvailableHints(){return availableHints;} public void setAvailableHints(List<String> v){availableHints=v;}
}
