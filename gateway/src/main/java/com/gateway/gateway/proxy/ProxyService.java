package com.gateway.gateway.proxy;

import com.gateway.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

@Service
public class ProxyService {

    private final CloseableHttpClient httpClient;
    private final GatewayProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final RequestCoalescer requestCoalescer;
    private final StringRedisTemplate redisTemplate;

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade"
    );

    public ProxyService(CloseableHttpClient httpClient, GatewayProperties properties, 
                        CircuitBreaker circuitBreaker, MeterRegistry meterRegistry,
                        RequestCoalescer requestCoalescer, StringRedisTemplate redisTemplate) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
        this.requestCoalescer = requestCoalescer;
        this.redisTemplate = redisTemplate;
    }

    public void proxyRequest(HttpServletRequest request, HttpServletResponse response) {
        try {
            circuitBreaker.executeCheckedSupplier(() -> {
                handleProxyLogic(request, response);
                return null;
            });
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\": \"Service Unavailable (Circuit Breaker OPEN)\"}");
            } catch (IOException ioException) {
                // ignore
            }
        } catch (Throwable e) {
            // executeProxyRequest handles most errors, but if it throws, catch here
            if (response.getStatus() == 200) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void handleProxyLogic(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String method = request.getMethod().toUpperCase();
        String idempotencyKey = request.getHeader("X-Idempotency-Key");
        String apiKey = request.getHeader("X-API-Key");
        
        boolean isGet = "GET".equals(method);
        boolean isMutatingWithIdempotency = ("POST".equals(method) || "PUT".equals(method)) && idempotencyKey != null && !idempotencyKey.isEmpty();
        
        if (isGet) {
            String cacheKey = "COALESCE:" + method + ":" + request.getRequestURI() + "?" + request.getQueryString();
            BufferedResponse bufferedResponse = requestCoalescer.executeOrWait(cacheKey, () -> executeProxyRequestBuffered(request));
            writeBufferedResponse(bufferedResponse, response);
        } else if (isMutatingWithIdempotency) {
            String redisKey = "idemp:" + apiKey + ":" + idempotencyKey;
            
            // 1. Check Redis first
            String cachedBody = redisTemplate.opsForValue().get(redisKey);
            if (cachedBody != null) {
                // Return cached response
                response.setStatus(200); // Assuming we only cache 200s
                response.setContentType("application/json");
                response.setHeader("X-Idempotent-Replay", "true");
                response.getOutputStream().write(cachedBody.getBytes());
                return;
            }
            
            // 2. Coalesce concurrent identical mutating requests
            BufferedResponse bufferedResponse = requestCoalescer.executeOrWait("COALESCE:" + redisKey, () -> executeProxyRequestBuffered(request));
            
            // 3. Cache the result if successful
            if (bufferedResponse.getStatusCode() >= 200 && bufferedResponse.getStatusCode() < 300) {
                redisTemplate.opsForValue().set(redisKey, new String(bufferedResponse.getBody()), 24, TimeUnit.HOURS);
            }
            
            writeBufferedResponse(bufferedResponse, response);
        } else {
            // Stream normally for everything else
            executeProxyRequestStream(request, response);
        }
    }

    private void writeBufferedResponse(BufferedResponse bufferedResponse, HttpServletResponse response) throws Exception {
        response.setStatus(bufferedResponse.getStatusCode());
        for (Map.Entry<String, List<String>> entry : bufferedResponse.getHeaders().entrySet()) {
            for (String val : entry.getValue()) {
                response.addHeader(entry.getKey(), val);
            }
        }
        if (bufferedResponse.getBody() != null) {
            response.getOutputStream().write(bufferedResponse.getBody());
        }
    }

    private BufferedResponse executeProxyRequestBuffered(HttpServletRequest request) {
        try {
            String targetUrl = properties.getUrl() + request.getRequestURI();
            if (request.getQueryString() != null) {
                targetUrl += "?" + request.getQueryString();
            }

            HttpUriRequestBase proxyRequest = new HttpUriRequestBase(request.getMethod().toUpperCase(), URI.create(targetUrl));
            copyHeaders(request, proxyRequest);
            copyBody(request, proxyRequest);

            Timer.Sample executeSample = Timer.start(meterRegistry);
            try {
                return httpClient.execute(proxyRequest, classicHttpResponse -> {
                    int statusCode = classicHttpResponse.getCode();
                    if (statusCode >= 500) {
                        throw new UpstreamServerErrorException("Upstream returned " + statusCode);
                    }
                    
                    Map<String, List<String>> headers = new HashMap<>();
                    for (Header header : classicHttpResponse.getHeaders()) {
                        String name = header.getName().toLowerCase();
                        if (!HOP_BY_HOP_HEADERS.contains(name)
                                && !name.equalsIgnoreCase("Transfer-Encoding")
                                && !name.startsWith("access-control-")) {
                            headers.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
                        }
                    }

                    byte[] body = new byte[0];
                    HttpEntity entity = classicHttpResponse.getEntity();
                    if (entity != null) {
                        Timer.Sample transferSample = Timer.start(meterRegistry);
                        try (InputStream is = entity.getContent()) {
                            body = is.readAllBytes();
                        } finally {
                            transferSample.stop(meterRegistry.timer("proxy.upstream.transfer"));
                        }
                    }
                    return new BufferedResponse(statusCode, headers, body);
                });
            } catch (java.net.SocketTimeoutException e) {
                throw new RuntimeException("GATEWAY_TIMEOUT", e);
            } catch (java.net.ConnectException e) {
                throw new RuntimeException("BAD_GATEWAY", e);
            } catch (Exception e) {
                throw new RuntimeException("INTERNAL_SERVER_ERROR", e);
            } finally {
                executeSample.stop(meterRegistry.timer("proxy.upstream.execute"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void copyHeaders(HttpServletRequest request, HttpUriRequestBase proxyRequest) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase()) && !headerName.equalsIgnoreCase("host")) {
                Enumeration<String> headers = request.getHeaders(headerName);
                while (headers.hasMoreElements()) {
                    String headerValue = headers.nextElement();
                    if (!headerName.equalsIgnoreCase("content-length")) {
                        proxyRequest.addHeader(headerName, headerValue);
                    }
                }
            }
        }
    }

    private void copyBody(HttpServletRequest request, HttpUriRequestBase proxyRequest) throws IOException {
        if (request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null) {
            proxyRequest.setEntity(new InputStreamEntity(request.getInputStream(), request.getContentLength(), null));
        }
    }

    private void executeProxyRequestStream(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String targetUrl = properties.getUrl() + request.getRequestURI();
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        HttpUriRequestBase proxyRequest = new HttpUriRequestBase(request.getMethod().toUpperCase(), URI.create(targetUrl));

        copyHeaders(request, proxyRequest);
        copyBody(request, proxyRequest);

        Timer.Sample executeSample = Timer.start(meterRegistry);
        try {
            httpClient.execute(proxyRequest, classicHttpResponse -> {
                response.setStatus(classicHttpResponse.getCode());

                for (Header header : classicHttpResponse.getHeaders()) {
                    String name = header.getName().toLowerCase();
                    // Strip hop-by-hop AND upstream CORS headers — gateway owns CORS
                    if (!HOP_BY_HOP_HEADERS.contains(name)
                            && !name.equalsIgnoreCase("Transfer-Encoding")
                            && !name.startsWith("access-control-")) {
                        response.addHeader(header.getName(), header.getValue());
                    }
                }

                HttpEntity entity = classicHttpResponse.getEntity();
                if (entity != null) {
                    Timer.Sample transferSample = Timer.start(meterRegistry);
                    try (InputStream is = entity.getContent(); OutputStream os = response.getOutputStream()) {
                        is.transferTo(os);
                    } finally {
                        transferSample.stop(meterRegistry.timer("proxy.upstream.transfer"));
                    }
                }
                
                if (classicHttpResponse.getCode() >= 500) {
                    throw new UpstreamServerErrorException("Upstream returned " + classicHttpResponse.getCode());
                }
                
                return null;
            });
        } catch (java.net.SocketTimeoutException e) {
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            throw e; // Rethrow so Circuit Breaker records it
        } catch (java.net.ConnectException e) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            throw e; // Rethrow so Circuit Breaker records it
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw e; // Rethrow so Circuit Breaker records it
        } finally {
            executeSample.stop(meterRegistry.timer("proxy.upstream.execute"));
        }
    }
}
