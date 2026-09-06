package com.zhixing.model;

import java.util.List;
import java.util.Map;

/**
 * F02 完整个人资料视图。手机号仅掩码返回；profileVisibility 为字段级可见范围。
 */
public class ProfileView {
    private Integer rowVersion;
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer value) { rowVersion = value; }
    private String id;
    private String shortId;
    private String nickname;
    private String avatarUrl;
    private String mobileMasked;
    private String status;
    private String currentPlanId;
    private String realName;
    private String englishName;
    private String birthday;
    private Integer gender;
    private List<String> hobbies;
    private String introduction;
    private String visibility;
    /** 字段级可见范围：real_name/english_name/birthday/gender/hobbies/introduction → private/friends/public */
    private Map<String, String> profileVisibility;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getShortId() { return shortId; }
    public void setShortId(String shortId) { this.shortId = shortId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getMobileMasked() { return mobileMasked; }
    public void setMobileMasked(String mobileMasked) { this.mobileMasked = mobileMasked; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentPlanId() { return currentPlanId; }
    public void setCurrentPlanId(String currentPlanId) { this.currentPlanId = currentPlanId; }
    public boolean isHasCurrentPlan() { return currentPlanId != null && !currentPlanId.isEmpty(); }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public List<String> getHobbies() { return hobbies; }
    public void setHobbies(List<String> hobbies) { this.hobbies = hobbies; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Map<String, String> getProfileVisibility() { return profileVisibility; }
    public void setProfileVisibility(Map<String, String> profileVisibility) { this.profileVisibility = profileVisibility; }
}
