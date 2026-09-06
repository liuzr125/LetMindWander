package com.zhixing.dto;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
public class TaskEventRequest { @NotBlank private String eventType; @NotNull private Integer expectedVersion; private String reason; public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} }
