package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WarnOnceTest {

    private final WarnOnce once = new WarnOnce();

    @Test
    void theFirstOccurrenceOfAKeyIsReported() {
        assertTrue(once.firstTime("a"));
    }

    @Test
    void aRepeatedKeyIsNeverReportedAgain() {
        assertTrue(once.firstTime("a"));
        assertFalse(once.firstTime("a"));
        assertFalse(once.firstTime("a"));
    }

    @Test
    void differentKeysAreEachReportedOnce() {
        assertTrue(once.firstTime("a"));
        assertTrue(once.firstTime("b"));
        assertFalse(once.firstTime("a"));
        assertFalse(once.firstTime("b"));
    }

    @Test
    void concurrentFirstOccurrencesOfTheSameKeyReportExactlyOnce() throws InterruptedException {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger reported = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    if (once.firstTime("shared")) {
                        reported.incrementAndGet();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, reported.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
