package com.zhixing.model;

public class UserView {
    private String id;
    private String shortId;
    private String nickname;
    private String avatarUrl;
    private String mobileMasked;
    private String status;
    private String currentPlanId;

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
}
