package dev.stym.tickradar.alert.discord;

import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

final class RateLimitState {

    static final long MIN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    static final long MAX_WAIT_NANOS = TimeUnit.SECONDS.toNanos(60);
    static final long DEFAULT_RETRY_NANOS = TimeUnit.SECONDS.toNanos(1);
    static final String REMAINING = "X-RateLimit-Remaining";
    static final String RESET_AFTER = "X-RateLimit-Reset-After";
    static final String RETRY_AFTER = "Retry-After";
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private boolean hasSent;
    private long lastSendAt;
    private boolean hasBlock;
    private long blockedUntil;

    synchronized long delayBeforeSend(long now) {
        long floor = hasSent ? lastSendAt + MIN_INTERVAL_NANOS - now : 0;
        long blocked = hasBlock ? blockedUntil - now : 0;
        return Math.max(0, Math.max(floor, blocked));
    }

    synchronized void recordSend(long now) {
        hasSent = true;
        lastSendAt = now;
    }

    synchronized void recordReply(HttpReply reply, long now) {
        OptionalDouble remaining = seconds(reply, REMAINING);
        OptionalDouble resetAfter = seconds(reply, RESET_AFTER);
        if (remaining.isPresent() && remaining.getAsDouble() <= 0 && resetAfter.isPresent()) {
            blockUntil(now + clampedNanos(resetAfter.getAsDouble()));
        }
    }

    synchronized long recordTooManyRequests(HttpReply reply, long now) {
        OptionalDouble retryAfter = Json.number(reply.body(), "retry_after");
        if (retryAfter.isEmpty()) {
            retryAfter = seconds(reply, RETRY_AFTER);
        }
        if (retryAfter.isEmpty()) {
            retryAfter = seconds(reply, RESET_AFTER);
        }
        long wait = retryAfter.isPresent() ? clampedNanos(retryAfter.getAsDouble()) : DEFAULT_RETRY_NANOS;
        blockUntil(now + wait);
        return wait;
    }

    synchronized void reset() {
        hasSent = false;
        hasBlock = false;
    }

    synchronized boolean isLimited(long now) {
        return hasBlock && blockedUntil - now > 0;
    }

    private void blockUntil(long until) {
        if (!hasBlock || until - blockedUntil > 0) {
            blockedUntil = until;
            hasBlock = true;
        }
    }

    private static long clampedNanos(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return 0;
        }
        return (long) Math.ceil(Math.min(seconds * NANOS_PER_SECOND, MAX_WAIT_NANOS));
    }

    private static OptionalDouble seconds(HttpReply reply, String header) {
        return reply.header(header).map(String::trim).map(RateLimitState::parse).orElse(OptionalDouble.empty());
    }

    private static OptionalDouble parse(String value) {
        try {
            return OptionalDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
