package com.zhixing.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class AdminTechnicalContentView {
    private String contentId,versionId,title,summary,body,difficulty,reviewStatus;
    private String sourceName,sourceType,sourceUrl,originUrl,originAuthor,licenseSnapshot;
    private Integer versionNo,estimatedSeconds;
    private Instant publishedAt,originPublishedAt,versionCreatedAt;
    private List<String> topics=Collections.emptyList();
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getVersionId(){return versionId;} public void setVersionId(String v){versionId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getDifficulty(){return difficulty;} public void setDifficulty(String v){difficulty=v;}
    public String getReviewStatus(){return reviewStatus;} public void setReviewStatus(String v){reviewStatus=v;}
    public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
    public String getOriginUrl(){return originUrl;} public void setOriginUrl(String v){originUrl=v;}
    public String getOriginAuthor(){return originAuthor;} public void setOriginAuthor(String v){originAuthor=v;}
    public String getLicenseSnapshot(){return licenseSnapshot;} public void setLicenseSnapshot(String v){licenseSnapshot=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Integer getEstimatedSeconds(){return estimatedSeconds;} public void setEstimatedSeconds(Integer v){estimatedSeconds=v;}
    public Instant getPublishedAt(){return publishedAt;} public void setPublishedAt(Instant v){publishedAt=v;}
    public Instant getOriginPublishedAt(){return originPublishedAt;} public void setOriginPublishedAt(Instant v){originPublishedAt=v;}
    public Instant getVersionCreatedAt(){return versionCreatedAt;} public void setVersionCreatedAt(Instant v){versionCreatedAt=v;}
    public List<String> getTopics(){return topics;} public void setTopics(List<String> v){topics=v==null?Collections.<String>emptyList():v;}
}
