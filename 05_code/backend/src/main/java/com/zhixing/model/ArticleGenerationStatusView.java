package com.zhixing.model;

import java.time.Instant;

public class ArticleGenerationStatusView {
    private boolean enabled;
    private String cronExpression, timezone;
    private int perBookCount, eligibleBookCount, availableTopicCount;
    private Instant nextRunAt;
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getCronExpression(){return cronExpression;} public void setCronExpression(String v){cronExpression=v;}
    public String getTimezone(){return timezone;} public void setTimezone(String v){timezone=v;}
    public int getPerBookCount(){return perBookCount;} public void setPerBookCount(int v){perBookCount=v;}
    public int getEligibleBookCount(){return eligibleBookCount;} public void setEligibleBookCount(int v){eligibleBookCount=v;}
    public int getAvailableTopicCount(){return availableTopicCount;} public void setAvailableTopicCount(int v){availableTopicCount=v;}
    public Instant getNextRunAt(){return nextRunAt;} public void setNextRunAt(Instant v){nextRunAt=v;}
}
