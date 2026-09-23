package com.gateway.gateway.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class LoggingInterceptorTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final com.gateway.gateway.auth.AuthService authService = mock(com.gateway.gateway.auth.AuthService.class);
    private final LoggingInterceptor loggingInterceptor = new LoggingInterceptor(eventPublisher, authService);

    @Test
    public void testMaskApiKey() {
        assertEquals("UNAUTHENTICATED", loggingInterceptor.maskApiKey(null));
        assertEquals("UNAUTHENTICATED", loggingInterceptor.maskApiKey(""));
        assertEquals("***", loggingInterceptor.maskApiKey("short"));
        assertEquals("sec***key", loggingInterceptor.maskApiKey("secret-key"));
        assertEquals("123***890", loggingInterceptor.maskApiKey("1234567890"));
    }

    @Test
    public void testInterceptorLatencyAndAttributes() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("X-API-Key")).thenReturn("super-secret-123");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        long beforePreHandle = System.currentTimeMillis();
        boolean result = loggingInterceptor.preHandle(request, response, null);
        
        // Assert preHandle returns true
        assertEquals(true, result);
        
        // Verify attribute was set
        verify(request, times(1)).setAttribute(eq("LoggingInterceptor.startTime"), anyLong());

        // Now simulate what Spring does and pass the attribute back
        long mockStartTime = System.currentTimeMillis() - 50; // Simulate 50ms latency
        when(request.getAttribute("LoggingInterceptor.startTime")).thenReturn(mockStartTime);

        // Run afterCompletion
        loggingInterceptor.afterCompletion(request, response, null, null);
        
        // This will print to console, we can't easily assert on standard output without a special appender, 
        // but this verifies no exceptions are thrown.
        verify(request, times(1)).getAttribute("LoggingInterceptor.startTime");
    }
}
