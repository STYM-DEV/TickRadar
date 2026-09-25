package dev.stym.tickradar.sample;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.Attachment;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.CostMeter;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.TickPeak;
import dev.stym.tickradar.player.PermissionCache;
import java.util.UUID;

public final class SamplingWarmUp {

    static final String WORLD = "tickradar-warm-up";
    static final int SAMPLES = 4;

    private static final BlockPosition POSITION = new BlockPosition(0, 64, 0);

    private SamplingWarmUp() {
    }

    public static void run(ConfigSnapshot config) {
        AnchorTracker anchors = new AnchorTracker();
        UUID observer = new UUID(0L, 0L);
        PermissionCache permissions = new PermissionCache(permission -> true);
        long intervalNanos = config.sampling().intervalNanos();
        long now = System.nanoTime();
        CostMeter costs = new CostMeter();
        TickPeak peak = new TickPeak(now, SamplingCosts.TICK_PEAK_WINDOW_NANOS);
        for (int sample = 0; sample < SAMPLES; sample++) {
            long start = System.nanoTime();
            permissions.next();
            Attachment attachment = anchors.attach(observer, true, WORLD, 0, 0, now, intervalNanos, (chunkX, chunkZ) -> true);
            Anchor anchor = attachment.anchor();
            peak.record(anchor.countSampleOnTick(sample), now);
            int players = anchor.players(now, RegionTracker.freshNanos(config));
            RegionSnapshot snapshot = anchor.record(msptOf(config, sample), POSITION, players, now,
                    config.sampling().windowCapacity());
            anchor.advanceAlert(snapshot.mspt5s(), config.alertRules(), now);
            snapshot.status(config.thresholds());
            costs.record(System.nanoTime() - start);
            now += intervalNanos;
        }
        permissions.refresh();
        anchors.detach(observer);
    }

    private static double msptOf(ConfigSnapshot config, int sample) {
        return sample % 2 == 0 ? config.thresholds().critical() : config.thresholds().warning() / 2;
    }
}
