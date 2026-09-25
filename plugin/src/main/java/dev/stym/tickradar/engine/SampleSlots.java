package dev.stym.tickradar.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

public final class SampleSlots {

    private final ConcurrentHashMap<Integer, AtomicIntegerArray> loads = new ConcurrentHashMap<>();
    private final AtomicInteger cursor = new AtomicInteger();

    public final class Lease {

        private final int interval;
        private final int slot;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(int interval, int slot) {
            this.interval = interval;
            this.slot = slot;
        }

        public int interval() {
            return interval;
        }

        public int slot() {
            return slot;
        }

        public long firstDelay(long currentTick) {
            return SampleSlots.firstDelay(slot, currentTick, interval);
        }

        public long realignDelay(long currentTick) {
            return SampleSlots.realignDelay(slot, currentTick, interval);
        }

        public void release() {
            if (released.compareAndSet(false, true)) {
                loadOf(interval).decrementAndGet(slot);
            }
        }
    }

    public Lease acquire(int interval) {
        if (interval < 1) {
            throw new IllegalArgumentException("interval must be at least 1: " + interval);
        }
        AtomicIntegerArray load = loadOf(interval);
        int slot = leastLoaded(load, Math.floorMod(cursor.getAndIncrement(), interval), interval);
        load.incrementAndGet(slot);
        return new Lease(interval, slot);
    }

    public int load(int interval, int slot) {
        AtomicIntegerArray load = loads.get(interval);
        return load == null ? 0 : load.get(slot);
    }

    static long firstDelay(int slot, long currentTick, int interval) {
        long delay = Math.floorMod(slot - currentTick, (long) interval);
        return delay == 0 ? interval : delay;
    }

    static long realignDelay(int slot, long currentTick, int interval) {
        long offset = Math.floorMod(currentTick - slot, (long) interval);
        if (offset == 0) {
            return 0;
        }
        long delay = interval - offset;
        return delay * 2 < interval ? delay + interval : delay;
    }

    private AtomicIntegerArray loadOf(int interval) {
        return loads.computeIfAbsent(interval, AtomicIntegerArray::new);
    }

    private static int leastLoaded(AtomicIntegerArray load, int start, int interval) {
        int best = start;
        int bestLoad = load.get(start);
        for (int step = 1; step < interval && bestLoad > 0; step++) {
            int slot = (start + step) % interval;
            int candidate = load.get(slot);
            if (candidate < bestLoad) {
                best = slot;
                bestLoad = candidate;
            }
        }
        return best;
    }
}
