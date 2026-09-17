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
    @TableField("real_name") private String realName;
    @TableField("english_name") private String englishName;
    private java.sql.Date birthday;
    private Integer gender;
    @TableField("hobbies_json") private String hobbiesJson;
    private String introduction;
    @TableField("profile_visibility") private String profileVisibility;
    @TableField("row_version") private Integer rowVersion;
    @TableField("ai_consent_version") private String aiConsentVersion;
    @TableField("ai_consented_at") private java.time.Instant aiConsentedAt;

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
    public String getRealName() { return realName; } public void setRealName(String v) { realName=v; }
    public String getEnglishName() { return englishName; } public void setEnglishName(String v) { englishName=v; }
    public java.sql.Date getBirthday() { return birthday; } public void setBirthday(java.sql.Date v) { birthday=v; }
    public Integer getGender() { return gender; } public void setGender(Integer v) { gender=v; }
    public String getHobbiesJson() { return hobbiesJson; } public void setHobbiesJson(String v) { hobbiesJson=v; }
    public String getIntroduction() { return introduction; } public void setIntroduction(String v) { introduction=v; }
    public String getProfileVisibility() { return profileVisibility; } public void setProfileVisibility(String v) { profileVisibility=v; }
    public Integer getRowVersion() { return rowVersion; } public void setRowVersion(Integer v) { rowVersion=v; }
    public String getAiConsentVersion() { return aiConsentVersion; } public void setAiConsentVersion(String v) { aiConsentVersion=v; }
    public java.time.Instant getAiConsentedAt() { return aiConsentedAt; } public void setAiConsentedAt(java.time.Instant v) { aiConsentedAt=v; }
}
