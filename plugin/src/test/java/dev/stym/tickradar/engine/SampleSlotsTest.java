package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SampleSlotsTest {

    private static final int INTERVAL = 20;

    private final SampleSlots slots = new SampleSlots();

    @Test
    void acquisitionsFillEverySlotBeforeDoublingOne() {
        for (int players = 1; players <= 3 * INTERVAL + 7; players++) {
            slots.acquire(INTERVAL);
            int ceiling = (players + INTERVAL - 1) / INTERVAL;
            for (int slot = 0; slot < INTERVAL; slot++) {
                assertTrue(slots.load(INTERVAL, slot) <= ceiling, "slot " + slot + " after " + players + " players");
            }
        }
    }

    @Test
    void aReleasedSlotIsTheNextOneGiven() {
        List<SampleSlots.Lease> leases = new ArrayList<>();
        for (int i = 0; i < INTERVAL; i++) {
            leases.add(slots.acquire(INTERVAL));
        }
        SampleSlots.Lease leaving = leases.get(7);
        leaving.release();
        leaving.release();
        assertEquals(0, slots.load(INTERVAL, leaving.slot()));
        assertEquals(leaving.slot(), slots.acquire(INTERVAL).slot());
    }

    @Test
    void departuresAreNotRebalanced() {
        List<SampleSlots.Lease> leases = new ArrayList<>();
        for (int i = 0; i < 2 * INTERVAL; i++) {
            leases.add(slots.acquire(INTERVAL));
        }
        int crowded = leases.getFirst().slot();
        List<SampleSlots.Lease> staying = leases.stream().filter(lease -> lease.slot() == crowded).toList();
        leases.stream().filter(lease -> lease.slot() != crowded).forEach(SampleSlots.Lease::release);
        assertEquals(2, staying.size());
        assertEquals(2, slots.load(INTERVAL, crowded));
    }

    @Test
    void eachIntervalHasItsOwnLoad() {
        SampleSlots.Lease twenty = slots.acquire(INTERVAL);
        SampleSlots.Lease forty = slots.acquire(40);
        assertEquals(1, slots.load(INTERVAL, twenty.slot()));
        assertEquals(1, slots.load(40, forty.slot()));
        twenty.release();
        assertEquals(0, slots.load(INTERVAL, twenty.slot()));
        assertEquals(1, slots.load(40, forty.slot()));
    }

    @Test
    void anIntervalBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> slots.acquire(0));
    }

    @Test
    void theFirstDelayLandsOnTheSlotWithinOneInterval() {
        for (long tick = -45; tick < 45; tick++) {
            for (int slot = 0; slot < INTERVAL; slot++) {
                long delay = SampleSlots.firstDelay(slot, tick, INTERVAL);
                assertTrue(delay >= 1 && delay <= INTERVAL, "delay " + delay);
                assertEquals(slot, Math.floorMod(tick + delay, INTERVAL));
            }
        }
    }

    @Test
    void anAlignedTaskIsNotMoved() {
        assertEquals(0, SampleSlots.realignDelay(3, 43, INTERVAL));
        assertEquals(0, SampleSlots.realignDelay(3, -17, INTERVAL));
    }

    @Test
    void aDriftedTaskMovesBackToItsSlotWithoutSamplingTooSoonOrTooLate() {
        for (long tick = 0; tick < 3 * INTERVAL; tick++) {
            for (int slot = 0; slot < INTERVAL; slot++) {
                long delay = SampleSlots.realignDelay(slot, tick, INTERVAL);
                if (Math.floorMod(tick, INTERVAL) == slot) {
                    continue;
                }
                assertTrue(delay >= INTERVAL / 2 && delay < INTERVAL + INTERVAL / 2, "delay " + delay);
                assertEquals(slot, Math.floorMod(tick + delay, INTERVAL));
            }
        }
    }

    @Test
    void playersAlreadyOnlineAtStartupAreSpreadFromTheirFirstSample() {
        Region region = new Region(slots, INTERVAL);
        for (int i = 0; i < 45; i++) {
            region.join(1_000);
        }
        region.runUntil(1_000 + 5 * INTERVAL);
        assertEquals(3, region.peakSince(1_000));
        assertEquals(0, region.realignments());
    }

    @Test
    void aWaveOfConnectionsOnRandomTicksIsSpreadEvenly() {
        Region region = new Region(slots, INTERVAL);
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            region.join(random.nextInt(200));
        }
        region.runUntil(400);
        assertEquals(25, region.peakSince(250));
    }

    @Test
    void teleportedPlayersComeBackToTheirSlot() {
        Region region = new Region(slots, INTERVAL);
        for (int i = 0; i < 40; i++) {
            region.join(i % 3);
        }
        region.runUntil(100);
        Random random = new Random(7);
        for (int i = 0; i < 40; i += 2) {
            region.delay(i, 1 + random.nextInt(INTERVAL - 1));
        }
        region.runUntil(200);
        assertTrue(region.realignments() > 0);
        assertEquals(2, region.peakSince(160));
    }

    @Test
    void aNewIntervalAfterAReloadIsSpreadAgain() {
        Region region = new Region(slots, INTERVAL);
        for (int i = 0; i < 40; i++) {
            region.join(0);
        }
        region.runUntil(100);
        region.changeInterval(30);
        region.runUntil(300);
        assertEquals(2, region.peakSince(200));
        for (int slot = 0; slot < INTERVAL; slot++) {
            assertEquals(0, slots.load(INTERVAL, slot));
        }
    }

    @Test
    void aReloadWithTheSameIntervalKeepsTheSpread() {
        Region region = new Region(slots, INTERVAL);
        for (int i = 0; i < 40; i++) {
            region.join(5);
        }
        region.runUntil(100);
        region.changeInterval(INTERVAL);
        region.runUntil(200);
        assertEquals(2, region.peakSince(100));
        assertEquals(0, region.realignments());
    }

    private static final class Region {

        private final SampleSlots slots;
        private final List<SampleSlots.Lease> leases = new ArrayList<>();
        private final List<Long> nextRuns = new ArrayList<>();
        private final Map<Long, Integer> samplesPerTick = new HashMap<>();
        private int interval;
        private long tick;
        private int realignments;

        Region(SampleSlots slots, int interval) {
            this.slots = slots;
            this.interval = interval;
        }

        void join(long atTick) {
            SampleSlots.Lease lease = slots.acquire(interval);
            leases.add(lease);
            nextRuns.add(atTick + lease.firstDelay(atTick));
        }

        void delay(int player, long ticks) {
            nextRuns.set(player, nextRuns.get(player) + ticks);
        }

        void changeInterval(int configured) {
            interval = configured;
        }

        void runUntil(long lastTick) {
            for (; tick <= lastTick; tick++) {
                for (int player = 0; player < leases.size(); player++) {
                    if (nextRuns.get(player) == tick) {
                        run(player);
                    }
                }
            }
        }

        int peakSince(long fromTick) {
            return samplesPerTick.entrySet().stream().filter(entry -> entry.getKey() >= fromTick)
                    .mapToInt(Map.Entry::getValue).max().orElse(0);
        }

        int realignments() {
            return realignments;
        }

        private void run(int player) {
            SampleSlots.Lease lease = leases.get(player);
            if (lease.interval() != interval) {
                lease.release();
                SampleSlots.Lease renewed = slots.acquire(interval);
                leases.set(player, renewed);
                nextRuns.set(player, tick + renewed.firstDelay(tick));
                return;
            }
            samplesPerTick.merge(tick, 1, Integer::sum);
            long delay = lease.realignDelay(tick);
            if (delay != 0) {
                realignments++;
            }
            nextRuns.set(player, tick + (delay == 0 ? lease.interval() : delay));
        }
    }
}
