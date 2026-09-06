package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("task_event")
public class TaskEventEntity {
    @TableId private String id; @TableField("owner_id") private String ownerId; @TableField("task_id") private String taskId;
    @TableField("task_version") private Integer taskVersion; @TableField("event_type") private String eventType; @TableField("actor_type") private String actorType;
    @TableField("from_status") private String fromStatus; @TableField("to_status") private String toStatus; @TableField("occurred_at") private Instant occurredAt; private String reason;
    public String getId(){return id;} public void setId(String v){id=v;} public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;} public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public Integer getTaskVersion(){return taskVersion;} public void setTaskVersion(Integer v){taskVersion=v;} public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public String getActorType(){return actorType;} public void setActorType(String v){actorType=v;}
    public String getFromStatus(){return fromStatus;} public void setFromStatus(String v){fromStatus=v;} public String getToStatus(){return toStatus;} public void setToStatus(String v){toStatus=v;} public Instant getOccurredAt(){return occurredAt;} public void setOccurredAt(Instant v){occurredAt=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;}
}
