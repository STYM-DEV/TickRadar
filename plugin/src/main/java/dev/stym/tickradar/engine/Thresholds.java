package dev.stym.tickradar.engine;

public record Thresholds(double warning, double critical) {

    public static final Thresholds DEFAULT = new Thresholds(40.0, 50.0);

    public Thresholds {
        if (!isValid(warning, critical)) {
            throw new IllegalArgumentException("Thresholds must satisfy 0 < warning < critical: " + warning + ", " + critical);
        }
    }

    public static boolean isValid(double warning, double critical) {
        return Double.isFinite(warning) && Double.isFinite(critical) && warning > 0 && warning < critical;
    }

    public HealthStatus status(double mspt) {
        if (mspt >= critical) {
            return HealthStatus.CRITICAL;
        }
        if (mspt >= warning) {
            return HealthStatus.WARNING;
        }
        return HealthStatus.OK;
    }
}
