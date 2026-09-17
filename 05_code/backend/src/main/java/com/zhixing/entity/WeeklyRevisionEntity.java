package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("weekly_revision")
public class WeeklyRevisionEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("summary_id") private String summaryId;
    @TableField("revision_no") private Integer revisionNo;
    @TableField("metrics_json") private String metricsJson;
    private String body;
    @TableField("sources_json") private String sourcesJson;
    @TableField("source_fingerprint") private byte[] sourceFingerprint;
    @TableField("ai_job_id") private String aiJobId;
    @TableField("confirmed_at") private Instant confirmedAt;
    @TableField("invalidated_at") private Instant invalidatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getSummaryId(){return summaryId;} public void setSummaryId(String v){summaryId=v;}
    public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer v){revisionNo=v;}
    public String getMetricsJson(){return metricsJson;} public void setMetricsJson(String v){metricsJson=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getSourcesJson(){return sourcesJson;} public void setSourcesJson(String v){sourcesJson=v;}
    public byte[] getSourceFingerprint(){return sourceFingerprint;} public void setSourceFingerprint(byte[] v){sourceFingerprint=v;}
    public String getAiJobId(){return aiJobId;} public void setAiJobId(String v){aiJobId=v;}
    public Instant getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(Instant v){confirmedAt=v;}
    public Instant getInvalidatedAt(){return invalidatedAt;} public void setInvalidatedAt(Instant v){invalidatedAt=v;}
}
