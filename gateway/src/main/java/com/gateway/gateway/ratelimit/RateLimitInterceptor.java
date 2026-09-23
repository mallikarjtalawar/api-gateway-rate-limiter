package com.gateway.gateway.ratelimit;

import com.gateway.gateway.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gateway.gateway.config.GatewayProperties;
import com.gateway.gateway.redis.RedisHealthMonitor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiter rateLimiter;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties gatewayProperties;

    // Local cache: hashedKey -> [limit, windowSecs, cacheExpiryEpochSecond]
    private final ConcurrentHashMap<String, long[]> configCache = new ConcurrentHashMap<>();
    
    private final RedisHealthMonitor redisHealthMonitor;

    private static final int DEFAULT_LIMIT  = 10;
    private static final int DEFAULT_WINDOW = 60;
    private static final int CACHE_TTL_SECS = 5; // Propagation delay for config changes

    public RateLimitInterceptor(
            @Qualifier("slidingWindowRateLimiter") RateLimiter rateLimiter,
            AuthService authService,
            StringRedisTemplate redisTemplate,
            GatewayProperties gatewayProperties,
            RedisHealthMonitor redisHealthMonitor) {
        this.rateLimiter   = rateLimiter;
        this.authService   = authService;
        this.redisTemplate = redisTemplate;
        this.gatewayProperties = gatewayProperties;
        this.redisHealthMonitor = redisHealthMonitor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            return true; // Let AuthInterceptor handle 401s
        }

        String hashedKey = authService.hashKey(apiKey);
        RateLimitResult result;

        try {
            int[] config     = getClientConfig(hashedKey);
            int limit        = config[0];
            int windowSecs   = config[1];

            result = rateLimiter.isAllowed(apiKey, limit, windowSecs);
            
            // If we succeed, reset the down timer via monitor
            redisHealthMonitor.markRedisUp();
            
            response.addHeader("X-RateLimit-Limit",     String.valueOf(result.getLimit()));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
            response.addHeader("X-RateLimit-Reset",     String.valueOf(result.getResetTimeEpochSeconds()));
            response.addHeader("X-Client-Tier",         getClientTier(hashedKey));

        } catch (Exception e) {
            boolean failOpen = redisHealthMonitor.markRedisDownAndCheckGracePeriod();
            
            if (failOpen) {
                return true;
            } else {
                response.setStatus(503);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Service Unavailable\"}");
                return false;
            }
        }

        if (!result.isAllowed()) {
            response.setStatus(429);
            long retryAfter = result.getResetTimeEpochSeconds() - Instant.now().getEpochSecond();
            response.addHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too Many Requests\"}");
            return false;
        }

        return true;
    }

    /** Returns [limit, windowSecs] from local cache (5s TTL) or Redis. */
    private int[] getClientConfig(String hashedKey) {
        long now = Instant.now().getEpochSecond();
        long[] cached = configCache.get(hashedKey);
        if (cached != null && cached[2] > now) {
            return new int[]{ (int) cached[0], (int) cached[1] };
        }
        // Cache miss or stale — read from Redis
        Map<Object, Object> data = redisTemplate.opsForHash().entries("client:" + hashedKey);
        int limit  = parseIntOrDefault(data.get("rateLimit"),  DEFAULT_LIMIT);
        int window = parseIntOrDefault(data.get("windowSecs"), DEFAULT_WINDOW);
        configCache.put(hashedKey, new long[]{ limit, window, now + CACHE_TTL_SECS });
        return new int[]{ limit, window };
    }

    private String getClientTier(String hashedKey) {
        long[] cached = configCache.get(hashedKey);
        if (cached != null) {
            Object tier = redisTemplate.opsForHash().get("client:" + hashedKey, "tier");
            return tier != null ? tier.toString() : "free";
        }
        return "free";
    }

    private int parseIntOrDefault(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
