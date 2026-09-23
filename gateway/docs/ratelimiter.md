# Rate Limiting & Storage Architecture

This document describes the architectural decisions and implementations for the API Gateway's distributed rate limiting, client configuration storage, and fallback mechanisms.

## Storage Strategy

We use **Redis Hashes** as the source of truth for client configurations.

- **Key Format**: `client:{hashedKey}`
- **Fields**:
  - `clientName` (string)
  - `email` (string)
  - `tier` (string: free, pro, enterprise)
  - `rateLimit` (int: requests allowed per window)
  - `windowSecs` (int: window duration in seconds)
  - `rotating_until` (string: epoch ms timestamp if the key is undergoing rotation)

By using Hashes, we can independently update a client's tier or limits without overwriting their other metadata. A global set `clients:all` is maintained to allow iterating over all registered clients (useful for the Admin UI).

## Rate Limiting Mechanism

The gateway uses a **Sliding Window** rate limiting algorithm backed by Redis.

1. **Local Caching**: To avoid hitting Redis twice per request (once for config, once for the Lua script execution), `RateLimitInterceptor` maintains a local `ConcurrentHashMap` caching the `[limit, windowSecs]` array for each `hashedKey`. 
   - This cache has a **5-second TTL**.
   - This ensures configuration changes (like upgrading a tier) propagate across the cluster within 5 seconds, while drastically reducing Redis read load.

2. **Distributed Enforcement**: We use a Lua script (`SlidingWindowRateLimiter`) executing against Redis to ensure atomic, exact enforcement across multiple instances of the gateway.

## Resilience: Fail-Open & Circuit Breaking

### Redis Down Behavior (Fail-Open to Fail-Closed)

If Redis goes down, `RateLimitInterceptor` catches the exception and engages a grace period:
- **Grace Period (e.g., 5s)**: If Redis just went down, the gateway **fails OPEN**. It allows requests through and logs a warning. This prevents a temporary Redis blip from dropping valid in-flight traffic.
- **Extended Outage**: Once the grace period is exceeded, the gateway **fails CLOSED**, returning a `503 Service Unavailable`. This protects the upstream systems from being overloaded if the rate limiter is permanently offline.

### Upstream Protection

The `ProxyService` uses **Resilience4j** for circuit breaking:
- Explicitly monitors `java.net.ConnectException`, `java.net.SocketTimeoutException`, and our custom `UpstreamServerErrorException` (thrown when the upstream returns HTTP 5xx).
- If the failure rate exceeds 50% over a sliding window, the circuit breaker opens and the gateway instantly returns `503 Service Unavailable`, preventing cascade failures.

## Key Rotation

To allow zero-downtime key rotation:
1. `POST /admin/keys/{hashedKey}/rotate` generates a new API key for the client.
2. The old key remains in Redis but receives a `rotating_until` field (set to 24 hours in the future).
3. The old key also receives a Redis TTL to match, ensuring it is automatically purged by Redis once the window expires.
4. `ApiKeyStore.isValid()` checks the `rotating_until` field; if the timestamp is in the past, it treats the key as invalid (even if the TTL hasn't fired yet).
