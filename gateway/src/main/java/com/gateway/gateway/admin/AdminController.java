package com.gateway.gateway.admin;

import com.gateway.gateway.auth.AuthService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import com.gateway.gateway.metrics.RpsMonitor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final CircuitBreaker circuitBreaker;
    private final RpsMonitor rpsMonitor;

    public AdminController(AuthService authService, StringRedisTemplate redisTemplate, 
                           CircuitBreaker circuitBreaker, RpsMonitor rpsMonitor) {
        this.authService = authService;
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
        this.rpsMonitor = rpsMonitor;
    }

    public static class KeyRequest {
        public String clientName;
        public String email;
    }

    public static class ClientConfigRequest {
        public String tier;        // free, pro, enterprise, custom
        public Integer rateLimit;  // requests per window
        public Integer windowSecs; // window size in seconds
    }

    @PostMapping("/keys")
    public ResponseEntity<?> generateKey(@RequestBody(required = false) KeyRequest request) {
        String clientName = (request != null && request.clientName != null) ? request.clientName : "Anonymous";
        String email = (request != null && request.email != null) ? request.email : "none";
        try {
            String newKey = authService.generateApiKey(clientName, email);
            return ResponseEntity.ok(Map.of("apiKey", newKey));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/clients")
    public ResponseEntity<java.util.List<Map<Object, Object>>> getClients() {
        return ResponseEntity.ok(authService.getAllClients());
    }
    
    @GetMapping("/circuit-breaker")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return ResponseEntity.ok(Map.of(
            "state", circuitBreaker.getState().name(),
            "failureRate", metrics.getFailureRate(),
            "failedCalls", metrics.getNumberOfFailedCalls(),
            "slowCalls", metrics.getNumberOfSlowCalls(),
            "totalCalls", metrics.getNumberOfBufferedCalls()
        ));
    }
    
    @GetMapping("/metrics/rps")
    public ResponseEntity<List<Integer>> getRpsMetrics() {
        return ResponseEntity.ok(rpsMonitor.getHistory());
    }

    @PutMapping("/clients/{hashedKey}/config")
    public ResponseEntity<Map<String, Object>> updateClientConfig(
            @PathVariable String hashedKey,
            @RequestBody ClientConfigRequest config) {
        String key = "client:" + hashedKey;
        // Validate key exists
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return ResponseEntity.notFound().build();
        }
        String tier = config.tier != null ? config.tier : "free";
        int limit = config.rateLimit != null ? config.rateLimit : tierDefaultLimit(tier);
        int window = config.windowSecs != null ? config.windowSecs : 60;
        redisTemplate.opsForHash().put(key, "tier", tier);
        redisTemplate.opsForHash().put(key, "rateLimit", String.valueOf(limit));
        redisTemplate.opsForHash().put(key, "windowSecs", String.valueOf(window));
        return ResponseEntity.ok(Map.of(
            "tier", tier,
            "rateLimit", limit,
            "windowSecs", window
        ));
    }

    @GetMapping("/clients/{hashedKey}/config")
    public ResponseEntity<Map<Object, Object>> getClientConfig(@PathVariable String hashedKey) {
        String key = "client:" + hashedKey;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(data);
    }

    private int tierDefaultLimit(String tier) {
        return switch (tier) {
            case "pro"        -> 100;
            case "enterprise" -> 1000;
            case "unlimited"  -> Integer.MAX_VALUE;
            default           -> 10;   // free
        };
    }

    @DeleteMapping("/keys/{rawKey}")
    public ResponseEntity<Void> revokeKey(@PathVariable String rawKey) {
        authService.revokeApiKey(rawKey);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clients/{hashedKey}")
    public ResponseEntity<Void> revokeClientByHash(@PathVariable String hashedKey) {
        // Direct revocation using hash
        authService.revokeApiKeyByHash(hashedKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/keys/{hashedKey}/rotate")
    public ResponseEntity<Map<String, String>> rotateKey(@PathVariable String hashedKey) {
        try {
            String newKey = authService.rotateApiKey(hashedKey);
            return ResponseEntity.ok(Map.of("apiKey", newKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/usage/{rawKey}")
    public ResponseEntity<Map<Object, Object>> getUsage(@PathVariable String rawKey) {
        String hashedKey = authService.hashKey(rawKey);
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String redisKey = "usage:" + hashedKey + ":" + currentMonth;

        Map<Object, Object> usageStats = redisTemplate.opsForHash().entries(redisKey);
        
        // Calculate latency percentiles
        String latenciesKey = redisKey + ":latencies";
        List<String> latenciesStrs = redisTemplate.opsForList().range(latenciesKey, 0, -1);
        if (latenciesStrs != null && !latenciesStrs.isEmpty()) {
            long[] latencies = latenciesStrs.stream().mapToLong(Long::parseLong).sorted().toArray();
            long p50 = latencies[(int) (latencies.length * 0.50)];
            long p99 = latencies[(int) (latencies.length * 0.99)];
            usageStats.put("p50_latency_ms", String.valueOf(p50));
            usageStats.put("p99_latency_ms", String.valueOf(p99));
            usageStats.put("latency_samples", String.valueOf(latencies.length));
        }

        return ResponseEntity.ok(usageStats);
    }
}
