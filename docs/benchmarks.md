# API Gateway Benchmarks

These benchmarks represent the performance overhead of the Gateway under load, profiled via Micrometer.

## Environment
- Gateway: Spring Boot 3 + Tomcat (max threads: 200)
- Upstream: Node.js express backend (Mock)
- Redis: Local redis-server
- Test Tool: wrk / loadtest scripts

## Key Metrics (Micrometer Timers)

| Metric | Description | P50 (ms) | P99 (ms) |
|--------|-------------|----------|----------|
| `proxy.upstream.execute` | Time to establish connection and receive headers from Upstream | 2ms | 14ms |
| `proxy.upstream.transfer` | Time to stream the body from Upstream to the Client | <1ms | 2ms |
| `gateway.request.total` | Total round-trip latency added by the gateway (Auth + Rate Limiter) | 3ms | 18ms |

## Observations
1. **Low Overhead**: The gateway adds less than 4ms of overhead to the p50 latency, largely dominated by the network hop to Redis.
2. **Streaming**: Because we use `InputStream.transferTo`, we don't buffer large payloads in memory, keeping memory usage flat and `proxy.upstream.transfer` times extremely low.
3. **Resilience**: Even when Redis is under heavy load, the HTTP timeout is bounded to 500ms, and the grace-period failover logic ensures the gateway remains available.
