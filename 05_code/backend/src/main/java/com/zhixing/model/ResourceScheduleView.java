package com.zhixing.model;
import java.time.Instant;import java.util.*;
public class ResourceScheduleView {
 private String id,name,resourceKind,keywords,state,timezone,lastRunState,topicsJson;private Integer weekdaysMask,minuteOfDay,perRunLimit,versionNo,lastResultCount;
 private Boolean dedupEnabled,summaryEnabled;private Instant nextRunAt,lastRunAt;private List<String> topics=new ArrayList<String>(),sourceIds=new ArrayList<String>(),sourceNames=new ArrayList<String>();
 public String getId(){return id;}public void setId(String v){id=v;}public String getName(){return name;}public void setName(String v){name=v;}public String getResourceKind(){return resourceKind;}public void setResourceKind(String v){resourceKind=v;}
 public String getKeywords(){return keywords;}public void setKeywords(String v){keywords=v;}public String getState(){return state;}public void setState(String v){state=v;}public String getTimezone(){return timezone;}public void setTimezone(String v){timezone=v;}
 public Integer getWeekdaysMask(){return weekdaysMask;}public void setWeekdaysMask(Integer v){weekdaysMask=v;}public Integer getMinuteOfDay(){return minuteOfDay;}public void setMinuteOfDay(Integer v){minuteOfDay=v;}public Integer getPerRunLimit(){return perRunLimit;}public void setPerRunLimit(Integer v){perRunLimit=v;}
 public Integer getVersionNo(){return versionNo;}public void setVersionNo(Integer v){versionNo=v;}public Boolean getDedupEnabled(){return dedupEnabled;}public void setDedupEnabled(Boolean v){dedupEnabled=v;}public Boolean getSummaryEnabled(){return summaryEnabled;}public void setSummaryEnabled(Boolean v){summaryEnabled=v;}
 public Instant getNextRunAt(){return nextRunAt;}public void setNextRunAt(Instant v){nextRunAt=v;}public String getLastRunState(){return lastRunState;}public void setLastRunState(String v){lastRunState=v;}public Integer getLastResultCount(){return lastResultCount;}public void setLastResultCount(Integer v){lastResultCount=v;}public Instant getLastRunAt(){return lastRunAt;}public void setLastRunAt(Instant v){lastRunAt=v;}
 public List<String> getTopics(){return topics;}public void setTopics(List<String> v){topics=v;}public List<String> getSourceIds(){return sourceIds;}public void setSourceIds(List<String> v){sourceIds=v;}public List<String> getSourceNames(){return sourceNames;}public void setSourceNames(List<String> v){sourceNames=v;}
 @com.fasterxml.jackson.annotation.JsonIgnore public String getTopicsJson(){return topicsJson;} public void setTopicsJson(String v){topicsJson=v;}
}
