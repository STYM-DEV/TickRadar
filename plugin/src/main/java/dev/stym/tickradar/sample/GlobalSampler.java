package dev.stym.tickradar.sample;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import dev.stym.tickradar.schedule.TaskScheduler;

public final class GlobalSampler {

    private static final long MIN_STALE_NANOS = 60_000_000_000L;
    static final long GLOBAL_TPS_PERIOD_NANOS = 60_000_000_000L;

    private final SamplingContext context;
    private final RegionMeter meter;
    private final AnchorTracker anchors;
    private final Anchor global;
    private volatile TaskScheduler.Task task;
    private int intervalTicks;

    public GlobalSampler(SamplingContext context, RegionMeter meter, AnchorTracker anchors, Anchor global) {
        this.context = context;
        this.meter = meter;
        this.anchors = anchors;
        this.global = global;
    }

    public void start() {
        intervalTicks = context.settings().get().config().sampling().intervalTicks();
        task = context.scheduler().runGlobalAtFixedRate(this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        TaskScheduler.Task scheduled = task;
        if (scheduled != null) {
            scheduled.cancel();
        }
    }

    private void tick(TaskScheduler.Task running) {
        if (context.isStopping()) {
            running.cancel();
            return;
        }
        long start = System.nanoTime();
        try {
            ConfigSnapshot config = context.settings().get().config();
            if (config.sampling().intervalTicks() != intervalTicks) {
                running.cancel();
                start();
                return;
            }
            measure(config, start);
            for (Anchor expired : anchors.sweep(start, RegionTracker.freshNanos(config),
                    Math.max(MIN_STALE_NANOS, RegionTracker.freshNanos(config)))) {
                RegionTracker.endAlert(expired, context.alerts());
            }
        } catch (Throwable error) {
            context.errors().report("the sampling of the global region", error);
        } finally {
            context.costs().global().record(System.nanoTime() - start);
        }
        readTpsIfDue(start);
    }

    private void readTpsIfDue(long nowNanos) {
        if (!global.claimTpsRead(nowNanos, GLOBAL_TPS_PERIOD_NANOS)) {
            return;
        }
        long start = System.nanoTime();
        try {
            global.publishTps(meter.readCurrentRegionTps(nowNanos));
        } catch (UnsupportedOperationException e) {
            context.errors().warnOnce("global-tps-unavailable", "The TPS of the global region cannot be read on this server ("
                    + e.getMessage() + "); TickRadar shows it as unknown. Please report it with your Folia version.");
        } catch (Throwable error) {
            context.errors().report("the TPS reading of the global region", error);
        } finally {
            context.costs().globalTps().record(System.nanoTime() - start);
        }
    }

    private void measure(ConfigSnapshot config, long nowNanos) {
        int players = context.online().size();
        int capacity = config.sampling().windowCapacity();
        try {
            double mspt = meter.measureGlobalMspt();
            if (ValueFormat.isUsable(mspt)) {
                RegionSnapshot snapshot = global.record(mspt, null, players, nowNanos, capacity);
                RegionTracker.advanceAlert(global, snapshot, config, context.alerts(), nowNanos);
            }
        } catch (UnsupportedOperationException e) {
            global.recordUnavailable(null, players, nowNanos, capacity);
            context.errors().warnOnce("global-unavailable", "The health of the global region cannot be measured on this server ("
                    + e.getMessage() + "); TickRadar shows it as unavailable. Please report it with your Folia version.");
        }
    }
}
