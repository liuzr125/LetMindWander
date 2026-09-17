package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;

@TableName("weekly_summary")
public class WeeklySummaryEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("week_start") private LocalDate weekStart;
    @TableField("current_revision_id") private String currentRevisionId;
    @TableField("confirmed_revision_id") private String confirmedRevisionId;
    @TableField("source_fingerprint") private byte[] sourceFingerprint;
    @TableField("is_stale") private Integer stale;
    @TableField("version_no") private Integer versionNo;
    @TableField("created_at") private Instant createdAt;
    @TableField("updated_at") private Instant updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public LocalDate getWeekStart(){return weekStart;} public void setWeekStart(LocalDate v){weekStart=v;}
    public String getCurrentRevisionId(){return currentRevisionId;} public void setCurrentRevisionId(String v){currentRevisionId=v;}
    public String getConfirmedRevisionId(){return confirmedRevisionId;} public void setConfirmedRevisionId(String v){confirmedRevisionId=v;}
    public byte[] getSourceFingerprint(){return sourceFingerprint;} public void setSourceFingerprint(byte[] v){sourceFingerprint=v;}
    public Integer getStale(){return stale;} public void setStale(Integer v){stale=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
