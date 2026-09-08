package com.zhixing.model;

public class KnowledgeFriendView {
    private String id,nickname,avatarUrl,relationId;
    private Integer relationGeneration;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getNickname(){return nickname;} public void setNickname(String v){nickname=v;}
    public String getAvatarUrl(){return avatarUrl;} public void setAvatarUrl(String v){avatarUrl=v;}
    public String getRelationId(){return relationId;} public void setRelationId(String v){relationId=v;}
    public Integer getRelationGeneration(){return relationGeneration;} public void setRelationGeneration(Integer v){relationGeneration=v;}
}
