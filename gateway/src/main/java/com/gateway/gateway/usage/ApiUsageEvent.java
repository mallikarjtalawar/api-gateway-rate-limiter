package com.gateway.gateway.usage;

import org.springframework.context.ApplicationEvent;

public class ApiUsageEvent extends ApplicationEvent {
    
    private final String apiKey;
    private final String path;
    private final int status;
    private final long latency;

    public ApiUsageEvent(Object source, String apiKey, String path, int status, long latency) {
        super(source);
        this.apiKey = apiKey;
        this.path = path;
        this.status = status;
        this.latency = latency;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return status;
    }

    public long getLatency() {
        return latency;
    }
}
