package com.gateway.gateway.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class FixedWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public FixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult isAllowed(String apiKey, int limit, int windowSeconds) {
        long currentEpochSecond = Instant.now().getEpochSecond();
        long windowStart = currentEpochSecond / windowSeconds * windowSeconds;
        long windowEnd = windowStart + windowSeconds;
        
        String key = "ratelimit:fixed:" + apiKey + ":" + windowStart;
        
        // This naive implementation increments a counter for the current fixed window.
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) count = 1L;
        
        if (count == 1L) {
            redisTemplate.expire(key, windowSeconds * 2L, TimeUnit.SECONDS);
        }
        
        boolean allowed = count <= limit;
        int remaining = Math.max(0, limit - count.intValue());
        
        return new RateLimitResult(allowed, limit, remaining, windowEnd);
    }
}
