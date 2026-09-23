package com.gateway.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);
    private final AtomicInteger rejectedCount = new AtomicInteger(0);

    @Bean(name = "usageExecutor")
    public Executor usageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("UsageLogger-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            int count = rejectedCount.incrementAndGet();
            if (count % 100 == 1 || count < 10) { // Log first few, then sample
                logger.warn("Usage logger thread pool full. Discarding old task. Total discarded: {}", count);
            }
            new ThreadPoolExecutor.DiscardOldestPolicy().rejectedExecution(r, e);
        });
        
        executor.initialize();
        return executor;
    }
}
