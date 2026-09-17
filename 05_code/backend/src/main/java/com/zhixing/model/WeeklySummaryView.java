package com.zhixing.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class WeeklySummaryView {
    private String id,currentRevisionId,confirmedRevisionId,status,body,gainText,blockerText;
    private LocalDate weekStart,weekEnd;
    private Integer versionNo,revisionNo;
    private Boolean stale,hasData,hasConfirmedVersion;
    private Map<String,Object> metrics;
    private List<GrowthDayView> days;
    private List<ActionView> actions;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getCurrentRevisionId(){return currentRevisionId;} public void setCurrentRevisionId(String v){currentRevisionId=v;}
    public String getConfirmedRevisionId(){return confirmedRevisionId;} public void setConfirmedRevisionId(String v){confirmedRevisionId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getGainText(){return gainText;} public void setGainText(String v){gainText=v;}
    public String getBlockerText(){return blockerText;} public void setBlockerText(String v){blockerText=v;}
    public LocalDate getWeekStart(){return weekStart;} public void setWeekStart(LocalDate v){weekStart=v;}
    public LocalDate getWeekEnd(){return weekEnd;} public void setWeekEnd(LocalDate v){weekEnd=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Integer getRevisionNo(){return revisionNo;} public void setRevisionNo(Integer v){revisionNo=v;}
    public Boolean getStale(){return stale;} public void setStale(Boolean v){stale=v;}
    public Boolean getHasData(){return hasData;} public void setHasData(Boolean v){hasData=v;}
    public Boolean getHasConfirmedVersion(){return hasConfirmedVersion;} public void setHasConfirmedVersion(Boolean v){hasConfirmedVersion=v;}
    public Map<String,Object> getMetrics(){return metrics;} public void setMetrics(Map<String,Object> v){metrics=v;}
    public List<GrowthDayView> getDays(){return days;} public void setDays(List<GrowthDayView> v){days=v;}
    public List<ActionView> getActions(){return actions;} public void setActions(List<ActionView> v){actions=v;}
}
