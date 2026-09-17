package com.zhixing.model;
public class FriendView {
 private String id,shortId,nickname,avatarUrl,relationId; private Integer sharedByMeCount,sharedByFriendCount;
 public String getId(){return id;} public void setId(String v){id=v;} public String getShortId(){return shortId;} public void setShortId(String v){shortId=v;}
 public String getNickname(){return nickname;} public void setNickname(String v){nickname=v;} public String getAvatarUrl(){return avatarUrl;} public void setAvatarUrl(String v){avatarUrl=v;}
 public String getRelationId(){return relationId;} public void setRelationId(String v){relationId=v;} public Integer getSharedByMeCount(){return sharedByMeCount;} public void setSharedByMeCount(Integer v){sharedByMeCount=v;}
 public Integer getSharedByFriendCount(){return sharedByFriendCount;} public void setSharedByFriendCount(Integer v){sharedByFriendCount=v;}
}
