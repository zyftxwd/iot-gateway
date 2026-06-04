package com.iot.backend.dto;

public class LoginResponse {
    private String token;
    private CurrentUserInfo user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public CurrentUserInfo getUser() {
        return user;
    }

    public void setUser(CurrentUserInfo user) {
        this.user = user;
    }
}
