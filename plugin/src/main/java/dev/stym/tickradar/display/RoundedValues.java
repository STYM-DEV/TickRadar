package dev.stym.tickradar.display;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import dev.stym.tickradar.engine.ValueFormat;

final class RoundedValues {

    private boolean known;
    private String region;
    private long tps;
    private long tps1m;
    private long mspt;
    private long msptAverage;
    private long msptMax;
    private long msptP95;
    private int history;
    private int players;
    private HealthStatus status;

    boolean changeTo(Settings settings, RegionSnapshot snapshot) {
        RegionStats stats = snapshot.history();
        long nextTps = ValueFormat.tpsTenths(snapshot.tps5s());
        long nextTps1m = ValueFormat.tpsTenths(snapshot.tps1m());
        long nextMspt = ValueFormat.msptTenths(snapshot.mspt5s());
        long nextAverage = ValueFormat.msptTenths(stats.average());
        long nextMax = ValueFormat.msptTenths(stats.max());
        long nextP95 = ValueFormat.msptTenths(stats.p95());
        int nextHistory = settings.config().sampling().historySeconds();
        HealthStatus nextStatus = snapshot.status(settings.config().thresholds());
        if (known && nextTps == tps && nextTps1m == tps1m && nextMspt == mspt && nextAverage == msptAverage
                && nextMax == msptMax && nextP95 == msptP95 && nextHistory == history && snapshot.players() == players
                && nextStatus == status && snapshot.id().equals(region)) {
            return false;
        }
        known = true;
        region = snapshot.id();
        tps = nextTps;
        tps1m = nextTps1m;
        mspt = nextMspt;
        msptAverage = nextAverage;
        msptMax = nextMax;
        msptP95 = nextP95;
        history = nextHistory;
        players = snapshot.players();
        status = nextStatus;
        return true;
    }
}
