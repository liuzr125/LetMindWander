package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("learning_plan_topic")
public class LearningPlanTopicEntity {
    @TableId private String id;
    @TableField("plan_id") private String planId;
    @TableField("topic_id") private String topicId;
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; } public void setPlanId(String planId) { this.planId = planId; }
    public String getTopicId() { return topicId; } public void setTopicId(String topicId) { this.topicId = topicId; }
}
