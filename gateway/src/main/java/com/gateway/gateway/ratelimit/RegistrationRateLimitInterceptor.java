package com.gateway.gateway.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

@Component
public class RegistrationRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private static final int REGISTRATION_LIMIT = 5;
    private static final int REGISTRATION_WINDOW_SECS = 60; // 5 reqs per minute

    public RegistrationRateLimitInterceptor(
            @Qualifier("slidingWindowRateLimiter") RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientIp = request.getRemoteAddr();
        String ipKey = "ip:" + clientIp;

        try {
            RateLimitResult result = rateLimiter.isAllowed(ipKey, REGISTRATION_LIMIT, REGISTRATION_WINDOW_SECS);

            response.addHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
            response.addHeader("X-RateLimit-Reset", String.valueOf(result.getResetTimeEpochSeconds()));

            if (!result.isAllowed()) {
                response.setStatus(429);
                long retryAfter = result.getResetTimeEpochSeconds() - Instant.now().getEpochSecond();
                response.addHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too Many Registration Requests\"}");
                return false;
            }

            return true;
        } catch (Exception e) {
            // Fail open on Redis errors for registration or handle gracefully.
            // Let's fail open.
            return true;
        }
    }
}
