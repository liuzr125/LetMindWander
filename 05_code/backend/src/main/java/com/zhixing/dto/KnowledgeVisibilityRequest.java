package com.zhixing.dto;

import java.util.List;

public class KnowledgeVisibilityRequest {
    private Integer expectedVersion;
    private String visibility;
    private List<String> selectedFriendIds;
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
    public String getVisibility(){return visibility;} public void setVisibility(String v){visibility=v;}
    public List<String> getSelectedFriendIds(){return selectedFriendIds;} public void setSelectedFriendIds(List<String> v){selectedFriendIds=v;}
}
