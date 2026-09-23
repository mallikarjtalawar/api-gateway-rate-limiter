package com.gateway.gateway.usage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class UsageAggregatorTest {

    private UsageAggregatorService usageAggregatorService;
    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        
        usageAggregatorService = new UsageAggregatorService(redisTemplate);
    }

    @Test
    public void testUsageAggregatorIncrementsCounters() {
        String apiKey = "test-client-key";
        String path = "/api/v1/resource";
        int status = 200;
        long latency = 45;

        ApiUsageEvent event = new ApiUsageEvent(this, apiKey, path, status, latency);
        
        usageAggregatorService.handleApiUsageEvent(event);

        String expectedCurrentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String expectedRedisKey = "usage:" + apiKey + ":" + expectedCurrentMonth;

        verify(hashOperations, times(1)).increment(expectedRedisKey, "total", 1);
        verify(hashOperations, times(1)).increment(expectedRedisKey, "status_200", 1);
        verify(hashOperations, times(1)).increment(expectedRedisKey, "path_/api/v1/resource", 1);
    }

    @Test
    public void testUsageAggregatorIgnoresNullApiKey() {
        ApiUsageEvent event = new ApiUsageEvent(this, null, "/test", 200, 10);
        usageAggregatorService.handleApiUsageEvent(event);
        verifyNoInteractions(hashOperations);
    }
}
