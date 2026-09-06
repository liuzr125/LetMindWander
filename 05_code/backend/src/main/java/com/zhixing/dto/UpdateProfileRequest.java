package com.zhixing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * F02 个人资料更新请求。手机号不在可编辑字段内（微信授权后只读）。
 */
public class UpdateProfileRequest {
    @NotBlank(message = "昵称不能为空")
    private String nickname;

    private String realName;

    private String englishName;

    /** YYYY-MM-DD，可空；不得晚于今天 */
    private String birthday;

    /** 0 未设置 / 1 男 / 2 女 / 3 其他 */
    private Integer gender;

    /** 最多 10 项，每项 ≤20 字符，去重、去空值 */
    private java.util.List<String> hobbies;

    private String introduction;

    /** private / friends / public；空则缺省 private */
    private String visibility;

    /** 头像 HTTPS 地址；仅指向本服务 media_asset 或微信官方头像域 */
    private String avatarUrl;
    private Integer rowVersion;
    private java.util.Map<String, String> profileVisibility;
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer value) { rowVersion = value; }
    public java.util.Map<String, String> getProfileVisibility() { return profileVisibility; }
    public void setProfileVisibility(java.util.Map<String, String> value) { profileVisibility = value; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getEnglishName() { return englishName; }
    public void setEnglishName(String englishName) { this.englishName = englishName; }
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public Integer getGender() { return gender; }
    public void setGender(Integer gender) { this.gender = gender; }
    public java.util.List<String> getHobbies() { return hobbies; }
    public void setHobbies(java.util.List<String> hobbies) { this.hobbies = hobbies; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
