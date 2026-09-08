package com.zhixing.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeListItemView {
    private String id,itemType,title,summary,learningStatus,verificationStatus,visibility,state,sourceContentId,sourceContentVersionId;
    private Integer versionNo;
    private Boolean inReview;
    private Instant updatedAt;
    private List<String> tags=new ArrayList<String>();
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getItemType(){return itemType;} public void setItemType(String v){itemType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getLearningStatus(){return learningStatus;} public void setLearningStatus(String v){learningStatus=v;}
    public String getVerificationStatus(){return verificationStatus;} public void setVerificationStatus(String v){verificationStatus=v;}
    public String getVisibility(){return visibility;} public void setVisibility(String v){visibility=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getSourceContentId(){return sourceContentId;} public void setSourceContentId(String v){sourceContentId=v;}
    public String getSourceContentVersionId(){return sourceContentVersionId;} public void setSourceContentVersionId(String v){sourceContentVersionId=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Boolean getInReview(){return inReview;} public void setInReview(Boolean v){inReview=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public List<String> getTags(){return tags;} public void setTags(List<String> v){tags=v;}
}
