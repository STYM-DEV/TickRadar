package dev.stym.tickradar.display;

enum DisplayTag {
    REGION("region"),
    TPS("tps"),
    TPS_1M("tps_1m"),
    MSPT("mspt"),
    MSPT_AVG("mspt_avg"),
    MSPT_MAX("mspt_max"),
    MSPT_P95("mspt_p95"),
    HISTORY("history"),
    PLAYERS("players");

    private static final char FIRST_MARKER = '\uFDD0';
    private static final DisplayTag[] ALL = values();

    private final String key;

    DisplayTag(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }

    String marker() {
        return String.valueOf((char) (FIRST_MARKER + ordinal()));
    }

    static DisplayTag ofMarker(char character) {
        int index = character - FIRST_MARKER;
        return index >= 0 && index < ALL.length ? ALL[index] : null;
    }
}
