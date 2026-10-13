package com.hobodev.hkeyall;

public final class PlayerTimer {
    private long progressMillis;
    private long requiredMillis;
    private long lastTickMillis;

    public PlayerTimer(long progressMillis, long requiredMillis, long lastTickMillis) {
        this.progressMillis = Math.max(0L, progressMillis);
        this.requiredMillis = Math.max(1L, requiredMillis);
        this.lastTickMillis = lastTickMillis;
    }

    public long progressMillis() {
        return progressMillis;
    }

    public long requiredMillis() {
        return requiredMillis;
    }

    public long remainingMillis() {
        return Math.max(0L, requiredMillis - progressMillis);
    }

    public void start(long nowMillis) {
        lastTickMillis = nowMillis;
    }

    public boolean addElapsed(long nowMillis) {
        if (lastTickMillis <= 0L) {
            lastTickMillis = nowMillis;
            return false;
        }

        progressMillis += Math.max(0L, nowMillis - lastTickMillis);
        lastTickMillis = nowMillis;
        return progressMillis >= requiredMillis;
    }

    public void reset(long requiredMillis, long nowMillis) {
        progressMillis = 0L;
        this.requiredMillis = Math.max(1L, requiredMillis);
        lastTickMillis = nowMillis;
    }
}
