package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** MyBatis-Plus 映射 app_user（仅本模块查询所需字段）。 */
@TableName("app_user")
public class AppUserEntity {
    @TableId
    private String id;
    private String nickname;
    private String mobile;
    @TableField("avatar_url")
    private String avatarUrl;
    private String status;
    @TableField("current_plan_id")
    private String currentPlanId;
    @TableField("short_id")
    private String shortId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentPlanId() { return currentPlanId; }
    public void setCurrentPlanId(String currentPlanId) { this.currentPlanId = currentPlanId; }
    public String getShortId() { return shortId; }
    public void setShortId(String shortId) { this.shortId = shortId; }
}
