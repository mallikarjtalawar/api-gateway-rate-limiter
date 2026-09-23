package com.gateway.gateway.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class RedisApiKeyStore implements ApiKeyStore {
    private final StringRedisTemplate redisTemplate;

    public RedisApiKeyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isValid(String hashedKey) {
        String key = "client:" + hashedKey;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return false;
        }
        Object rotatingUntil = redisTemplate.opsForHash().get(key, "rotating_until");
        if (rotatingUntil != null) {
            long expiry = Long.parseLong(rotatingUntil.toString());
            if (System.currentTimeMillis() > expiry) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void saveKey(String hashedKey, String clientName, String email) {
        String key = "client:" + hashedKey;
        redisTemplate.opsForHash().put(key, "clientName", clientName != null ? clientName : "Anonymous");
        redisTemplate.opsForHash().put(key, "email", email != null ? email : "none");
        redisTemplate.opsForHash().put(key, "createdAt", java.time.Instant.now().toString());
        
        // Add to the global set of clients
        redisTemplate.opsForSet().add("clients:all", hashedKey);
    }

    @Override
    public void markRotating(String hashedKey, long rotatingUntilMs) {
        String key = "client:" + hashedKey;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForHash().put(key, "rotating_until", String.valueOf(rotatingUntilMs));
            long ttlSecs = (rotatingUntilMs - System.currentTimeMillis()) / 1000;
            if (ttlSecs > 0) {
                redisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSecs));
            }
        }
    }

    @Override
    public void revokeKey(String hashedKey) {
        redisTemplate.delete("client:" + hashedKey);
        redisTemplate.opsForSet().remove("clients:all", hashedKey);
    }

    @Override
    public List<Map<Object, Object>> getAllClients() {
        Set<String> allKeys = redisTemplate.opsForSet().members("clients:all");
        List<Map<Object, Object>> clients = new ArrayList<>();
        if (allKeys != null) {
            for (String hashedKey : allKeys) {
                Map<Object, Object> data = redisTemplate.opsForHash().entries("client:" + hashedKey);
                if (!data.isEmpty()) {
                    data.put("hashedKey", hashedKey); // Include the hash for tracking
                    clients.add(data);
                }
            }
        }
        return clients;
    }
}
