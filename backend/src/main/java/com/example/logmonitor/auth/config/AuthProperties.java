package com.example.logmonitor.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private int loginBurstCapacity = 5;
    private long loginWindowSeconds = 60;
    private long refreshTokenExpirationSeconds = 604800;
    private boolean refreshCookieSecure;
    private String refreshCookieSameSite = "Strict";

    public int getLoginBurstCapacity() { return loginBurstCapacity; }
    public void setLoginBurstCapacity(int loginBurstCapacity) { this.loginBurstCapacity = loginBurstCapacity; }

    public long getLoginWindowSeconds() { return loginWindowSeconds; }
    public void setLoginWindowSeconds(long loginWindowSeconds) { this.loginWindowSeconds = loginWindowSeconds; }

    public long getRefreshTokenExpirationSeconds() { return refreshTokenExpirationSeconds; }
    public void setRefreshTokenExpirationSeconds(long refreshTokenExpirationSeconds) {
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }

    public String getRefreshCookieSameSite() { return refreshCookieSameSite; }
    public void setRefreshCookieSameSite(String refreshCookieSameSite) {
        this.refreshCookieSameSite = refreshCookieSameSite;
    }
}
