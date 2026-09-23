package com.gateway.gateway.ratelimit;

public interface RateLimiter {
    RateLimitResult isAllowed(String apiKey, int limit, int windowSeconds);
}
