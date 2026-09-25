package dev.stym.tickradar.sample;

import dev.stym.tickradar.alert.AlertEvent;
import dev.stym.tickradar.alert.AlertSink;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.AlertKind;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.Attachment;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.TpsReading;
import dev.stym.tickradar.engine.ValueFormat;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.World;

public final class RegionTracker {

    static final int FRESH_INTERVALS = 3;
    static final long FALLBACK_TPS_PERIOD_NANOS = 60_000_000_000L;
    static final String FALLBACK_TPS_TASK = "the TPS reading of a region";

    private final AnchorTracker anchors;
    private final RegionMeter meter;
    private final FoliaRegionTps regionTps;
    private final Supplier<Settings> settings;
    private final ErrorReporter errors;
    private final SamplingCosts costs;
    private final AlertSink alerts;

    public RegionTracker(AnchorTracker anchors, RegionMeter meter, FoliaRegionTps regionTps, Supplier<Settings> settings,
                         ErrorReporter errors, SamplingCosts costs, AlertSink alerts) {
        this.anchors = anchors;
        this.meter = meter;
        this.regionTps = regionTps;
        this.settings = settings;
        this.errors = errors;
        this.costs = costs;
        this.alerts = alerts;
    }

    public Anchor observe(UUID observer, boolean player, World world, BlockPosition position, long nowNanos) {
        ConfigSnapshot config = settings.get().config();
        String worldName = world.getName();
        regionTps.remember(worldName, world);
        Attachment attachment = anchors.attach(observer, player, worldName, position.chunkX(), position.chunkZ(), nowNanos,
                config.sampling().intervalNanos(), (chunkX, chunkZ) -> Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ));
        if (attachment.measureDue()) {
            measure(attachment.anchor(), position, nowNanos, config);
            readFallbackTpsIfDue(attachment.anchor(), nowNanos);
        }
        return attachment.anchor();
    }

    public void detach(UUID observer) {
        anchors.detach(observer);
    }

    public static long freshNanos(ConfigSnapshot config) {
        return FRESH_INTERVALS * config.sampling().intervalNanos();
    }

    static void advanceAlert(Anchor anchor, RegionSnapshot snapshot, ConfigSnapshot config, AlertSink sink, long nowNanos) {
        if (!config.alerts().enabled() || (snapshot.isGlobal() && !config.alerts().globalRegion())) {
            anchor.resetAlert();
            return;
        }
        Optional<AlertKind> kind = anchor.advanceAlert(snapshot.mspt5s(), config.alertRules(), nowNanos);
        if (kind.isPresent()) {
            sink.accept(AlertEvent.of(kind.get(), snapshot, Instant.now()));
        }
    }

    static void endAlert(Anchor anchor, AlertSink sink) {
        RegionSnapshot snapshot = anchor.snapshot();
        anchor.endAlert().ifPresent(kind -> {
            if (snapshot != null) {
                sink.accept(AlertEvent.of(kind, snapshot, Instant.now()));
            }
        });
    }

    private void measure(Anchor anchor, BlockPosition position, long nowNanos, ConfigSnapshot config) {
        int players = anchor.players(nowNanos, freshNanos(config));
        int capacity = config.sampling().windowCapacity();
        try {
            double mspt = meter.measureRegionMspt();
            if (ValueFormat.isUsable(mspt)) {
                RegionSnapshot snapshot = anchor.record(mspt, position, players, nowNanos, capacity);
                advanceAlert(anchor, snapshot, config, alerts, nowNanos);
            }
        } catch (UnsupportedOperationException e) {
            anchor.recordUnavailable(position, players, nowNanos, capacity);
            errors.warnOnce("region-unavailable", "The health of region " + anchor.id() + " cannot be measured on this server ("
                    + e.getMessage() + "); TickRadar shows it as unavailable. Please report it with your Folia version.");
        }
    }

    private void readFallbackTpsIfDue(Anchor anchor, long nowNanos) {
        if (meter.regionTpsAvailable() || !anchor.claimTpsRead(nowNanos, FALLBACK_TPS_PERIOD_NANOS)) {
            return;
        }
        long start = System.nanoTime();
        try {
            TpsReading reading = meter.readCurrentRegionTps(nowNanos);
            anchor.publishTps(reading);
        } catch (UnsupportedOperationException e) {
            errors.warnOnce("region-tps-unavailable", "The TPS of region " + anchor.id() + " cannot be read on this server ("
                    + e.getMessage() + "); TickRadar shows it as unknown. Please report it with your Folia version.");
        } catch (Throwable error) {
            errors.report(FALLBACK_TPS_TASK, error);
        } finally {
            costs.tpsCall().record(System.nanoTime() - start);
        }
    }
}
