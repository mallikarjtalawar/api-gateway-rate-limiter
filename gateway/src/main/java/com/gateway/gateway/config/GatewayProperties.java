package com.gateway.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gateway.upstream")
public class GatewayProperties {
    private String url;
    private int connectTimeoutMs = 500;
    private int responseTimeoutMs = 2000;
    private int maxConnections = 500;
    private int maxPerRoute = 200;
    private int redisDownGracePeriodSecs = 5;
    private String adminSecret = "super-secret-admin-token";

    public String getAdminSecret() {
        return adminSecret;
    }

    public void setAdminSecret(String adminSecret) {
        this.adminSecret = adminSecret;
    }

    public int getRedisDownGracePeriodSecs() {
        return redisDownGracePeriodSecs;
    }

    public void setRedisDownGracePeriodSecs(int redisDownGracePeriodSecs) {
        this.redisDownGracePeriodSecs = redisDownGracePeriodSecs;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public void setResponseTimeoutMs(int responseTimeoutMs) {
        this.responseTimeoutMs = responseTimeoutMs;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxPerRoute() {
        return maxPerRoute;
    }

    public void setMaxPerRoute(int maxPerRoute) {
        this.maxPerRoute = maxPerRoute;
    }
}
