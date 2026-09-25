package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FirstSampleWithoutWarmUpColdStartTest {

    private static final double MIN_COLD_RATIO = 20;

    @Test
    void theFirstSampleOfAFreshJvmIsFarSlowerThanTheFollowingOnes() {
        FirstSampleBench bench = new FirstSampleBench();
        FirstSampleBench.Timings timings = bench.sampleLikeThePlayerTask("en");
        System.out.println(timings.describe("without warm-up"));
        assertEquals(3, timings.firstSampleSends());
        assertTrue(timings.ratio() >= MIN_COLD_RATIO, timings.describe("this check has lost its sensitivity"));
    }
}
