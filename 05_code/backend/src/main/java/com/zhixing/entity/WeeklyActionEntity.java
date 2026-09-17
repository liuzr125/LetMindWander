package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;

@TableName("weekly_action")
public class WeeklyActionEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("summary_id") private String summaryId;
    @TableField("action_no") private Integer actionNo;
    private String title;
    private String note;
    @TableField("scheduled_date") private LocalDate scheduledDate;
    @TableField("estimated_minutes") private Integer estimatedMinutes;
    private String state;
    @TableField("confirmed_at") private Instant confirmedAt;
    @TableField("version_no") private Integer versionNo;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public String getSummaryId(){return summaryId;} public void setSummaryId(String v){summaryId=v;}
    public Integer getActionNo(){return actionNo;} public void setActionNo(Integer v){actionNo=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public LocalDate getScheduledDate(){return scheduledDate;} public void setScheduledDate(LocalDate v){scheduledDate=v;}
    public Integer getEstimatedMinutes(){return estimatedMinutes;} public void setEstimatedMinutes(Integer v){estimatedMinutes=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public Instant getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(Instant v){confirmedAt=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
}
