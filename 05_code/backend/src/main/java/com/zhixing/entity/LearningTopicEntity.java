package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("learning_topic")
public class LearningTopicEntity {
    @TableId private String id;
    @TableField("scope_key") private String scopeKey;
    @TableField("owner_id") private String ownerId;
    private String name;
    @TableField("normalized_name") private String normalizedName;
    private String state;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getScopeKey() { return scopeKey; } public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getOwnerId() { return ownerId; } public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; } public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getState() { return state; } public void setState(String state) { this.state = state; }
}
