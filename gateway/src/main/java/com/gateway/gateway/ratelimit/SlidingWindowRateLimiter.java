package com.gateway.gateway.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class SlidingWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> redisScript;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setLocation(new ClassPathResource("rate_limit.lua"));
        this.redisScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult isAllowed(String apiKey, int limit, int windowSeconds) {
        String key = "ratelimit:sliding:" + apiKey;
        
        long now = Instant.now().getEpochSecond();
        long nowMs = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;
        String requestId = UUID.randomUUID().toString();

        // Cast to List<Long> safely since Lua returns numbers
        @SuppressWarnings("unchecked")
        List<Long> results = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(nowMs),
                String.valueOf(windowMs),
                String.valueOf(limit),
                requestId
        );

        if (results == null || results.size() != 2) {
            return new RateLimitResult(false, limit, 0, now + windowSeconds);
        }

        boolean allowed = results.get(0) == 1L;
        int currentCount = results.get(1).intValue();
        int remaining = Math.max(0, limit - currentCount);
        
        // In a true sliding window, there isn't a single reset time, but we can estimate the maximum wait
        long resetTimeEpochSeconds = now + windowSeconds;

        return new RateLimitResult(allowed, limit, remaining, resetTimeEpochSeconds);
    }
}
