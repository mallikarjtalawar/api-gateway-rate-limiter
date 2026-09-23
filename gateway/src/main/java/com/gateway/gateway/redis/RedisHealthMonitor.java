package com.gateway.gateway.redis;

import com.gateway.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RedisHealthMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisHealthMonitor.class);
    
    // Tracks when Redis was first detected as down. 0 means it's up.
    private final AtomicLong redisDownSinceEpochSecond = new AtomicLong(0);
    private final GatewayProperties gatewayProperties;

    public RedisHealthMonitor(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    public void markRedisUp() {
        if (redisDownSinceEpochSecond.get() > 0) {
            logger.info("Redis is back up. Gateway operating normally.");
            redisDownSinceEpochSecond.set(0);
        }
    }

    /**
     * Call this when a Redis exception occurs.
     * @return true if we are still within the grace period (FAIL OPEN), false if we should FAIL CLOSED.
     */
    public boolean markRedisDownAndCheckGracePeriod() {
        long now = Instant.now().getEpochSecond();
        long downSince = redisDownSinceEpochSecond.updateAndGet(current -> current == 0 ? now : current);
        long gracePeriod = gatewayProperties.getRedisDownGracePeriodSecs();
        
        long downFor = now - downSince;
        
        if (downFor < gracePeriod) {
            logger.warn("Redis is down (down for {}s). Failing OPEN (allowing request).", downFor);
            return true;
        } else {
            logger.error("Redis is down (down for {}s). Grace period ({}s) exceeded. Failing CLOSED.", downFor, gracePeriod);
            return false;
        }
    }
}
