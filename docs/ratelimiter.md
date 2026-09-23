# API Gateway Rate Limiter Architecture

The API Gateway uses a highly resilient, distributed **Sliding Window Rate Limiter** powered by Redis, with a local Caffeine cache for performance.

## Architecture

1. **Redis Sliding Window (Global State)**
   We use a sliding window log approach implemented in Lua.
   - Key format: `rate_limit:{hashed_api_key}`
   - Each incoming request records its timestamp (in microseconds) using Redis `ZADD`.
   - We use `ZREMRANGEBYSCORE` to evict timestamps older than `(now - windowSize)`.
   - `ZCARD` counts the remaining valid timestamps in the window.
   - If `ZCARD` > limit, the request is dropped (HTTP 429).
   
   This ensures perfect rate limiting even when traffic is distributed across multiple Gateway instances.

2. **Caffeine Cache (Local Tier Configurations)**
   To avoid reading a user's rate limit tier from Redis on *every* request, we cache their `[limit, windowSecs]` locally in a ConcurrentHashMap/Caffeine cache.
   - The cache expires keys locally every 60 seconds.
   - If a key isn't in cache, we fetch it from Redis `client:{hashedKey}` and populate the local cache.

## Fallback & Resilience (Fail-Open)

The Rate Limiter and Authentication paths share a `RedisHealthMonitor` component.

1. **Fail-Open (Grace Period)**
   If Redis becomes unreachable (e.g., connection timeout, cluster election), the gateway enters a **grace period** (default: 5 seconds). During this period, all API requests are allowed through (Failing OPEN), ensuring that a brief Redis blip doesn't cause a massive outage for our clients.

2. **Fail-Closed**
   If Redis remains down beyond the grace period, the gateway switches to **Fail-Closed** and begins rejecting API requests with HTTP 503 (Service Unavailable). This protects the backend systems from being overwhelmed by unthrottled traffic.

3. **Recovery**
   As soon as a Redis command succeeds, the `RedisHealthMonitor` resets the timer and normal authentication and rate-limiting resumes immediately.
