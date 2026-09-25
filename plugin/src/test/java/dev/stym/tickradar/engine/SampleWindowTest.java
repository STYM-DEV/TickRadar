package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SampleWindowTest {

    @Test
    void emptyWindowHasNoStats() {
        assertSame(RegionStats.EMPTY, new SampleWindow(10).stats());
    }

    @Test
    void computesAverageMaxAndNearestRankP95() {
        SampleWindow window = new SampleWindow(100);
        for (int i = 1; i <= 100; i++) {
            window.add(i);
        }
        RegionStats stats = window.stats();
        assertEquals(50.5, stats.average(), 1e-9);
        assertEquals(100, stats.max());
        assertEquals(95, stats.p95());
        assertEquals(100, stats.count());
    }

    @Test
    void singleSampleIsItsOwnPercentile() {
        SampleWindow window = new SampleWindow(5);
        window.add(12.5);
        RegionStats stats = window.stats();
        assertEquals(12.5, stats.average());
        assertEquals(12.5, stats.max());
        assertEquals(12.5, stats.p95());
    }

    @Test
    void rotationDropsTheOldestSamples() {
        SampleWindow window = new SampleWindow(3);
        window.add(100);
        window.add(1);
        window.add(2);
        window.add(3);
        assertEquals(3, window.size());
        assertArrayEquals(new double[] {1, 2, 3}, window.ordered());
        assertEquals(3, window.stats().max());
        assertEquals(2, window.stats().average(), 1e-9);
    }

    @Test
    void shrinkingKeepsTheNewestSamplesInOrder() {
        SampleWindow window = new SampleWindow(5);
        for (int i = 1; i <= 7; i++) {
            window.add(i);
        }
        window.resize(2);
        assertArrayEquals(new double[] {6, 7}, window.ordered());
        window.add(8);
        assertArrayEquals(new double[] {7, 8}, window.ordered());
    }

    @Test
    void growingKeepsEverySample() {
        SampleWindow window = new SampleWindow(2);
        window.add(1);
        window.add(2);
        window.add(3);
        window.resize(4);
        window.add(4);
        assertArrayEquals(new double[] {2, 3, 4}, window.ordered());
        window.add(5);
        window.add(6);
        assertArrayEquals(new double[] {3, 4, 5, 6}, window.ordered());
    }

    @Test
    void capacityIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new SampleWindow(0));
        assertThrows(IllegalArgumentException.class, () -> new SampleWindow(SampleWindow.MAX_CAPACITY + 1));
        assertEquals(SampleWindow.MAX_CAPACITY, new SampleWindow(SampleWindow.MAX_CAPACITY).capacity());
    }

    @Test
    void fullWindowOfTheLargestCapacityStaysSmall() {
        SampleWindow window = new SampleWindow(SampleWindow.MAX_CAPACITY);
        for (int i = 0; i < 2 * SampleWindow.MAX_CAPACITY; i++) {
            window.add(i % 97);
        }
        assertEquals(SampleWindow.MAX_CAPACITY, window.stats().count());
        assertEquals(96, window.stats().max());
    }

    @Test
    void statsMatchASortedCopyForEverySizeAndCapacity() {
        Random random = new Random(20260925);
        for (int capacity : new int[] {1, 2, 3, 19, 20, 21, 60, 61, 300, SampleWindow.MAX_CAPACITY}) {
            SampleWindow window = new SampleWindow(capacity);
            for (int added = 0; added < capacity + 50; added++) {
                window.add(random.nextInt(8) == 0 ? 50 : Math.round(random.nextDouble() * 600) / 10.0);
                assertMatchesSortedCopy(window);
            }
        }
    }

    @Test
    void statsFollowAResize() {
        SampleWindow window = new SampleWindow(20);
        for (int i = 1; i <= 20; i++) {
            window.add(i);
        }
        window.resize(200);
        for (int i = 21; i <= 150; i++) {
            window.add(i);
        }
        assertMatchesSortedCopy(window);
        assertEquals(143, window.stats().p95());
    }

    private static void assertMatchesSortedCopy(SampleWindow window) {
        double[] sorted = window.ordered();
        Arrays.sort(sorted);
        double sum = 0;
        for (double value : sorted) {
            sum += value;
        }
        int rank = (int) Math.ceil(0.95 * sorted.length);
        RegionStats stats = window.stats();
        assertEquals(sum / sorted.length, stats.average(), 1e-9);
        assertEquals(sorted[sorted.length - 1], stats.max());
        assertEquals(sorted[rank - 1], stats.p95());
        assertEquals(sorted.length, stats.count());
    }
}
