package com.gateway.gateway.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.gateway.gateway.config.GatewayProperties;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final GatewayProperties gatewayProperties;
    
    public AdminAuthInterceptor(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    } 

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String secret = request.getHeader("X-Admin-Secret");
        
        if (gatewayProperties.getAdminSecret().equals(secret)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}
