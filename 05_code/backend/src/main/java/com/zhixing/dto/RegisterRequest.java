package com.zhixing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class RegisterRequest {
    @NotBlank(message = "待注册凭证不能为空")
    private String registrationTicket;
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String mobile;
    @NotBlank(message = "请输入短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "请输入6位短信验证码")
    private String smsCode;
    @NotBlank(message = "昵称不能为空")
    private String nickname;
    private String avatarUrl;
    @NotBlank(message = "请先同意隐私协议")
    private String privacyVersion;
    private boolean aiConsent;

    public String getRegistrationTicket() { return registrationTicket; }
    public void setRegistrationTicket(String registrationTicket) { this.registrationTicket = registrationTicket; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getSmsCode() { return smsCode; }
    public void setSmsCode(String smsCode) { this.smsCode = smsCode; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getPrivacyVersion() { return privacyVersion; }
    public void setPrivacyVersion(String privacyVersion) { this.privacyVersion = privacyVersion; }
    public boolean isAiConsent() { return aiConsent; }
    public void setAiConsent(boolean aiConsent) { this.aiConsent = aiConsent; }
}
