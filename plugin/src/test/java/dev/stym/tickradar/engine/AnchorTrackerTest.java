package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AnchorTrackerTest {

    private static final String WORLD = "world";
    private static final long INTERVAL = 1_000_000_000L;
    private static final OwnershipProbe WEST = (chunkX, chunkZ) -> chunkX < 100;
    private static final OwnershipProbe EAST = (chunkX, chunkZ) -> chunkX >= 100;
    private static final OwnershipProbe EVERYTHING = (chunkX, chunkZ) -> true;

    private final AnchorTracker tracker = new AnchorTracker();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void firstObserverCreatesAnAnchorAndMeasures() {
        Attachment attachment = tracker.attach(alice, true, WORLD, 3, 4, 0, INTERVAL, WEST);
        assertEquals("R1", attachment.anchor().id());
        assertEquals(3, attachment.anchor().chunkX());
        assertEquals(4, attachment.anchor().chunkZ());
        assertTrue(attachment.measureDue());
        assertTrue(attachment.merged().isEmpty());
        assertEquals(Optional.of(attachment.anchor()), tracker.find("R1"));
    }

    @Test
    void observersOfTheSameRegionShareTheAnchor() {
        Anchor first = tracker.attach(alice, true, WORLD, 1, 1, 0, INTERVAL, WEST).anchor();
        Anchor second = tracker.attach(bob, true, WORLD, 50, -20, 10, INTERVAL, WEST).anchor();
        assertSame(first, second);
        assertEquals(2, first.players(10, INTERVAL));
        assertEquals(1, tracker.size());
    }

    @Test
    void theAnchorFollowsItsObservers() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 1, 1, 0, INTERVAL, WEST).anchor();
        tracker.attach(alice, true, WORLD, 42, -7, 1, INTERVAL, WEST);
        assertEquals(42, anchor.chunkX());
        assertEquals(-7, anchor.chunkZ());
    }

    @Test
    void negativeChunksArePackedWithoutLoss() {
        Anchor anchor = tracker.attach(alice, true, WORLD, -1_875_000, -1, 0, INTERVAL, WEST).anchor();
        assertEquals(-1_875_000, anchor.chunkX());
        assertEquals(-1, anchor.chunkZ());
    }

    @Test
    void differentWorldsNeverShareAnAnchor() {
        Anchor overworld = tracker.attach(alice, true, WORLD, 1, 1, 0, INTERVAL, EVERYTHING).anchor();
        Anchor nether = tracker.attach(bob, true, "world_nether", 1, 1, 0, INTERVAL, EVERYTHING).anchor();
        assertNotEquals(overworld.id(), nether.id());
    }

    @Test
    void measureIsDueOncePerInterval() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 1, 1, 0, INTERVAL, WEST).anchor();
        anchor.record(10, new BlockPosition(16, 64, 16), 1, 0, 60);
        assertFalse(tracker.attach(bob, true, WORLD, 1, 1, INTERVAL / 2, INTERVAL, WEST).measureDue());
        assertTrue(tracker.attach(bob, true, WORLD, 1, 1, INTERVAL * 9 / 10, INTERVAL, WEST).measureDue());
    }

    @Test
    void recordPublishesAnImmutableSnapshot() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 1, 1, 0, INTERVAL, WEST).anchor();
        anchor.publishTps(new TpsReading(18, 19, 4));
        anchor.record(30, new BlockPosition(20, 70, 30), 1, 5, 3);
        RegionSnapshot first = anchor.record(50, new BlockPosition(20, 70, 30), 2, 6, 3);
        assertEquals("R1", first.id());
        assertEquals(WORLD, first.world());
        assertEquals(Optional.of(new BlockPosition(20, 70, 30)), first.position());
        assertEquals(2, first.players());
        assertEquals(50, first.mspt5s());
        assertEquals(18, first.tps5s());
        assertEquals(19, first.tps1m());
        assertEquals(40, first.history().average(), 1e-9);
        assertEquals(50, first.history().max());
        assertEquals(6, first.measuredAtNanos());
        assertSame(first, anchor.snapshot());
        assertEquals(HealthStatus.CRITICAL, first.status(Thresholds.DEFAULT));
    }

    @Test
    void unavailableMeasurementIsFlaggedAndKeepsTheHistory() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 1, 1, 0, INTERVAL, WEST).anchor();
        anchor.record(30, null, 1, 0, 60);
        RegionSnapshot snapshot = anchor.recordUnavailable(null, 1, INTERVAL, 60);
        assertTrue(snapshot.unavailable());
        assertEquals(HealthStatus.UNAVAILABLE, snapshot.status(Thresholds.DEFAULT));
        assertEquals(1, snapshot.history().count());
        assertFalse(tracker.attach(alice, true, WORLD, 1, 1, INTERVAL + 1, INTERVAL, WEST).measureDue());
    }

    @Test
    void mergedRegionsKeepTheOldestAnchor() {
        Anchor west = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        Anchor east = tracker.attach(bob, true, WORLD, 200, 0, 0, INTERVAL, EAST).anchor();
        Attachment merged = tracker.attach(bob, true, WORLD, 200, 0, 1, INTERVAL, EVERYTHING);
        assertSame(west, merged.anchor());
        assertEquals(List.of(east), merged.merged());
        assertTrue(east.isDead());
        assertEquals(Optional.empty(), tracker.find(east.id()));
        assertEquals(1, tracker.anchors().size());
        assertEquals(Optional.of(west), tracker.anchorOf(bob));
        assertEquals(2, west.players(1, INTERVAL));
    }

    @Test
    void theKeptAnchorTakesTheWorstAlertOfAMerge() {
        AlertRules rules = AlertRules.of(Thresholds.DEFAULT, 1, 1, 0);
        Anchor west = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        Anchor east = tracker.attach(bob, true, WORLD, 200, 0, 0, INTERVAL, EAST).anchor();
        assertEquals(Optional.of(AlertKind.CRITICAL), east.advanceAlert(60, rules, 1));
        tracker.attach(bob, true, WORLD, 200, 0, 1, INTERVAL, EVERYTHING);
        assertEquals(HealthStatus.CRITICAL, west.alertState().level());
        assertEquals(Optional.of(AlertKind.RECOVERED), west.advanceAlert(10, rules, 2));
    }

    @Test
    void anObserverWhoseRegionSplitAwayCreatesANewAnchor() {
        Anchor original = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        tracker.attach(bob, true, WORLD, 20, 0, 0, INTERVAL, WEST);
        Anchor split = tracker.attach(bob, true, WORLD, 150, 0, 1, INTERVAL, EAST).anchor();
        assertNotEquals(original.id(), split.id());
        assertEquals(1, original.players(1, INTERVAL));
        assertEquals(1, split.players(1, INTERVAL));
        assertEquals(2, tracker.size());
    }

    @Test
    void identifiersAreNeverReused() {
        Anchor first = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        tracker.detach(alice);
        assertEquals(List.of(first), tracker.sweep(10 * INTERVAL, 3 * INTERVAL, 60 * INTERVAL));
        Anchor second = tracker.attach(alice, true, WORLD, 10, 0, 11 * INTERVAL, INTERVAL, WEST).anchor();
        assertEquals("R2", second.id());
    }

    @Test
    void anAnchorExpiresOnlyAfterItsLastObserverLeftForLongEnough() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        assertTrue(tracker.sweep(100 * INTERVAL, 3 * INTERVAL, 1_000 * INTERVAL).isEmpty());
        tracker.detach(alice);
        assertTrue(tracker.sweep(2 * INTERVAL, 3 * INTERVAL, 1_000 * INTERVAL).isEmpty());
        assertEquals(List.of(anchor), tracker.sweep(4 * INTERVAL, 3 * INTERVAL, 1_000 * INTERVAL));
        assertTrue(anchor.isDead());
        assertTrue(tracker.anchors().isEmpty());
        assertEquals(0, tracker.size());
    }

    @Test
    void staleObserversArePrunedSoTheirAnchorCanExpire() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        assertTrue(tracker.sweep(30 * INTERVAL, 3 * INTERVAL, 60 * INTERVAL).isEmpty());
        assertEquals(List.of(anchor), tracker.sweep(61 * INTERVAL, 3 * INTERVAL, 60 * INTERVAL));
    }

    @Test
    void syntheticObserversAreNotCountedAsPlayers() {
        Anchor anchor = tracker.attach(alice, false, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        tracker.attach(bob, true, WORLD, 10, 0, 0, INTERVAL, WEST);
        assertEquals(1, anchor.players(0, INTERVAL));
    }

    @Test
    void playersSeenTooLongAgoAreNotCounted() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        tracker.attach(bob, true, WORLD, 10, 0, 5 * INTERVAL, INTERVAL, WEST);
        assertEquals(1, anchor.players(5 * INTERVAL, 3 * INTERVAL));
    }

    @Test
    void anObserverOfARecentlyMeasuredRegionProbesOnlyItsOwnAnchor() {
        Anchor west = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        west.record(10, null, 1, 0, 60);
        tracker.attach(bob, true, WORLD, 200, 0, 0, INTERVAL, EAST);
        AtomicInteger probes = new AtomicInteger();
        Attachment again = tracker.attach(alice, true, WORLD, 11, 0, INTERVAL / 2, INTERVAL, (chunkX, chunkZ) -> {
            probes.incrementAndGet();
            return chunkX < 100;
        });
        assertSame(west, again.anchor());
        assertFalse(again.measureDue());
        assertEquals(1, probes.get());
        assertEquals(11, west.chunkX());
    }

    @Test
    void aMergeIsFoundAtTheNextMeasureOfTheAnchorKeptMeanwhile() {
        Anchor west = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        west.record(10, null, 1, 0, 60);
        Anchor east = tracker.attach(bob, true, WORLD, 200, 0, 0, INTERVAL, EAST).anchor();
        east.record(10, null, 1, 0, 60);
        Attachment meanwhile = tracker.attach(bob, true, WORLD, 200, 0, INTERVAL / 2, INTERVAL, EVERYTHING);
        assertSame(east, meanwhile.anchor());
        assertFalse(meanwhile.measureDue());
        Attachment measuring = tracker.attach(bob, true, WORLD, 200, 0, INTERVAL, INTERVAL, EVERYTHING);
        assertSame(west, measuring.anchor());
        assertEquals(List.of(east), measuring.merged());
        assertTrue(east.isDead());
    }

    @Test
    void anObserverWhoseAnchorIsNoLongerOwnedLooksForAnother() {
        Anchor west = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        west.record(10, null, 1, 0, 60);
        Anchor moved = tracker.attach(alice, true, WORLD, 200, 0, INTERVAL / 2, INTERVAL, EAST).anchor();
        assertNotEquals(west, moved);
        assertEquals(Optional.of(moved), tracker.anchorOf(alice));
    }

    @Test
    void samplesAreCountedPerRegionTick() {
        Anchor anchor = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        assertEquals(1, anchor.countSampleOnTick(400));
        assertEquals(2, anchor.countSampleOnTick(400));
        assertEquals(1, anchor.countSampleOnTick(401));
        assertEquals(1, anchor.countSampleOnTick(400));
    }

    @Test
    void anAnchorSeenAgainByTheSameObserverKeepsIt() {
        Anchor first = tracker.attach(alice, true, WORLD, 10, 0, 0, INTERVAL, WEST).anchor();
        Anchor again = tracker.attach(alice, true, WORLD, 11, 0, INTERVAL, INTERVAL, WEST).anchor();
        assertSame(first, again);
        assertEquals(Optional.of(first), tracker.anchorOf(alice));
        assertEquals(1, first.players(INTERVAL, INTERVAL));
    }

    @Test
    void detachingAnUnknownObserverIsHarmless() {
        tracker.detach(alice);
        assertEquals(Optional.empty(), tracker.anchorOf(alice));
    }
}
