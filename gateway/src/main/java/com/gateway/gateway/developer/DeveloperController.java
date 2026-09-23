package com.gateway.gateway.developer;

import com.gateway.gateway.auth.AuthService;
import com.gateway.gateway.metrics.RpsMonitor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/developer")
public class DeveloperController {

    private final AuthService authService;
    private final RpsMonitor rpsMonitor;

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public DeveloperController(AuthService authService, RpsMonitor rpsMonitor, org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.authService = authService;
        this.rpsMonitor = rpsMonitor;
        this.redisTemplate = redisTemplate;
    }

    public static class KeyRequest {
        public String clientName;
        public String email;
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

    @GetMapping("/usage/{rawKey}")
    public ResponseEntity<?> getUsage(@PathVariable String rawKey) {
        String hashedKey = authService.hashKey(rawKey);
        
        // Ensure the key exists
        if (authService.getAllClients().stream().noneMatch(c -> c.get("hashedKey").equals(hashedKey))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Key not found or revoked"));
        }
        
        String currentMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        String redisKey = "usage:" + hashedKey + ":" + currentMonth;

        Map<Object, Object> usageStats = redisTemplate.opsForHash().entries(redisKey);
        
        // Calculate latency percentiles
        String latenciesKey = redisKey + ":latencies";
        java.util.List<String> latenciesStrs = redisTemplate.opsForList().range(latenciesKey, 0, -1);
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
