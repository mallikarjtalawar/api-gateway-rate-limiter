package com.gateway.gateway.auth;

import java.util.List;
import java.util.Map;

public interface ApiKeyStore {
    boolean isValid(String hashedKey);
    void saveKey(String hashedKey, String clientName, String email);
    void markRotating(String hashedKey, long rotatingUntilMs);
    void revokeKey(String hashedKey);
    List<Map<Object, Object>> getAllClients();
}
