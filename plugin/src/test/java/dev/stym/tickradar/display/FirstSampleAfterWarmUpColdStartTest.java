package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FirstSampleAfterWarmUpColdStartTest {

    private static final long FLOOR_NANOS = 5_000_000L;
    private static final long MAX_RATIO = 20;
    private static final long MAX_WARM_UP_NANOS = 5_000_000_000L;

    @Test
    void theFirstSampleAfterTheWarmUpCostsAboutAsMuchAsTheFollowingOnes() throws InterruptedException {
        FirstSampleBench bench = new FirstSampleBench();
        long warmUp = bench.warmUpOnAnotherThread();
        FirstSampleBench.Timings timings = bench.sampleLikeThePlayerTask("en");
        String report = timings.describe("after warm-up") + String.format(", warm-up %.1f ms", warmUp / 1_000_000.0);
        System.out.println(report);
        assertEquals(3, timings.firstSampleSends());
        long bound = Math.max(FLOOR_NANOS, MAX_RATIO * timings.followingMedianNanos());
        assertTrue(timings.firstNanos() <= bound, report);
        assertTrue(warmUp <= MAX_WARM_UP_NANOS, report);
    }
}
