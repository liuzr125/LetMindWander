package com.zhixing.model;

import java.time.Instant;

public class RegistrationContext {
    private String wxAppId;
    private String wxOpenId;
    private String inviteCode;
    private String privacyVersion;
    private String mobile;
    private Instant expiresAt;
    private AuthResult completedResult;

    public String getWxAppId() { return wxAppId; }
    public void setWxAppId(String wxAppId) { this.wxAppId = wxAppId; }
    public String getWxOpenId() { return wxOpenId; }
    public void setWxOpenId(String wxOpenId) { this.wxOpenId = wxOpenId; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public String getPrivacyVersion() { return privacyVersion; }
    public void setPrivacyVersion(String privacyVersion) { this.privacyVersion = privacyVersion; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public AuthResult getCompletedResult() { return completedResult; }
    public void setCompletedResult(AuthResult completedResult) { this.completedResult = completedResult; }
}
