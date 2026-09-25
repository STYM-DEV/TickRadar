package dev.stym.tickradar.engine;

import java.util.Optional;

public record RegionSnapshot(
        String id,
        String world,
        Optional<BlockPosition> position,
        int players,
        double tps5s,
        double tps1m,
        double mspt5s,
        RegionStats history,
        long measuredAtNanos,
        boolean unavailable) {

    public boolean isGlobal() {
        return Anchor.GLOBAL_ID.equals(id);
    }

    public HealthStatus status(Thresholds thresholds) {
        return unavailable ? HealthStatus.UNAVAILABLE : thresholds.status(mspt5s);
    }

    public boolean isFresh(long nowNanos, long maxAgeNanos) {
        return nowNanos - measuredAtNanos <= maxAgeNanos;
    }

    public boolean isTpsKnown() {
        return ValueFormat.isUsable(tps5s) && ValueFormat.isUsable(tps1m);
    }

    public RegionSnapshot withTps(TpsReading reading) {
        return new RegionSnapshot(id, world, position, players, reading.tps5s(), reading.tps1m(), mspt5s, history,
                measuredAtNanos, unavailable);
    }
}
