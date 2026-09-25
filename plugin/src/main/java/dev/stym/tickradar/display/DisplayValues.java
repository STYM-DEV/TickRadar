package dev.stym.tickradar.display;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;

record DisplayValues(String region, String tps, String tps1m, String mspt, String msptAverage, String msptMax,
                     String msptP95, String history, String players, boolean onePlayer, HealthStatus status) {

    static DisplayValues of(Settings settings, RegionSnapshot snapshot) {
        return new DisplayValues(
                snapshot.id(),
                ValueFormat.tps(snapshot.tps5s()),
                ValueFormat.tps(snapshot.tps1m()),
                ValueFormat.mspt(snapshot.mspt5s()),
                ValueFormat.mspt(snapshot.history().average()),
                ValueFormat.mspt(snapshot.history().max()),
                ValueFormat.mspt(snapshot.history().p95()),
                Integer.toString(settings.config().sampling().historySeconds()),
                Integer.toString(snapshot.players()),
                snapshot.players() == 1,
                snapshot.status(settings.config().thresholds()));
    }

    static DisplayValues markers(HealthStatus status, boolean onePlayer) {
        return new DisplayValues(DisplayTag.REGION.marker(), DisplayTag.TPS.marker(), DisplayTag.TPS_1M.marker(),
                DisplayTag.MSPT.marker(), DisplayTag.MSPT_AVG.marker(), DisplayTag.MSPT_MAX.marker(),
                DisplayTag.MSPT_P95.marker(), DisplayTag.HISTORY.marker(), DisplayTag.PLAYERS.marker(), onePlayer, status);
    }

    DisplayValues withDecimalSeparator(char separator) {
        if (separator == ValueFormat.DECIMAL_POINT) {
            return this;
        }
        return new DisplayValues(region, localized(tps, separator), localized(tps1m, separator), localized(mspt, separator),
                localized(msptAverage, separator), localized(msptMax, separator), localized(msptP95, separator), history,
                players, onePlayer, status);
    }

    private static String localized(String value, char separator) {
        return ValueFormat.withDecimalSeparator(value, separator);
    }

    String text(DisplayTag tag) {
        return switch (tag) {
            case REGION -> region;
            case TPS -> tps;
            case TPS_1M -> tps1m;
            case MSPT -> mspt;
            case MSPT_AVG -> msptAverage;
            case MSPT_MAX -> msptMax;
            case MSPT_P95 -> msptP95;
            case HISTORY -> history;
            case PLAYERS -> players;
        };
    }
}
