package com.gateway.gateway.proxy;

public class UpstreamServerErrorException extends RuntimeException {
    public UpstreamServerErrorException(String message) {
        super(message);
    }
}
