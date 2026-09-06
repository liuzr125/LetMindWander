package com.zhixing.model;

public class AuthenticatedSession {
    private final String sessionId;
    private final String userId;

    public AuthenticatedSession(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
}
