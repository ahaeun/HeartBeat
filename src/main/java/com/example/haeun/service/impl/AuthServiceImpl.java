package com.example.haeun.service.impl;

import com.example.haeun.model.Session;
import com.example.haeun.model.User;
import com.example.haeun.service.AuthService;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthService {

    private static final User TEST_USER = new User("test", "qwer");
    private static final long IDLE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public String login(String username, String password) {
        if (!TEST_USER.username().equals(username) || !TEST_USER.password().equals(password)) {
            return null;
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new Session(sessionId, username, System.currentTimeMillis()));
        return sessionId;
    }

    @Override
    public boolean recordActivity(String sessionId) {
        Session session = validSession(sessionId);
        if (session == null) {
            return false;
        }
        session.setLastActivity(System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean isAlive(String sessionId) {
        return validSession(sessionId) != null;
    }

    @Override
    public String getUsername(String sessionId) {
        Session session = validSession(sessionId);
        return session != null ? session.getUsername() : null;
    }

    @Override
    public void logout(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private Session validSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (System.currentTimeMillis() - session.getLastActivity() > IDLE_TIMEOUT_MS) {
            sessions.remove(sessionId);
            return null;
        }
        return session;
    }
}
