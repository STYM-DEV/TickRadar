package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AnchorTrackerConcurrencyTest {

    private static final int REGIONS = 8;
    private static final int ATTACHMENTS_PER_REGION = 10_000;
    private static final int OBSERVERS_PER_REGION = 16;
    private static final int CHUNKS_PER_REGION = 1_000;
    private static final long INTERVAL = 1_000_000_000L;
    private static final String WORLD = "world";

    private record RegionRun(int region, List<UUID> observers, Set<String> anchorIds) {
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void eightRegionsAttachingInParallelLoseNoAnchorAndDuplicateNoIdentifier() throws Exception {
        AnchorTracker tracker = new AnchorTracker();
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        List<RegionRun> runs;
        try (ExecutorService pool = Executors.newFixedThreadPool(REGIONS + 1)) {
            Future<Integer> sweeper = pool.submit(sweeper(tracker, start, running));
            List<Future<RegionRun>> futures = new ArrayList<>();
            for (int region = 0; region < REGIONS; region++) {
                futures.add(pool.submit(region(tracker, region, start)));
            }
            start.countDown();
            runs = new ArrayList<>();
            for (Future<RegionRun> future : futures) {
                runs.add(future.get());
            }
            running.set(false);
            assertTrue(sweeper.get() > 0);
        }

        Set<String> allIds = new HashSet<>();
        for (RegionRun run : runs) {
            assertEquals(1, run.anchorIds().size(), "region " + run.region() + " saw " + run.anchorIds());
            String id = run.anchorIds().iterator().next();
            assertTrue(allIds.add(id), "identifier " + id + " used by two regions");
            Anchor anchor = tracker.find(id).orElseThrow();
            assertFalse(anchor.isDead());
            assertEquals(OBSERVERS_PER_REGION, anchor.players(ATTACHMENTS_PER_REGION, Long.MAX_VALUE / 2));
            for (UUID observer : run.observers()) {
                assertEquals(id, tracker.anchorOf(observer).orElseThrow().id());
            }
        }
        assertEquals(REGIONS, tracker.anchors().size());
        assertEquals(REGIONS, tracker.size());
        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= REGIONS; i++) {
            expected.add("R" + i);
        }
        assertEquals(expected, allIds);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void observersMovingBetweenRegionsNeverShareAnIdentifier() throws Exception {
        AnchorTracker tracker = new AnchorTracker();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Set<String>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(REGIONS)) {
            for (int region = 0; region < REGIONS; region++) {
                int owned = region;
                futures.add(pool.submit(() -> {
                    start.await();
                    UUID observer = UUID.randomUUID();
                    Set<String> seen = new HashSet<>();
                    for (int i = 0; i < ATTACHMENTS_PER_REGION; i++) {
                        int band = i % 2 == 0 ? owned : owned + REGIONS;
                        OwnershipProbe probe = (chunkX, chunkZ) -> chunkX / CHUNKS_PER_REGION == band;
                        int chunkX = band * CHUNKS_PER_REGION + ThreadLocalRandom.current().nextInt(CHUNKS_PER_REGION);
                        seen.add(tracker.attach(observer, true, WORLD, chunkX, 0, i, INTERVAL, probe).anchor().id());
                    }
                    return seen;
                }));
            }
            start.countDown();
            Set<String> seenByAll = new HashSet<>();
            for (Future<Set<String>> future : futures) {
                Set<String> seen = future.get();
                assertEquals(2, seen.size());
                seen.forEach(id -> assertTrue(seenByAll.add(id), "identifier " + id + " seen by two threads"));
            }
        }
        List<Anchor> live = tracker.anchors();
        Set<String> ids = new HashSet<>();
        for (Anchor anchor : live) {
            assertTrue(ids.add(anchor.id()));
            assertFalse(anchor.isDead());
        }
        assertEquals(2 * REGIONS, live.size());
        assertEquals(live.size(), tracker.size());
    }

    private static Callable<RegionRun> region(AnchorTracker tracker, int region, CountDownLatch start) {
        return () -> {
            List<UUID> observers = new ArrayList<>();
            for (int i = 0; i < OBSERVERS_PER_REGION; i++) {
                observers.add(UUID.randomUUID());
            }
            OwnershipProbe probe = (chunkX, chunkZ) -> Math.floorDiv(chunkX, CHUNKS_PER_REGION) == region;
            Set<String> anchorIds = new HashSet<>();
            start.await();
            for (int i = 0; i < ATTACHMENTS_PER_REGION; i++) {
                UUID observer = observers.get(i % OBSERVERS_PER_REGION);
                int chunkX = region * CHUNKS_PER_REGION + ThreadLocalRandom.current().nextInt(CHUNKS_PER_REGION);
                int chunkZ = ThreadLocalRandom.current().nextInt(-500, 500);
                Attachment attachment = tracker.attach(observer, true, WORLD, chunkX, chunkZ, i, INTERVAL, probe);
                anchorIds.add(attachment.anchor().id());
                if (attachment.measureDue()) {
                    attachment.anchor().record(10, new BlockPosition(chunkX << 4, 64, chunkZ << 4),
                            OBSERVERS_PER_REGION, i, 60);
                }
            }
            return new RegionRun(region, observers, anchorIds);
        };
    }

    private static Callable<Integer> sweeper(AnchorTracker tracker, CountDownLatch start, AtomicBoolean running) {
        return () -> {
            start.await();
            int sweeps = 0;
            while (running.get() || sweeps == 0) {
                assertTrue(tracker.sweep(ATTACHMENTS_PER_REGION, 3 * INTERVAL, 60 * INTERVAL).isEmpty());
                sweeps++;
            }
            return sweeps;
        };
    }
}
