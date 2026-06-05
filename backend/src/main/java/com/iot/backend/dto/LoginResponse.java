package com.iot.backend.dto;

public class LoginResponse {
    private String token;
    private Long expiresAt;
    private CurrentUserInfo user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public CurrentUserInfo getUser() {
        return user;
    }

    public void setUser(CurrentUserInfo user) {
        this.user = user;
    }
}
