package com.gateway.gateway.ratelimit;

public class RateLimitResult {
    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long resetTimeEpochSeconds;

    public RateLimitResult(boolean allowed, int limit, int remaining, long resetTimeEpochSeconds) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = remaining;
        this.resetTimeEpochSeconds = resetTimeEpochSeconds;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getLimit() {
        return limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getResetTimeEpochSeconds() {
        return resetTimeEpochSeconds;
    }
}
