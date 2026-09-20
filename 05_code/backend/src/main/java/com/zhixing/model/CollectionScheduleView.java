package com.zhixing.model;

import java.time.Instant;

public class CollectionScheduleView {
    private String id, name, cronExpression, timezone, state;
    private int perRunLimit;
    private Instant lastRunAt, nextRunAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getCronExpression(){return cronExpression;} public void setCronExpression(String v){cronExpression=v;}
    public String getTimezone(){return timezone;} public void setTimezone(String v){timezone=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public int getPerRunLimit(){return perRunLimit;} public void setPerRunLimit(int v){perRunLimit=v;}
    public Instant getLastRunAt(){return lastRunAt;} public void setLastRunAt(Instant v){lastRunAt=v;}
    public Instant getNextRunAt(){return nextRunAt;} public void setNextRunAt(Instant v){nextRunAt=v;}
}
