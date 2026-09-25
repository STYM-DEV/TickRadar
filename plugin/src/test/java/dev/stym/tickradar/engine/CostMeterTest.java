package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class CostMeterTest {

    private static final double MAX_RELATIVE_ERROR = 0.05;
    private static final long MICRO = 1_000L;
    private static final long MILLI = 1_000_000L;
    private static final long SECOND = 1_000_000_000L;
    private static final int SAMPLES = 20_000;

    @Test
    void emptyMeterReportsZero() {
        assertEquals(new CostMeter.Summary(0, 0, 0, 0), new CostMeter().summary());
    }

    @Test
    void averageP99AndMaxAreInMicroseconds() {
        CostMeter meter = new CostMeter();
        for (int i = 0; i < 99; i++) {
            meter.record(10 * MICRO);
        }
        meter.record(2 * MILLI);
        CostMeter.Summary summary = meter.summary();
        assertEquals(100, summary.count());
        assertEquals((99 * 10.0 + 2_000) / 100, summary.averageMicros(), 1e-9);
        assertWithinRelativeError(10, summary.p99Micros());
        assertEquals(2_000, summary.maxMicros());
    }

    @Test
    void p99OfUniformDurationsFrom5To50MillisIsAccurate() {
        Random random = new Random(42);
        assertAccurateP99(() -> 5 * MILLI + (long) (random.nextDouble() * 45 * MILLI));
    }

    @Test
    void p99OfABimodalDistributionFallsInTheSlowMode() {
        Random random = new Random(7);
        long[] values = assertAccurateP99(() -> random.nextInt(100) < 95
                ? 15 * MICRO + random.nextInt(10_000)
                : 30 * MILLI + (long) (random.nextDouble() * 20 * MILLI));
        assertTrue(exactP99(values) >= 30 * MILLI);
    }

    @Test
    void p99OfSlowPassesAroundTenSecondsIsAccurate() {
        Random random = new Random(3);
        assertAccurateP99(() -> 2 * SECOND + (long) (random.nextDouble() * 10 * SECOND));
    }

    @Test
    void p99OfFastSamplesIsAccurate() {
        Random random = new Random(11);
        assertAccurateP99(() -> 5 * MICRO + (long) (random.nextDouble() * 200 * MICRO));
    }

    @Test
    void p99NeverExceedsTheMaximum() {
        CostMeter meter = new CostMeter();
        meter.record(50 * MILLI);
        assertEquals(50_000, meter.summary().p99Micros());
    }

    @Test
    void durationsBeyondTheHistogramAreBoundedByTheMaximum() {
        CostMeter meter = new CostMeter();
        for (int i = 0; i < 50; i++) {
            meter.record(10 * MICRO);
        }
        for (int i = 0; i < 50; i++) {
            meter.record(100 * SECOND + i);
        }
        CostMeter.Summary summary = meter.summary();
        assertEquals(summary.maxMicros(), summary.p99Micros());
        assertEquals((100 * SECOND + 49) / 1_000.0, summary.maxMicros());
    }

    @Test
    void everyBucketIsNarrowerThanTheRelativePrecision() {
        for (long nanos = 1; nanos <= CostMeter.LARGEST_TRACKED_NANOS; nanos = nanos * 3 / 2 + 1) {
            long upper = CostMeter.upperBoundNanos(CostMeter.bucketOf(nanos));
            assertTrue(upper > nanos, "upper bound above " + nanos);
            if (nanos >= CostMeter.SUB_BUCKETS) {
                assertTrue((double) upper / nanos - 1 <= 1.0 / CostMeter.SUB_BUCKETS, "precision at " + nanos);
            }
        }
    }

    @Test
    void bucketsFollowTheDurations() {
        int previous = -1;
        for (long nanos = 0; nanos < 100_000; nanos++) {
            int bucket = CostMeter.bucketOf(nanos);
            assertTrue(bucket == previous || bucket == previous + 1, "contiguous buckets at " + nanos);
            previous = bucket;
        }
        assertEquals(CostMeter.BUCKETS - 1, CostMeter.bucketOf(CostMeter.LARGEST_TRACKED_NANOS));
        assertEquals(CostMeter.BUCKETS, CostMeter.bucketOf(CostMeter.LARGEST_TRACKED_NANOS + 1));
        assertTrue(CostMeter.LARGEST_TRACKED_NANOS >= 10 * SECOND);
    }

    @Test
    void negativeDurationsCountAsZero() {
        CostMeter meter = new CostMeter();
        meter.record(-5);
        assertEquals(0, meter.summary().maxMicros());
    }

    @Test
    void aRecordedZeroDurationIsCountedExactly() {
        CostMeter meter = new CostMeter();
        meter.record(0);
        meter.record(0);
        CostMeter.Summary summary = meter.summary();
        assertEquals(2, summary.count());
        assertEquals(0, summary.averageMicros());
        assertEquals(0, summary.p99Micros());
        assertEquals(0, summary.maxMicros());
    }

    @Test
    void aSingleNanosecondIsBelowOneMicrosecond() {
        CostMeter meter = new CostMeter();
        meter.record(1);
        CostMeter.Summary summary = meter.summary();
        assertEquals(1, summary.count());
        assertEquals(0.001, summary.averageMicros(), 1e-9);
        assertEquals(0.001, summary.p99Micros(), 1e-9);
        assertEquals(0.001, summary.maxMicros(), 1e-9);
    }

    @Test
    void powerOfTwoBoundariesStayInAccurateBuckets() {
        for (int exponent = CostMeter.SUB_BUCKET_BITS; exponent < CostMeter.HIGHEST_EXPONENT; exponent++) {
            long boundary = 1L << exponent;
            CostMeter meter = new CostMeter();
            for (int i = 0; i < 99; i++) {
                meter.record(boundary - 1);
            }
            meter.record(boundary);
            CostMeter.Summary summary = meter.summary();
            assertEquals((double) boundary / MICRO, summary.maxMicros(), (double) boundary / MICRO * 1e-9,
                    "max at boundary " + boundary);
            assertWithinRelativeError((double) (boundary - 1) / MICRO, summary.p99Micros());
        }
    }

    @Test
    void concurrentRecordsAreAllCounted() throws Exception {
        CostMeter meter = new CostMeter();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int thread = 0; thread < 8; thread++) {
                pool.submit(() -> {
                    for (int i = 0; i < 10_000; i++) {
                        meter.record(1_000);
                    }
                });
            }
        }
        assertEquals(80_000, meter.summary().count());
        assertEquals(1.0, meter.summary().averageMicros(), 1e-9);
    }

    @Test
    void concurrentRecordsProduceAnAccurateP99() throws Exception {
        CostMeter meter = new CostMeter();
        int threads = 8;
        int perThread = SAMPLES / threads;
        long[][] byThread = new long[threads][perThread];
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int thread = 0; thread < threads; thread++) {
                int threadIndex = thread;
                pool.submit(() -> {
                    Random random = new Random(1_000 + threadIndex);
                    for (int i = 0; i < perThread; i++) {
                        long duration = 5 * MILLI + (long) (random.nextDouble() * 45 * MILLI);
                        byThread[threadIndex][i] = duration;
                        meter.record(duration);
                    }
                });
            }
        }
        long[] all = new long[threads * perThread];
        int index = 0;
        for (long[] values : byThread) {
            for (long value : values) {
                all[index++] = value;
            }
        }
        assertEquals(all.length, meter.summary().count());
        assertWithinRelativeError(exactP99(all) / 1_000.0, meter.summary().p99Micros());
    }

    private static long[] assertAccurateP99(LongSupplier durations) {
        CostMeter meter = new CostMeter();
        long[] values = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            values[i] = durations.getAsLong();
            meter.record(values[i]);
        }
        assertWithinRelativeError(exactP99(values) / 1_000.0, meter.summary().p99Micros());
        return values;
    }

    private static long exactP99(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(0.99 * sorted.length) - 1];
    }

    private static void assertWithinRelativeError(double expectedMicros, double actualMicros) {
        double error = Math.abs(actualMicros - expectedMicros) / expectedMicros;
        assertTrue(error <= MAX_RELATIVE_ERROR, "p99 " + actualMicros + " µs vs exact " + expectedMicros + " µs: " + error);
    }
}
