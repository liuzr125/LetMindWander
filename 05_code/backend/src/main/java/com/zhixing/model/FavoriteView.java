package com.zhixing.model;
import java.time.Instant;
public class FavoriteView {
 private String targetType,targetId,title,summary; private Instant favoritedAt;
 public String getTargetType(){return targetType;} public void setTargetType(String v){targetType=v;}
 public String getTargetId(){return targetId;} public void setTargetId(String v){targetId=v;}
 public String getTitle(){return title;} public void setTitle(String v){title=v;}
 public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
 public Instant getFavoritedAt(){return favoritedAt;} public void setFavoritedAt(Instant v){favoritedAt=v;}
}
