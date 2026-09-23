package com.gateway.gateway.config;

import com.gateway.gateway.admin.AdminAuthInterceptor;
import com.gateway.gateway.auth.AuthInterceptor;
import com.gateway.gateway.logging.LoggingInterceptor;
import com.gateway.gateway.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final LoggingInterceptor loggingInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final com.gateway.gateway.ratelimit.RegistrationRateLimitInterceptor registrationRateLimitInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, 
                        RateLimitInterceptor rateLimitInterceptor, 
                        LoggingInterceptor loggingInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor,
                        com.gateway.gateway.ratelimit.RegistrationRateLimitInterceptor registrationRateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.loggingInterceptor = loggingInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.registrationRateLimitInterceptor = registrationRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Admin routes are protected by the admin secret token
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/admin/**");

        // Logging should run first (and complete last) to measure full latency
        registry.addInterceptor(loggingInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/admin/**", "/actuator/**");
        
        registry.addInterceptor(authInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/admin/**", "/actuator/**", "/developer/**");
                
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/**")
                .excludePathPatterns("/admin/**", "/actuator/**", "/developer/**");

        registry.addInterceptor(registrationRateLimitInterceptor).addPathPatterns("/developer/keys");
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.filter.CorsFilter corsFilter() {
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowCredentials(false);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("X-RateLimit-Limit");
        config.addExposedHeader("X-RateLimit-Remaining");
        config.addExposedHeader("X-RateLimit-Reset");
        config.addExposedHeader("X-Client-Tier");
        config.addExposedHeader("Retry-After");
        source.registerCorsConfiguration("/**", config);
        return new org.springframework.web.filter.CorsFilter(source);
    }
}
