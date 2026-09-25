package dev.stym.tickradar.sample;

import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.ChunkPos;
import dev.stym.tickradar.engine.CostMeter;
import dev.stym.tickradar.engine.TpsReading;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class RegionTpsPoller {

    public static final String THREAD_NAME = "TickRadar-RegionTPS";
    static final String PASS_TASK = "the region TPS thread";
    static final String READ_TASK = "the reading of a region TPS";
    private static final int TPS_5S = 0;
    private static final int TPS_1M = 2;
    private static final long STOP_WAIT_MILLIS = 2_000;

    private final AnchorTracker anchors;
    private final RegionTpsSource source;
    private final CostMeter readCost;
    private final CostMeter passCost;
    private final ErrorReporter errors;
    private final Supplier<Duration> interval;
    private final LongSupplier clock;
    private ScheduledExecutorService executor;
    private volatile Thread worker;
    private boolean stopped;

    public RegionTpsPoller(AnchorTracker anchors, RegionTpsSource source, SamplingCosts costs, ErrorReporter errors,
                           Supplier<Duration> interval) {
        this(anchors, source, costs.tpsCall(), costs.tpsThread(), errors, interval, System::nanoTime);
    }

    RegionTpsPoller(AnchorTracker anchors, RegionTpsSource source, CostMeter readCost, CostMeter passCost, ErrorReporter errors,
                    Supplier<Duration> interval, LongSupplier clock) {
        this.anchors = anchors;
        this.source = source;
        this.readCost = readCost;
        this.passCost = passCost;
        this.errors = errors;
        this.interval = interval;
        this.clock = clock;
    }

    public synchronized void start() {
        if (stopped || executor != null) {
            return;
        }
        long periodNanos = interval.get().toNanos();
        executor = Executors.newSingleThreadScheduledExecutor(this::newWorker);
        executor.scheduleWithFixedDelay(this::runPass, periodNanos, periodNanos, TimeUnit.NANOSECONDS);
    }

    public synchronized void restart() {
        if (stopped) {
            return;
        }
        shutdown();
        start();
    }

    public synchronized void stop() {
        stopped = true;
        shutdown();
    }

    public synchronized boolean isRunning() {
        return executor != null;
    }

    void runPass() {
        try {
            readEveryAnchor();
        } catch (Throwable error) {
            reportSafely(PASS_TASK, error);
        }
    }

    private void readEveryAnchor() {
        long start = clock.getAsLong();
        int read = 0;
        try {
            for (Anchor anchor : anchors.anchors()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                read(anchor);
                read++;
            }
        } finally {
            if (read > 0) {
                passCost.record(clock.getAsLong() - start);
            }
        }
    }

    private void read(Anchor anchor) {
        long start = clock.getAsLong();
        try {
            ChunkPos chunk = anchor.chunk();
            double[] tps = source.regionTps(anchor.world(), chunk.x(), chunk.z());
            long readAt = clock.getAsLong();
            readCost.record(readAt - start);
            if (tps != null && tps.length > TPS_1M) {
                anchor.publishTps(new TpsReading(tps[TPS_5S], tps[TPS_1M], readAt));
            }
        } catch (Throwable error) {
            reportSafely(READ_TASK, error);
        }
    }

    private void reportSafely(String task, Throwable error) {
        try {
            errors.report(task, error);
        } catch (Throwable ignored) {
        }
    }

    private void shutdown() {
        ScheduledExecutorService running = executor;
        executor = null;
        if (running == null) {
            return;
        }
        running.shutdownNow();
        Thread last = worker;
        worker = null;
        try {
            if (running.awaitTermination(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS) && last != null) {
                last.join(STOP_WAIT_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Thread newWorker(Runnable task) {
        Thread thread = new Thread(task, THREAD_NAME);
        thread.setDaemon(true);
        worker = thread;
        return thread;
    }
}
