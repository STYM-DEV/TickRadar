package dev.stym.tickradar.alert;

import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AlertKind;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import java.time.Instant;
import java.util.Optional;

public record AlertEvent(
        String regionId,
        AlertKind kind,
        double mspt,
        int players,
        String world,
        Optional<BlockPosition> position,
        Instant at) {

    public static AlertEvent of(AlertKind kind, RegionSnapshot snapshot, Instant at) {
        return new AlertEvent(snapshot.id(), kind, snapshot.mspt5s(), snapshot.players(), snapshot.world(), snapshot.position(), at);
    }

    public boolean isGlobal() {
        return Anchor.GLOBAL_ID.equals(regionId);
    }

    public AlertLevel level() {
        return switch (kind) {
            case WARNING -> AlertLevel.WARNING;
            case CRITICAL -> AlertLevel.CRITICAL;
            case RECOVERED, ENDED -> AlertLevel.RECOVERED;
        };
    }

    public HealthStatus status() {
        return switch (kind) {
            case WARNING -> HealthStatus.WARNING;
            case CRITICAL -> HealthStatus.CRITICAL;
            case RECOVERED, ENDED -> HealthStatus.OK;
        };
    }
}
