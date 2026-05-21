package com.example.haeun.model;

public class Session {

    private final String sessionId;
    private final String username;
    private volatile long lastActivity;

    public Session(String sessionId, String username, long lastActivity) {
        this.sessionId = sessionId;
        this.username = username;
        this.lastActivity = lastActivity;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUsername() {
        return username;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }
}
