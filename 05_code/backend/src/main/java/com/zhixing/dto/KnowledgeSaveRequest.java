package com.zhixing.dto;

import java.util.List;

public class KnowledgeSaveRequest {
    private Integer expectedVersion;
    private String itemType, title, body, state, visibility, noteParentId;
    private KnowledgeProblemFields problem;
    private List<String> tags, selectedFriendIds;
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
    public String getItemType(){return itemType;} public void setItemType(String v){itemType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getVisibility(){return visibility;} public void setVisibility(String v){visibility=v;}
    public String getNoteParentId(){return noteParentId;} public void setNoteParentId(String v){noteParentId=v;}
    public KnowledgeProblemFields getProblem(){return problem;} public void setProblem(KnowledgeProblemFields v){problem=v;}
    public List<String> getTags(){return tags;} public void setTags(List<String> v){tags=v;}
    public List<String> getSelectedFriendIds(){return selectedFriendIds;} public void setSelectedFriendIds(List<String> v){selectedFriendIds=v;}
}
