package com.zhixing.model;

import com.zhixing.dto.KnowledgeProblemFields;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeDetailView {
    private String id,itemType,title,body,learningStatus,verificationStatus,visibility,state,sourceContentId,sourceContentVersionId,noteParentId,sourceTitle,sourceSummary;
    private Integer versionNo;
    private Boolean inReview;
    private LocalDate lastVerifiedDate;
    private Instant createdAt,updatedAt;
    private KnowledgeProblemFields problem;
    private List<String> tags=new ArrayList<String>(),selectedFriendIds=new ArrayList<String>();
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getItemType(){return itemType;} public void setItemType(String v){itemType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getLearningStatus(){return learningStatus;} public void setLearningStatus(String v){learningStatus=v;}
    public String getVerificationStatus(){return verificationStatus;} public void setVerificationStatus(String v){verificationStatus=v;}
    public String getVisibility(){return visibility;} public void setVisibility(String v){visibility=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getSourceContentId(){return sourceContentId;} public void setSourceContentId(String v){sourceContentId=v;}
    public String getSourceContentVersionId(){return sourceContentVersionId;} public void setSourceContentVersionId(String v){sourceContentVersionId=v;}
    public String getNoteParentId(){return noteParentId;} public void setNoteParentId(String v){noteParentId=v;}
    public String getSourceTitle(){return sourceTitle;} public void setSourceTitle(String v){sourceTitle=v;}
    public String getSourceSummary(){return sourceSummary;} public void setSourceSummary(String v){sourceSummary=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Boolean getInReview(){return inReview;} public void setInReview(Boolean v){inReview=v;}
    public LocalDate getLastVerifiedDate(){return lastVerifiedDate;} public void setLastVerifiedDate(LocalDate v){lastVerifiedDate=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public KnowledgeProblemFields getProblem(){return problem;} public void setProblem(KnowledgeProblemFields v){problem=v;}
    public List<String> getTags(){return tags;} public void setTags(List<String> v){tags=v;}
    public List<String> getSelectedFriendIds(){return selectedFriendIds;} public void setSelectedFriendIds(List<String> v){selectedFriendIds=v;}
}
