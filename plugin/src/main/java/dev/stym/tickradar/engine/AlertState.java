package dev.stym.tickradar.engine;

public record AlertState(
        HealthStatus level,
        int aboveWarning,
        int aboveCritical,
        int belowWarning,
        boolean emitted,
        boolean pending,
        long lastWarningNanos,
        long lastCriticalNanos) {

    public static final long NEVER = Long.MIN_VALUE;
    public static final AlertState INITIAL = new AlertState(HealthStatus.OK, 0, 0, 0, false, false, NEVER, NEVER);

    public boolean isActive() {
        return level != HealthStatus.OK;
    }

    boolean isAnnounced() {
        return isActive() && !pending;
    }

    boolean isCoolingDown(HealthStatus alertLevel, long nowNanos, long cooldownNanos) {
        long last = alertLevel == HealthStatus.CRITICAL ? lastCriticalNanos : lastWarningNanos;
        return last != NEVER && nowNanos - last < cooldownNanos;
    }

    boolean isTriggered(HealthStatus alertLevel, int triggerSamples) {
        int slowSamples = alertLevel == HealthStatus.CRITICAL ? aboveCritical : aboveWarning;
        return slowSamples >= triggerSamples;
    }
}
