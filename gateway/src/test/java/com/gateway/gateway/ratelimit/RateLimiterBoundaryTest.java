package com.gateway.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class RateLimiterBoundaryTest {

    @Autowired
    private FixedWindowRateLimiter rateLimiter;

    @Test
    public void testBoundaryBurstFlaw() throws InterruptedException {
        String apiKey = "boundary-test-key-" + System.currentTimeMillis();
        int limit = 10;
        int window = 2; // 2 seconds window

        // 1. Wait until we are just before the window boundary
        // We want to fire our requests at the very end of the current 2-second window.
        long startEpoch = Instant.now().getEpochSecond();
        while (Instant.now().getEpochSecond() % window != (window - 1)) {
            Thread.sleep(100);
        }

        // 2. We are in the last second of the window. Exhaust the limit.
        for (int i = 0; i < limit; i++) {
            RateLimitResult res = rateLimiter.isAllowed(apiKey, limit, window);
            assertTrue(res.isAllowed(), "Should be allowed in first window");
        }

        // 3. Wait for the window to flip (a little over 1 second to ensure we cross the boundary)
        Thread.sleep(1100);

        // 4. Now we are in the first second of the NEXT window.
        // Even though only ~1.5 seconds have passed since our first request,
        // we can exhaust the limit AGAIN in the new fixed window.
        for (int i = 0; i < limit; i++) {
            RateLimitResult res = rateLimiter.isAllowed(apiKey, limit, window);
            assertTrue(res.isAllowed(), "Should be allowed in second window due to boundary flaw!");
        }

        // We just successfully made 20 requests in ~1.5 seconds, even though our rate limit is 10 per 2 seconds.
        // This proves the boundary burst flaw of the fixed window algorithm.
        System.out.println("Boundary flaw successfully demonstrated: 2x limit allowed within the time window duration.");
    }
}
