package com.zhixing.model;

public class WordMemoryHintRevealView {
    private String episodeId,hintType,content;
    private Integer sessionVersion;
    private Boolean answerRevealed;
    public String getEpisodeId(){return episodeId;} public void setEpisodeId(String v){episodeId=v;}
    public String getHintType(){return hintType;} public void setHintType(String v){hintType=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public Integer getSessionVersion(){return sessionVersion;} public void setSessionVersion(Integer v){sessionVersion=v;}
    public Boolean getAnswerRevealed(){return answerRevealed;} public void setAnswerRevealed(Boolean v){answerRevealed=v;}
}
