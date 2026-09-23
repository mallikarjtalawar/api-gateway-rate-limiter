package com.gateway.gateway.proxy;

import com.gateway.gateway.config.GatewayProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;
    private ProxyService proxyService;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    public void setup() throws Exception {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
                
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("upstream");

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        // Force the HTTP client to always throw an exception to simulate a dead backend
        when(httpClient.execute(any(org.apache.hc.core5.http.ClassicHttpRequest.class), 
                                any(org.apache.hc.core5.http.io.HttpClientResponseHandler.class)))
                .thenThrow(new java.net.ConnectException("Connection refused"));

        GatewayProperties properties = new GatewayProperties();
        properties.setUrl("http://localhost:59999");

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RequestCoalescer requestCoalescer = mock(RequestCoalescer.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        proxyService = new ProxyService(httpClient, properties, circuitBreaker, meterRegistry, requestCoalescer, redisTemplate);
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");
        when(request.getHeaderNames()).thenReturn(java.util.Collections.emptyEnumeration());
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(mock(java.io.PrintWriter.class));
    }

    @Test
    public void testCircuitBreakerTripsOnFailures() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // We configured it to trip if 5 out of 10 requests fail (50% failure rate)
        // Send 5 requests. They will all fail internally and set the response status.
        for (int i = 0; i < 5; i++) {
            proxyService.proxyRequest(request, response);
            // Verify that the proxyService caught the ConnectException and set 502 Bad Gateway
            verify(response, times(i + 1)).setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        }

        // After 5 failures, the circuit breaker should be OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // The 6th request should be instantly rejected by the Circuit Breaker with 503 Service Unavailable,
        // without even attempting to execute the http client.
        proxyService.proxyRequest(request, response);
        verify(response, times(1)).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
