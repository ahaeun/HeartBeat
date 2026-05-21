package com.example.haeun.service;

public interface AuthService {

    String login(String username, String password);

    boolean recordActivity(String sessionId);

    boolean isAlive(String sessionId);

    String getUsername(String sessionId);

    void logout(String sessionId);
}
