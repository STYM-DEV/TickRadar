package dev.stym.tickradar.engine;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class TickPeak {

    private static final int COUNT_BITS = 32;
    private static final long COUNT_MASK = 0xFFFFFFFFL;

    private final long originNanos;
    private final long windowNanos;
    private final AtomicLong current = new AtomicLong();
    private final AtomicLong previous = new AtomicLong();
    private final AtomicInteger highest = new AtomicInteger();

    public TickPeak(long originNanos, long windowNanos) {
        if (windowNanos <= 0) {
            throw new IllegalArgumentException("window must be positive: " + windowNanos);
        }
        this.originNanos = originNanos;
        this.windowNanos = windowNanos;
    }

    public void record(int samplesOnTick, long nowNanos) {
        if (samplesOnTick <= 0) {
            return;
        }
        if (samplesOnTick > highest.get()) {
            highest.accumulateAndGet(samplesOnTick, Math::max);
        }
        long window = windowOf(nowNanos);
        while (true) {
            long packed = current.get();
            long packedWindow = packed >>> COUNT_BITS;
            if (packedWindow == window) {
                if (samplesOnTick <= countOf(packed) || current.compareAndSet(packed, pack(window, samplesOnTick))) {
                    return;
                }
            } else if (packedWindow > window) {
                return;
            } else if (current.compareAndSet(packed, pack(window, samplesOnTick))) {
                previous.set(packed);
                return;
            }
        }
    }

    public int recent(long nowNanos) {
        long window = windowOf(nowNanos);
        return Math.max(countIn(current.get(), window), countIn(previous.get(), window));
    }

    public int highest() {
        return highest.get();
    }

    private long windowOf(long nowNanos) {
        return Math.max(0, nowNanos - originNanos) / windowNanos + 1;
    }

    private static int countIn(long packed, long window) {
        long packedWindow = packed >>> COUNT_BITS;
        return packedWindow == window || packedWindow + 1 == window ? countOf(packed) : 0;
    }

    private static int countOf(long packed) {
        return (int) (packed & COUNT_MASK);
    }

    private static long pack(long window, int count) {
        return window << COUNT_BITS | (count & COUNT_MASK);
    }
}
