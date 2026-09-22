package com.zhixing.model;

import java.time.Instant;

/** 管理端「用户反馈」列表行 / 详情。 */
public class AdminFeedbackView {
    private String id,ownerId,nickname,shortId,mobile,category,categoryLabel,body,excerpt,state,stateLabel,contentId,contentTitle,requestId;
    private Instant createdAt,updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getNickname(){return nickname;} public void setNickname(String v){nickname=v;}
    public String getShortId(){return shortId;} public void setShortId(String v){shortId=v;}
    public String getMobile(){return mobile;} public void setMobile(String v){mobile=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getCategoryLabel(){return categoryLabel;} public void setCategoryLabel(String v){categoryLabel=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getExcerpt(){return excerpt;} public void setExcerpt(String v){excerpt=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getStateLabel(){return stateLabel;} public void setStateLabel(String v){stateLabel=v;}
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getContentTitle(){return contentTitle;} public void setContentTitle(String v){contentTitle=v;}
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
