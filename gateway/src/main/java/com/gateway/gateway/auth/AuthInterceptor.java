package com.gateway.gateway.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.gateway.gateway.redis.RedisHealthMonitor;
import com.gateway.gateway.metrics.RpsMonitor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final RedisHealthMonitor redisHealthMonitor;
    private final RpsMonitor rpsMonitor;

    public AuthInterceptor(AuthService authService, RedisHealthMonitor redisHealthMonitor, RpsMonitor rpsMonitor) {
        this.authService = authService;
        this.redisHealthMonitor = redisHealthMonitor;
        this.rpsMonitor = rpsMonitor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Count request towards RPS
        rpsMonitor.increment();

        String apiKey = request.getHeader("X-API-Key");
        
        try {
            if (authService.validateApiKey(apiKey)) {
                redisHealthMonitor.markRedisUp();
                return true;
            }
        } catch (Exception e) {
            boolean failOpen = redisHealthMonitor.markRedisDownAndCheckGracePeriod();
            if (failOpen) {
                return true;
            } else {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Service Unavailable\"}");
                return false;
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Unauthorized: Invalid or missing X-API-Key\"}");
        return false;
    }
}
