package com.gateway.gateway.proxy;

import java.util.Map;
import java.util.List;

public class BufferedResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public BufferedResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}
