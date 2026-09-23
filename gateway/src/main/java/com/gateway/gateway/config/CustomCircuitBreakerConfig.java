package com.gateway.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CustomCircuitBreakerConfig {

    @Bean
    public CircuitBreaker proxyCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Trip if 50% of requests fail
                .waitDurationInOpenState(Duration.ofSeconds(5)) // Cooldown before trying again
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5) // Need at least 5 calls to calculate failure rate
                .recordExceptions(
                        com.gateway.gateway.proxy.UpstreamServerErrorException.class,
                        java.net.SocketTimeoutException.class,
                        java.net.ConnectException.class
                )
                .build();
                
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker("upstream");
    }
}
