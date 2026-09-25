package dev.stym.tickradar.engine;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

public final class CostMeter {

    static final int SUB_BUCKET_BITS = 5;
    static final int SUB_BUCKETS = 1 << SUB_BUCKET_BITS;
    static final int HIGHEST_EXPONENT = 36;
    static final long LARGEST_TRACKED_NANOS = (1L << HIGHEST_EXPONENT) - 1;
    static final int BUCKETS = SUB_BUCKETS + (HIGHEST_EXPONENT - SUB_BUCKET_BITS) * SUB_BUCKETS;
    private static final double PERCENTILE = 0.99;
    private static final double NANOS_PER_MICRO = 1_000.0;

    private final AtomicLongArray histogram = new AtomicLongArray(BUCKETS + 1);
    private final LongAdder count = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();

    public record Summary(long count, double averageMicros, double p99Micros, double maxMicros) {
    }

    public void record(long nanos) {
        long duration = Math.max(0, nanos);
        histogram.incrementAndGet(bucketOf(duration));
        count.increment();
        totalNanos.add(duration);
        if (duration > maxNanos.get()) {
            maxNanos.accumulateAndGet(duration, Math::max);
        }
    }

    public Summary summary() {
        long samples = count.sum();
        if (samples == 0) {
            return new Summary(0, 0, 0, 0);
        }
        double max = maxNanos.get() / NANOS_PER_MICRO;
        return new Summary(samples, totalNanos.sum() / NANOS_PER_MICRO / samples, p99Micros(max), max);
    }

    private double p99Micros(double maxMicros) {
        long total = 0;
        for (int i = 0; i <= BUCKETS; i++) {
            total += histogram.get(i);
        }
        long rank = (long) Math.ceil(PERCENTILE * total);
        long seen = 0;
        for (int i = 0; i < BUCKETS; i++) {
            seen += histogram.get(i);
            if (seen >= rank) {
                return Math.min(upperBoundNanos(i) / NANOS_PER_MICRO, maxMicros);
            }
        }
        return maxMicros;
    }

    static int bucketOf(long nanos) {
        if (nanos < SUB_BUCKETS) {
            return (int) nanos;
        }
        if (nanos > LARGEST_TRACKED_NANOS) {
            return BUCKETS;
        }
        int exponent = 63 - Long.numberOfLeadingZeros(nanos);
        int shift = exponent - SUB_BUCKET_BITS;
        int subBucket = (int) (nanos >>> shift) - SUB_BUCKETS;
        return SUB_BUCKETS + shift * SUB_BUCKETS + subBucket;
    }

    static long upperBoundNanos(int bucket) {
        if (bucket < SUB_BUCKETS) {
            return bucket + 1L;
        }
        int shift = (bucket - SUB_BUCKETS) / SUB_BUCKETS;
        int subBucket = (bucket - SUB_BUCKETS) % SUB_BUCKETS;
        return (long) (SUB_BUCKETS + subBucket + 1) << shift;
    }
}
