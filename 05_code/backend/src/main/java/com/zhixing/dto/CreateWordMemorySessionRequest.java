package com.zhixing.dto;

import java.util.List;

public class CreateWordMemorySessionRequest {
    private List<String> contentIds;
    private List<String> dimensions;
    private String source, returnTo, taskId;
    private Boolean addToReview;
    public List<String> getContentIds(){return contentIds;} public void setContentIds(List<String> v){contentIds=v;}
    public List<String> getDimensions(){return dimensions;} public void setDimensions(List<String> v){dimensions=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public String getReturnTo(){return returnTo;} public void setReturnTo(String v){returnTo=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public Boolean getAddToReview(){return addToReview;} public void setAddToReview(Boolean v){addToReview=v;}
}
