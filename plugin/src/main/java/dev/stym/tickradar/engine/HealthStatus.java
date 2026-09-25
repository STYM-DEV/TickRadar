package dev.stym.tickradar.engine;

import java.util.Locale;

public enum HealthStatus {
    OK,
    WARNING,
    CRITICAL,
    UNAVAILABLE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
