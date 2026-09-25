package dev.stym.tickradar.engine;

import java.util.Locale;

public enum AlertKind {
    WARNING,
    CRITICAL,
    RECOVERED,
    ENDED;

    public boolean isSlow() {
        return this == WARNING || this == CRITICAL;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
