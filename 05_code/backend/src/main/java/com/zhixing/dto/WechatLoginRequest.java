package com.zhixing.dto;

import javax.validation.constraints.NotBlank;

public class WechatLoginRequest {
    @NotBlank(message = "微信登录凭证不能为空")
    private String code;
    private String inviteCode;
    @NotBlank(message = "请先同意隐私协议")
    private String privacyVersion;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public String getPrivacyVersion() { return privacyVersion; }
    public void setPrivacyVersion(String privacyVersion) { this.privacyVersion = privacyVersion; }
}
