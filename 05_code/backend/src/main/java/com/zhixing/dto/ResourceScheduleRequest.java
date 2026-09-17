package com.zhixing.dto;
import javax.validation.constraints.*;import java.util.*;
public class ResourceScheduleRequest {
 @NotBlank(message="任务名称不能为空") private String name;private String resourceKind,keywords,state;private Integer weekdaysMask,minuteOfDay,perRunLimit,expectedVersion;private Boolean dedupEnabled,summaryEnabled;private List<String> topics,sourceIds;
 public String getName(){return name;}public void setName(String v){name=v;}public String getResourceKind(){return resourceKind;}public void setResourceKind(String v){resourceKind=v;}public String getKeywords(){return keywords;}public void setKeywords(String v){keywords=v;}public String getState(){return state;}public void setState(String v){state=v;}
 public Integer getWeekdaysMask(){return weekdaysMask;}public void setWeekdaysMask(Integer v){weekdaysMask=v;}public Integer getMinuteOfDay(){return minuteOfDay;}public void setMinuteOfDay(Integer v){minuteOfDay=v;}public Integer getPerRunLimit(){return perRunLimit;}public void setPerRunLimit(Integer v){perRunLimit=v;}public Integer getExpectedVersion(){return expectedVersion;}public void setExpectedVersion(Integer v){expectedVersion=v;}
 public Boolean getDedupEnabled(){return dedupEnabled;}public void setDedupEnabled(Boolean v){dedupEnabled=v;}public Boolean getSummaryEnabled(){return summaryEnabled;}public void setSummaryEnabled(Boolean v){summaryEnabled=v;}public List<String> getTopics(){return topics;}public void setTopics(List<String> v){topics=v;}public List<String> getSourceIds(){return sourceIds;}public void setSourceIds(List<String> v){sourceIds=v;}
}
