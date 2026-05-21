package com.example.haeun.controller;

import com.example.haeun.model.LoginRequest;
import com.example.haeun.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class AuthController {

    private static final String SESSION_COOKIE = "SESSION_ID";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(@CookieValue(name = SESSION_COOKIE, required = false) String sessionId) {
        if (sessionId != null && authService.getUsername(sessionId) != null) {
            return "redirect:/home";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute LoginRequest request,
                        HttpServletResponse response,
                        Model model) {
        String sessionId = authService.login(request.getUsername(), request.getPassword());
        if (sessionId == null) {
            model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return "login";
        }
        Cookie cookie = new Cookie(SESSION_COOKIE, sessionId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(@CookieValue(name = SESSION_COOKIE, required = false) String sessionId,
                       Model model) {
        if (sessionId == null) {
            return "redirect:/login";
        }
        String username = authService.getUsername(sessionId);
        if (username == null) {
            return "redirect:/login";
        }
        model.addAttribute("username", username);
        return "home";
    }

    @PostMapping("/api/heartbeat")
    @ResponseBody
    public Map<String, Object> heartbeat(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionId,
            @RequestParam(name = "active", defaultValue = "false") boolean active) {
        boolean alive = active ? authService.recordActivity(sessionId) : authService.isAlive(sessionId);
        return Map.of("alive", alive);
    }

    @PostMapping("/logout")
    public String logout(@CookieValue(name = SESSION_COOKIE, required = false) String sessionId,
                         HttpServletResponse response) {
        authService.logout(sessionId);
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/login";
    }
}
