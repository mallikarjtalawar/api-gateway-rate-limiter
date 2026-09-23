package com.gateway.gateway.logging;

import com.gateway.gateway.usage.ApiUsageEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.gateway.gateway.auth.AuthService;

@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final String START_TIME_ATTR = "LoggingInterceptor.startTime";

    private final ApplicationEventPublisher eventPublisher;
    private final AuthService authService;

    public LoggingInterceptor(ApplicationEventPublisher eventPublisher, AuthService authService) {
        this.eventPublisher = eventPublisher;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long latency = 0;
        if (startTime != null) {
            latency = System.currentTimeMillis() - startTime;
        }
        
        String apiKey = request.getHeader("X-API-Key");
        String maskedKey = maskApiKey(apiKey);
        
        String method = request.getMethod();
        String path = request.getRequestURI();
        int status = response.getStatus();
        
        logger.info("[API-GATEWAY] Method: {}, Path: {}, API_KEY: {}, Status: {}, Latency: {}ms", 
                method, path, maskedKey, status, latency);

        // Fire asynchronous usage event for counting metrics
        String hashedKey = apiKey != null ? authService.hashKey(apiKey) : null;
        eventPublisher.publishEvent(new ApiUsageEvent(this, hashedKey, path, status, latency));
    }

    protected String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "UNAUTHENTICATED";
        }
        if (apiKey.length() <= 6) {
            return "***";
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }
}
