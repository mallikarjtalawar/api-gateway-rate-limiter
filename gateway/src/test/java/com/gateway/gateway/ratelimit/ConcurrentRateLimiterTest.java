package com.gateway.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ConcurrentRateLimiterTest {

    @Autowired
    private SlidingWindowRateLimiter rateLimiter;

    @Test
    public void testConcurrencyWithLuaScript() throws InterruptedException {
        String apiKey = "concurrent-test-key-" + System.currentTimeMillis();
        int limit = 10;
        int windowSeconds = 10;
        
        int totalRequests = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(totalRequests);
        
        // Use a latch to ensure all threads hit the limiter at the exact same millisecond
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalRequests);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = rateLimiter.isAllowed(apiKey, limit, windowSeconds);
                    if (result.isAllowed()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Release the hounds!
        startLatch.countDown();
        
        // Wait for all threads to complete
        endLatch.await();
        executorService.shutdown();

        System.out.println("Concurrent test completed. Successes: " + successCount.get() + ", Failures: " + failureCount.get());

        assertEquals(limit, successCount.get(), "Only exactly " + limit + " requests should succeed under heavy concurrency");
        assertEquals(totalRequests - limit, failureCount.get(), "The rest should be correctly rate limited");
    }
}
