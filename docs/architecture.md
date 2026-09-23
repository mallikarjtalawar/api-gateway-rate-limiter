# ThrottleGate Architecture

ThrottleGate is a high-performance, secure API Gateway built with Spring Boot, Redis, and React. It provides transparent proxy routing, real-time rate limiting, upstream circuit breaking, and a self-service developer portal.

## 1. System Overview

The system is separated into three primary contexts:
- **Upstream Proxy (`/**`)**: Transparently forwards traffic to the backend services while applying security, rate limiting, and observability.
- **Admin API (`/admin/**`)**: Secured via a shared `X-Admin-Secret` header. Provides capabilities for global key management, configuration changes, and usage monitoring.
- **Developer API (`/developer/**`)**: Public-facing, IP-rate-limited endpoints enabling customers to self-register for API keys and fetch their own usage analytics.

## 2. Core Components

### 2.1 Proxy Routing (`ProxyService`)
- Backed by **Apache HTTP Client 5** for asynchronous, high-throughput request execution.
- Removes standard hop-by-hop headers before forwarding to the upstream service.
- Handles streamed request/response bodies cleanly with minimal memory overhead.

### 2.2 Rate Limiting
Backed by Redis using a Sliding Window algorithm for precise enforcement without edge-case bursting.
- **`RateLimitInterceptor`**: Enforces usage quotas on downstream APIs using the client's `X-API-Key`. Fallbacks allow the system to fail-open dynamically if Redis goes down.
- **`RegistrationRateLimitInterceptor`**: Protects the public `/developer/keys` endpoint by tracking the client IP address (capped at 5 requests/minute) to prevent spam signups.

### 2.3 Circuit Breaker
- Integrated with **Resilience4j**.
- Protects the upstream server from cascading failures by halting proxy forwarding (fast-failing with 503) when upstream error rates cross a defined threshold.

### 2.4 Metrics & Observability
- **`LoggingInterceptor`**: Captures request details (path, status code, latency) before and after execution.
- **Asynchronous Aggregation**: Emits an `ApiUsageEvent` which is asynchronously processed by `UsageAggregatorService`.
- **Redis Counters**: Traffic statistics and percentiles (p50, p99) are stored natively in Redis, partitioned by month and API key for O(1) reads in the dashboard.

## 3. Security Model

- **API Keys**: Cryptographically generated (32-byte `SecureRandom`).
- **Hashing**: Raw keys are *never* stored in plaintext. They are immediately hashed using SHA-256 and stored as URL-safe Base64 strings. The gateway authenticates incoming requests by hashing the provided key on-the-fly and checking it against the registry.
- **Email Deduplication**: Developer self-service allows multiple anonymous signups but gracefully deduplicates explicitly provided email addresses to prevent account spam.

## 4. Frontend Application (Portal UI)

A unified Vite + React application providing two distinct experiences:
- **Admin Dashboard (`/dashboard`)**: Locked behind the admin secret. Enables administrators to view all registered clients, live system traffic, and configure individual client tiers.
- **Developer Portal (`/developer`)**: Public-facing portal where end-users can seamlessly generate keys, learn how to authenticate, and visualize their live usage breakdown via dynamic charts.
