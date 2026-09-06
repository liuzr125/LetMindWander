package com.zhixing.model;

import java.time.Instant;

public class AuthResult {
    private String stage;
    private String accessToken;
    private Instant expiresAt;
    private String nextPage;
    private UserView user;

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getNextPage() { return nextPage; }
    public void setNextPage(String nextPage) { this.nextPage = nextPage; }
    public UserView getUser() { return user; }
    public void setUser(UserView user) { this.user = user; }
}
