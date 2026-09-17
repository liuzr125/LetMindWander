package com.zhixing.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("resource_schedule")
public class ResourceScheduleEntity {
 @TableId private String id; @TableField("owner_id") private String ownerId; private String name;
 @TableField("resource_kind") private String resourceKind; private String keywords;
 @TableField("weekdays_mask") private Integer weekdaysMask; @TableField("minute_of_day") private Integer minuteOfDay;
 private String timezone; @TableField("per_run_limit") private Integer perRunLimit; private String state;
 @TableField("next_run_at") private Instant nextRunAt; @TableField("version_no") private Integer versionNo;
 @TableField("dedup_enabled") private Integer dedupEnabled; @TableField("summary_enabled") private Integer summaryEnabled;
 @TableField("topics_json") private String topicsJson;
 public String getId(){return id;}public void setId(String v){id=v;}public String getOwnerId(){return ownerId;}public void setOwnerId(String v){ownerId=v;}
 public String getName(){return name;}public void setName(String v){name=v;}public String getResourceKind(){return resourceKind;}public void setResourceKind(String v){resourceKind=v;}
 public String getKeywords(){return keywords;}public void setKeywords(String v){keywords=v;}public Integer getWeekdaysMask(){return weekdaysMask;}public void setWeekdaysMask(Integer v){weekdaysMask=v;}
 public Integer getMinuteOfDay(){return minuteOfDay;}public void setMinuteOfDay(Integer v){minuteOfDay=v;}public String getTimezone(){return timezone;}public void setTimezone(String v){timezone=v;}
 public Integer getPerRunLimit(){return perRunLimit;}public void setPerRunLimit(Integer v){perRunLimit=v;}public String getState(){return state;}public void setState(String v){state=v;}
 public Instant getNextRunAt(){return nextRunAt;}public void setNextRunAt(Instant v){nextRunAt=v;}public Integer getVersionNo(){return versionNo;}public void setVersionNo(Integer v){versionNo=v;}
 public Integer getDedupEnabled(){return dedupEnabled;}public void setDedupEnabled(Integer v){dedupEnabled=v;}public Integer getSummaryEnabled(){return summaryEnabled;}public void setSummaryEnabled(Integer v){summaryEnabled=v;}
 public String getTopicsJson(){return topicsJson;}public void setTopicsJson(String v){topicsJson=v;}
}
