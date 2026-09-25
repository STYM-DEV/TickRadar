package dev.stym.tickradar.engine;

public record AlertRules(Thresholds thresholds, int triggerSamples, int recoverySamples, long cooldownNanos) {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public AlertRules {
        if (triggerSamples < 1 || recoverySamples < 1 || cooldownNanos < 0) {
            throw new IllegalArgumentException("Alert rules must have at least one sample and no negative cooldown");
        }
    }

    public static AlertRules of(Thresholds thresholds, int triggerSamples, int recoverySamples, int cooldownSeconds) {
        return new AlertRules(thresholds, triggerSamples, recoverySamples, cooldownSeconds * NANOS_PER_SECOND);
    }
}
