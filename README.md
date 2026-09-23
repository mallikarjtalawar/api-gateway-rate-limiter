# ThrottleGate: High-Performance API Gateway 🚀

ThrottleGate is a robust, lightweight, and high-performance API Gateway built with **Spring Boot 3**, **Redis**, and **React**. It is designed to sit in front of your upstream services, providing essential features like transparent proxy routing, intelligent rate-limiting, circuit breaking, and detailed traffic analytics without adding latency overhead.

## ✨ Features

- **Transparent Proxy Routing**: Powered by Apache HTTP Client 5, seamlessly proxying headers, query parameters, and streaming bodies with near-zero overhead.
- **Intelligent Rate Limiting**: Redis-backed Sliding Window rate limiting that prevents edge-case bursting.
- **Circuit Breaker**: Integrated Resilience4j stops cascading failures by cutting off traffic to failing upstream servers dynamically.
- **Usage Analytics**: Asynchronous, event-driven tracking of request volumes, latency percentiles (p50/p99), and status code breakdowns.
- **Developer Self-Service Portal**: A sleek React frontend allowing developers to instantly generate and revoke API keys and view their own usage metrics.
- **Admin Dashboard**: Centralized view for administrators to monitor global system health, manage clients, and track platform usage.
- **Cryptographic Security**: API Keys are generated securely and stored purely as SHA-256 hashes—your users' keys are never stored in plaintext.

## 🏗️ Architecture

ThrottleGate is split into two primary components:

1. **Gateway Engine (Java / Spring Boot)**: Handles all incoming traffic, intercepts requests for rate-limiting and authentication, proxies to the upstream, and records asynchronous usage events.
2. **Portal UI (React / Vite)**: The user-facing dashboard containing both the secured Admin panel and the public Developer Portal.

*For a deep dive into the technical architecture, read our [Architecture Document](docs/architecture.md).*

## 🚀 Getting Started

### Prerequisites
- **Java 17+**
- **Node.js 18+**
- **Redis** (Running locally or via a cloud provider like Upstash)

### 1. Start the Backend
The backend runs on port `8091` by default.

```bash
cd gateway
# Ensure Redis is running locally on port 6379, or set SPRING_REDIS_URL
./mvnw spring-boot:run -DskipTests
```

### 2. Start the Frontend
The frontend runs on port `5173`.

```bash
cd portal-ui
npm install
npm run dev
```

### 3. Open the Dashboard
Navigate to `http://localhost:5173` in your browser. 
- You can access the **Developer Portal** to generate a key immediately.
- You can access the **Admin Dashboard** (Default Admin Secret is `secret123` unless changed in your backend properties).

## 🛡️ Using the Gateway

Once you have generated an API key from the Developer Portal, you can route requests through ThrottleGate to your upstream server.

Just pass the key in the `X-API-Key` header:

```bash
curl -H "X-API-Key: YOUR_GENERATED_KEY" \
     http://localhost:8091/api/v1/mock/orders
```

The gateway will validate your key, check your rate-limit quota in Redis, proxy the request to the upstream target, and return the response.

## 🌐 Deployment

ThrottleGate is built to be deployed easily on modern cloud providers:
- **Frontend**: Deploy the `portal-ui` folder directly to Vercel or Netlify. Set the `VITE_API_URL` environment variable.
- **Backend**: Deploy the `gateway` folder as a Web Service on Render, Railway, or AWS. Supply `SPRING_REDIS_URL` and `ADMIN_SECRET` environment variables.

## 📄 License
This project is open-source and available under the MIT License.
