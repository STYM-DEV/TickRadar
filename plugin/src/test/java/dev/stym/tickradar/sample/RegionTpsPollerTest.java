package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.CostMeter;
import dev.stym.tickradar.engine.OwnershipProbe;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RegionTpsPollerTest {

    private static final String WORLD = "world";
    private static final long INTERVAL = 1_000_000_000L;
    private static final OwnershipProbe NOTHING_OWNED = (chunkX, chunkZ) -> false;
    private static final double[] HEALTHY = {19.5, 19.7, 19.8, 19.9, 20.0};
    private static final Duration FAST = Duration.ofMillis(5);
    private static final long WAIT_MILLIS = 5_000;

    private final AnchorTracker tracker = new AnchorTracker();
    private final CostMeter readCost = new CostMeter();
    private final CostMeter passCost = new CostMeter();
    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final ErrorReporter errors = new ErrorReporter(capturingLogger());
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<RegionTpsPoller> started = new ArrayList<>();

    @AfterEach
    void stopEveryPoller() {
        started.forEach(RegionTpsPoller::stop);
    }

    @Test
    void aPassPublishesTheTpsOfEveryAnchor() {
        Anchor first = anchorAt(1, 1);
        Anchor second = anchorAt(40, -3);
        first.record(20, new BlockPosition(16, 64, 16), 1, 0, 60);
        poller((world, chunkX, chunkZ) -> HEALTHY).runPass();
        assertEquals(19.5, first.snapshot().tps5s());
        assertEquals(19.8, first.snapshot().tps1m());
        assertEquals(20, first.snapshot().mspt5s());
        assertEquals(19.5, second.tps().orElseThrow().tps5s());
        assertEquals(2, readCost.summary().count());
        assertEquals(1, passCost.summary().count());
    }

    @Test
    void theAnchorPositionIsAskedToTheSource() {
        anchorAt(-7, 12);
        List<String> asked = new ArrayList<>();
        poller((world, chunkX, chunkZ) -> {
            asked.add(world + ":" + chunkX + ":" + chunkZ);
            return HEALTHY;
        }).runPass();
        assertEquals(List.of("world:-7:12"), asked);
    }

    @Test
    void noReadingLeavesTheTpsUnknown() {
        Anchor anchor = anchorAt(1, 1);
        anchor.record(20, null, 1, 0, 60);
        poller((world, chunkX, chunkZ) -> null).runPass();
        poller((world, chunkX, chunkZ) -> new double[] {20}).runPass();
        RegionSnapshot snapshot = anchor.snapshot();
        assertFalse(snapshot.isTpsKnown());
        assertEquals(ValueFormat.MISSING, ValueFormat.tps(snapshot.tps5s()));
        assertEquals(Optional.empty(), anchor.tps());
    }

    @Test
    void aMissingReadingKeepsTheLastKnownValue() {
        Anchor anchor = anchorAt(1, 1);
        poller((world, chunkX, chunkZ) -> HEALTHY).runPass();
        poller((world, chunkX, chunkZ) -> null).runPass();
        assertEquals(19.5, anchor.tps().orElseThrow().tps5s());
    }

    @Test
    void anAnchorThatExpiresDuringTheReadIsNotUpdatedNorRevived() {
        UUID observer = UUID.randomUUID();
        Anchor anchor = tracker.attach(observer, true, WORLD, 1, 1, 0, INTERVAL, NOTHING_OWNED).anchor();
        anchor.record(20, null, 1, 0, 60);
        poller((world, chunkX, chunkZ) -> {
            tracker.detach(observer);
            tracker.sweep(Long.MAX_VALUE / 2, 0, 0);
            return HEALTHY;
        }).runPass();
        assertTrue(anchor.isDead());
        assertFalse(anchor.snapshot().isTpsKnown());
        assertEquals(Optional.empty(), tracker.find(anchor.id()));
        assertEquals(List.of(), tracker.anchors());
    }

    @Test
    void anAnchorAlreadyExpiredIsNotRead() {
        UUID observer = UUID.randomUUID();
        tracker.attach(observer, true, WORLD, 1, 1, 0, INTERVAL, NOTHING_OWNED);
        tracker.detach(observer);
        tracker.sweep(Long.MAX_VALUE / 2, 0, 0);
        AtomicInteger reads = new AtomicInteger();
        poller((world, chunkX, chunkZ) -> {
            reads.incrementAndGet();
            return HEALTHY;
        }).runPass();
        assertEquals(0, reads.get());
        assertEquals(0, passCost.summary().count());
    }

    @Test
    void aFailingReadIsReportedOnceAndTheOtherAnchorsAreStillRead() {
        Anchor broken = anchorAt(1, 1);
        Anchor healthy = anchorAt(50, 50);
        RegionTpsPoller poller = poller((world, chunkX, chunkZ) -> {
            if (chunkX == 1) {
                throw new IllegalStateException("boom");
            }
            return HEALTHY;
        });
        poller.runPass();
        poller.runPass();
        poller.runPass();
        assertEquals(Optional.empty(), broken.tps());
        assertEquals(19.5, healthy.tps().orElseThrow().tps5s());
        assertEquals(1, warnings());
        assertTrue(logged.getFirst().getMessage().contains(RegionTpsPoller.READ_TASK));
    }

    @Test
    void theThreadIsNamedDaemonAndStopsWithThePlugin() throws InterruptedException {
        anchorAt(1, 1);
        AtomicInteger reads = new AtomicInteger();
        RegionTpsPoller poller = started(countingReads(reads));
        poller.start();
        assertTrue(poller.isRunning());
        waitUntil(() -> reads.get() >= 2);
        Thread thread = pollerThreads().getFirst();
        assertTrue(thread.isDaemon());
        poller.stop();
        assertFalse(poller.isRunning());
        assertFalse(thread.isAlive());
        assertEquals(0, pollerThreads().size());
    }

    @Test
    void stopIsIdempotentAndFinal() {
        RegionTpsPoller poller = started((world, chunkX, chunkZ) -> HEALTHY);
        poller.start();
        assertEquals(1, pollerThreads().size());
        poller.stop();
        poller.stop();
        poller.start();
        poller.stop();
        poller.restart();
        poller.stop();
        assertFalse(poller.isRunning());
        assertEquals(0, pollerThreads().size());
    }

    @Test
    void aPollerStoppedBeforeItsStartNeverStarts() {
        RegionTpsPoller poller = started((world, chunkX, chunkZ) -> HEALTHY);
        poller.stop();
        poller.start();
        assertFalse(poller.isRunning());
        assertEquals(0, pollerThreads().size());
    }

    @Test
    void reloadsRestartTheThreadWithoutLeakingOne() throws InterruptedException {
        anchorAt(1, 1);
        AtomicInteger reads = new AtomicInteger();
        RegionTpsPoller poller = started(countingReads(reads));
        poller.start();
        for (int i = 0; i < 10; i++) {
            poller.restart();
            assertTrue(pollerThreads().size() <= 1);
        }
        int before = reads.get();
        waitUntil(() -> reads.get() > before + 1);
        assertEquals(1, pollerThreads().size());
        poller.stop();
        assertEquals(0, pollerThreads().size());
    }

    @Test
    void theThreadSurvivesFailingReadsAndKeepsReadingTheOthers() throws InterruptedException {
        anchorAt(1, 1);
        Anchor healthy = anchorAt(50, 50);
        AtomicInteger failures = new AtomicInteger();
        RegionTpsPoller poller = started((world, chunkX, chunkZ) -> {
            if (chunkX == 1) {
                failures.incrementAndGet();
                throw new LinkageError("broken read");
            }
            return HEALTHY;
        });
        poller.start();
        waitUntil(() -> failures.get() >= 3 && healthy.tps().isPresent());
        assertTrue(poller.isRunning());
        assertEquals(1, pollerThreads().size());
        assertEquals(1, warnings());
    }

    @Test
    void aReporterThatFailsDoesNotEscapeThePass() {
        Anchor broken = anchorAt(1, 1);
        Anchor healthy = anchorAt(50, 50);
        List<Integer> readOrder = new CopyOnWriteArrayList<>();
        AtomicInteger published = new AtomicInteger();
        RegionTpsSource brokenFirst = (world, chunkX, chunkZ) -> {
            readOrder.add(chunkX);
            if (chunkX == 1) {
                throw new IllegalStateException("broken read");
            }
            return HEALTHY;
        };
        RegionTpsPoller poller = new RegionTpsPoller(tracker, brokenFirst, readCost, passCost,
                new ErrorReporter(throwingLogger(published)), () -> FAST, () -> clock.getAndAdd(10));
        poller.runPass();
        assertEquals(List.of(1, 50), readOrder);
        assertEquals(1, published.get());
        assertEquals(Optional.empty(), broken.tps());
        assertEquals(19.5, healthy.tps().orElseThrow().tps5s());
        assertEquals(1, passCost.summary().count());
    }

    @Test
    void aReporterThatFailsDoesNotStopTheNextPasses() throws InterruptedException {
        anchorAt(1, 1);
        AtomicInteger failures = new AtomicInteger();
        RegionTpsPoller poller = new RegionTpsPoller(tracker, failingOn(1, failures), readCost, passCost,
                new ErrorReporter(throwingLogger(new AtomicInteger())), () -> FAST, () -> clock.getAndAdd(10));
        started.add(poller);
        poller.start();
        waitUntil(() -> failures.get() >= 5);
        assertTrue(poller.isRunning());
        assertEquals(1, pollerThreads().size());
    }

    private static RegionTpsSource failingOn(int failingChunkX, AtomicInteger failures) {
        return (world, chunkX, chunkZ) -> {
            if (chunkX == failingChunkX) {
                failures.incrementAndGet();
                throw new IllegalStateException("broken read");
            }
            return HEALTHY;
        };
    }

    private static Logger throwingLogger(AtomicInteger published) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                published.incrementAndGet();
                throw new IllegalStateException("broken logger");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private RegionTpsPoller poller(RegionTpsSource source) {
        return new RegionTpsPoller(tracker, source, readCost, passCost, errors, () -> FAST, () -> clock.getAndAdd(10));
    }

    private RegionTpsPoller started(RegionTpsSource source) {
        RegionTpsPoller poller = poller(source);
        started.add(poller);
        return poller;
    }

    private static RegionTpsSource countingReads(AtomicInteger reads) {
        return (world, chunkX, chunkZ) -> {
            reads.incrementAndGet();
            return HEALTHY;
        };
    }

    private Anchor anchorAt(int chunkX, int chunkZ) {
        return tracker.attach(UUID.randomUUID(), true, WORLD, chunkX, chunkZ, 0, INTERVAL, NOTHING_OWNED).anchor();
    }

    private long warnings() {
        return logged.stream().filter(entry -> entry.getLevel() == Level.WARNING).count();
    }

    private static List<Thread> pollerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(RegionTpsPoller.THREAD_NAME) && thread.isAlive())
                .toList();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within " + WAIT_MILLIS + " ms");
            }
            Thread.sleep(5);
        }
    }

    private Logger capturingLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }
}
