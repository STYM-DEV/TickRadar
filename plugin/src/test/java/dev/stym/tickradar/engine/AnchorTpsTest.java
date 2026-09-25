package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AnchorTpsTest {

    private static final int CAPACITY = 60;
    private static final BlockPosition POSITION = new BlockPosition(16, 64, 16);

    private final Anchor anchor = Anchor.region(1, "world", 1, 1, 0);

    @Test
    void tpsIsUnknownUntilTheFirstReading() {
        RegionSnapshot snapshot = anchor.record(12, POSITION, 1, 5, CAPACITY);
        assertFalse(snapshot.isTpsKnown());
        assertEquals(ValueFormat.MISSING, ValueFormat.tps(snapshot.tps5s()));
        assertEquals(ValueFormat.MISSING, ValueFormat.tps(snapshot.tps1m()));
        assertEquals(Optional.empty(), anchor.tps());
    }

    @Test
    void aReadingIsPublishedIntoTheSnapshotWithoutTouchingTheMspt() {
        RegionSnapshot measured = anchor.record(30, POSITION, 2, 5, CAPACITY);
        assertTrue(anchor.publishTps(new TpsReading(19.5, 19.8, 7)));
        RegionSnapshot published = anchor.snapshot();
        assertEquals(19.5, published.tps5s());
        assertEquals(19.8, published.tps1m());
        assertTrue(published.isTpsKnown());
        assertEquals(30, published.mspt5s());
        assertEquals(5, published.measuredAtNanos());
        assertEquals(measured.history(), published.history());
        assertEquals(measured.position(), published.position());
        assertEquals(2, published.players());
        assertEquals(measured, published.withTps(new TpsReading(Double.NaN, Double.NaN, 0)));
    }

    @Test
    void aReadingBeforeTheFirstMsptIsKeptForTheFirstSnapshot() {
        assertTrue(anchor.publishTps(new TpsReading(18, 19, 1)));
        assertNull(anchor.snapshot());
        RegionSnapshot snapshot = anchor.record(25, POSITION, 1, 2, CAPACITY);
        assertEquals(18, snapshot.tps5s());
        assertEquals(19, snapshot.tps1m());
        assertEquals(25, snapshot.mspt5s());
    }

    @Test
    void aNewMsptKeepsTheLastKnownTps() {
        anchor.record(10, POSITION, 1, 1, CAPACITY);
        anchor.publishTps(new TpsReading(17, 18, 2));
        RegionSnapshot next = anchor.record(40, POSITION, 1, 3, CAPACITY);
        assertEquals(17, next.tps5s());
        assertEquals(40, next.mspt5s());
        assertSame(next, anchor.snapshot());
    }

    @Test
    void anOlderReadingNeverReplacesANewerOne() {
        anchor.record(10, POSITION, 1, 1, CAPACITY);
        assertTrue(anchor.publishTps(new TpsReading(15, 15, 200)));
        assertFalse(anchor.publishTps(new TpsReading(20, 20, 100)));
        assertEquals(15, anchor.snapshot().tps5s());
    }

    @Test
    void anInvalidReadingIsIgnored() {
        anchor.record(10, POSITION, 1, 1, CAPACITY);
        assertFalse(anchor.publishTps(new TpsReading(Double.NaN, 20, 2)));
        assertFalse(anchor.publishTps(new TpsReading(20, -1, 3)));
        assertFalse(anchor.snapshot().isTpsKnown());
    }

    @Test
    void aDeadAnchorIsNeitherUpdatedNorRevived() {
        RegionSnapshot before = anchor.record(10, POSITION, 1, 1, CAPACITY);
        assertTrue(anchor.kill());
        assertFalse(anchor.publishTps(new TpsReading(20, 20, 2)));
        assertSame(before, anchor.snapshot());
        assertTrue(anchor.isDead());
    }

    @Test
    void theUnavailableFlagKeepsTheLastKnownTps() {
        anchor.publishTps(new TpsReading(19, 19, 1));
        RegionSnapshot snapshot = anchor.recordUnavailable(POSITION, 1, 2, CAPACITY);
        assertTrue(snapshot.unavailable());
        assertEquals(19, snapshot.tps5s());
        assertEquals(HealthStatus.UNAVAILABLE, snapshot.status(Thresholds.DEFAULT));
    }

    @Test
    void aTpsReadIsClaimedOncePerPeriod() {
        assertTrue(anchor.claimTpsRead(100, 60));
        assertFalse(anchor.claimTpsRead(159, 60));
        assertTrue(anchor.claimTpsRead(160, 60));
        assertFalse(anchor.claimTpsRead(161, 60));
    }

    @Test
    void theChunkIsReadAsOnePair() {
        anchor.moveTo(-42, 7);
        assertEquals(new ChunkPos(-42, 7), anchor.chunk());
    }

    @Test
    void neitherWriterLosesTheOtherOnesLastValue() throws InterruptedException {
        int rounds = 20_000;
        AtomicInteger lostMspt = new AtomicInteger();
        AtomicInteger lostTps = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread region = new Thread(() -> {
            await(go);
            for (int i = 1; i <= rounds; i++) {
                anchor.record(i, POSITION, 1, i, CAPACITY);
                if (anchor.snapshot().mspt5s() != i) {
                    lostMspt.incrementAndGet();
                }
            }
        });
        Thread poller = new Thread(() -> {
            await(go);
            for (int i = 1; i <= rounds; i++) {
                anchor.publishTps(new TpsReading(i, i, i));
                RegionSnapshot snapshot = anchor.snapshot();
                if (snapshot != null && snapshot.tps1m() != i) {
                    lostTps.incrementAndGet();
                }
            }
        });
        region.start();
        poller.start();
        go.countDown();
        region.join();
        poller.join();
        assertEquals(0, lostMspt.get());
        assertEquals(0, lostTps.get());
        assertEquals(rounds, anchor.snapshot().mspt5s());
        assertEquals(rounds, anchor.snapshot().tps1m());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
