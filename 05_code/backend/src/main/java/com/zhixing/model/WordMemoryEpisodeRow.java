package com.zhixing.model;

public class WordMemoryEpisodeRow {
    private String id,sessionId,contentId,contentVersionId,senseId,questionId,dimension,state,firstResult,finalResult;
    private String promptText,expectedAnswer,acceptedAnswersJson,answerPolicy,hintText,wordTerm,meaning;
    private Integer questionVersion,positionNo,hintUsed,answerRevealed,attemptCount;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;}
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getContentVersionId(){return contentVersionId;} public void setContentVersionId(String v){contentVersionId=v;}
    public String getSenseId(){return senseId;} public void setSenseId(String v){senseId=v;}
    public String getQuestionId(){return questionId;} public void setQuestionId(String v){questionId=v;}
    public String getDimension(){return dimension;} public void setDimension(String v){dimension=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getFirstResult(){return firstResult;} public void setFirstResult(String v){firstResult=v;}
    public String getFinalResult(){return finalResult;} public void setFinalResult(String v){finalResult=v;}
    public String getPromptText(){return promptText;} public void setPromptText(String v){promptText=v;}
    public String getExpectedAnswer(){return expectedAnswer;} public void setExpectedAnswer(String v){expectedAnswer=v;}
    public String getAcceptedAnswersJson(){return acceptedAnswersJson;} public void setAcceptedAnswersJson(String v){acceptedAnswersJson=v;}
    public String getAnswerPolicy(){return answerPolicy;} public void setAnswerPolicy(String v){answerPolicy=v;}
    public String getHintText(){return hintText;} public void setHintText(String v){hintText=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public Integer getQuestionVersion(){return questionVersion;} public void setQuestionVersion(Integer v){questionVersion=v;}
    public Integer getPositionNo(){return positionNo;} public void setPositionNo(Integer v){positionNo=v;}
    public Integer getHintUsed(){return hintUsed;} public void setHintUsed(Integer v){hintUsed=v;}
    public Integer getAnswerRevealed(){return answerRevealed;} public void setAnswerRevealed(Integer v){answerRevealed=v;}
    public Integer getAttemptCount(){return attemptCount;} public void setAttemptCount(Integer v){attemptCount=v;}
}
