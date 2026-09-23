package com.gateway.gateway.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class UsageAggregatorService {

    private static final Logger logger = LoggerFactory.getLogger(UsageAggregatorService.class);
    private final StringRedisTemplate redisTemplate;
    
    public UsageAggregatorService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Async("usageExecutor")
    @EventListener
    public void handleApiUsageEvent(ApiUsageEvent event) {
        String apiKey = event.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }

        try {
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String redisKey = "usage:" + apiKey + ":" + currentMonth;

            // Increment total requests
            redisTemplate.opsForHash().increment(redisKey, "total", 1);
            
            // Increment status code specific counters
            redisTemplate.opsForHash().increment(redisKey, "status_" + event.getStatus(), 1);
            
            // Increment path specific counters
            redisTemplate.opsForHash().increment(redisKey, "path_" + event.getPath(), 1);

            // Track latency distribution in a rolling window of 1000 samples
            if (event.getStatus() == 200 || event.getStatus() == 500 || event.getStatus() == 503) {
                String latenciesKey = redisKey + ":latencies";
                redisTemplate.opsForList().leftPush(latenciesKey, String.valueOf(event.getLatency()));
                redisTemplate.opsForList().trim(latenciesKey, 0, 999);
            }
            
            logger.debug("Successfully aggregated usage for key in background thread");
        } catch (Exception e) {
            logger.error("Failed to aggregate usage for api key", e);
        }
    }
}
