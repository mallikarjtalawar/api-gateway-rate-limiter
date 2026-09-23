package com.gateway.gateway.admin;

import com.gateway.gateway.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import com.gateway.gateway.metrics.RpsMonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

public class AdminControllerTest {

    private AdminController adminController;
    private AuthService authService;
    private StringRedisTemplate redisTemplate;
    private CircuitBreaker circuitBreaker;
    private HashOperations<String, Object, Object> hashOperations;
    private ListOperations<String, String> listOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        authService = mock(AuthService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        listOperations = mock(ListOperations.class);
        circuitBreaker = mock(CircuitBreaker.class);
        RpsMonitor rpsMonitor = mock(RpsMonitor.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        adminController = new AdminController(authService, redisTemplate, circuitBreaker, rpsMonitor);
    }

    @Test
    public void testGenerateKey() {
        when(authService.generateApiKey("Anonymous", "none")).thenReturn("new-api-key");
        
        AdminController.KeyRequest req = new AdminController.KeyRequest();
        req.clientName = "Anonymous";
        req.email = "none";

        ResponseEntity<?> response = adminController.generateKey(req);
        
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("new-api-key", ((java.util.Map<?, ?>) response.getBody()).get("apiKey"));
        verify(authService, times(1)).generateApiKey("Anonymous", "none");
    }

    @Test
    public void testRevokeKey() {
        ResponseEntity<Void> response = adminController.revokeKey("some-key");
        
        assertEquals(200, response.getStatusCode().value());
        verify(authService, times(1)).revokeApiKey("some-key");
    }

    @Test
    public void testGetUsage() {
        String rawKey = "some-key";
        String hashedKey = "hashed-key";
        when(authService.hashKey(rawKey)).thenReturn(hashedKey);
        
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String expectedRedisKey = "usage:" + hashedKey + ":" + currentMonth;

        Map<Object, Object> mockStats = new java.util.HashMap<>(Map.of("total", "100", "status_200", "95", "status_500", "5"));
        when(hashOperations.entries(expectedRedisKey)).thenReturn(mockStats);
        
        String latenciesKey = expectedRedisKey + ":latencies";
        when(listOperations.range(latenciesKey, 0, -1)).thenReturn(java.util.List.of("10", "20", "30"));

        ResponseEntity<Map<Object, Object>> response = adminController.getUsage(rawKey);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("20", response.getBody().get("p50_latency_ms"));
        verify(hashOperations, times(1)).entries(expectedRedisKey);
        verify(listOperations, times(1)).range(latenciesKey, 0, -1);
    }
}
