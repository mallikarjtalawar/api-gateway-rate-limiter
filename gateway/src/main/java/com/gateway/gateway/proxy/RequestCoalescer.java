package com.gateway.gateway.proxy;

import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RequestCoalescer {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestCoalescer.class);
    
    private final ConcurrentHashMap<String, CompletableFuture<BufferedResponse>> inFlightRequests = new ConcurrentHashMap<>();

    /**
     * Executes the supplier only if no other request for the same key is currently in flight.
     * Otherwise, waits for the in-flight request to complete and returns its result.
     */
    public BufferedResponse executeOrWait(String key, Supplier<BufferedResponse> upstreamCaller) throws Exception {
        CompletableFuture<BufferedResponse> newFuture = new CompletableFuture<>();
        CompletableFuture<BufferedResponse> existingFuture = inFlightRequests.putIfAbsent(key, newFuture);

        if (existingFuture != null) {
            logger.info("Coalescing request for key: {}", key);
            // Wait for the existing request to finish
            return existingFuture.get(); // can throw ExecutionException
        }

        try {
            // We are the first, call upstream
            BufferedResponse response = upstreamCaller.get();
            newFuture.complete(response);
            return response;
        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            throw e;
        } finally {
            // Always remove the future from the map when done
            inFlightRequests.remove(key);
        }
    }
}
