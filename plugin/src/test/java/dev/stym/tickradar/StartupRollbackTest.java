package dev.stym.tickradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.sample.ErrorReporter;
import dev.stym.tickradar.sample.RegionTpsPoller;
import dev.stym.tickradar.sample.SamplingCosts;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class StartupRollbackTest {

    @Test
    void aFailureAfterTheTpsThreadStartedStopsTheThreadAndIsRethrown() {
        RegionTpsPoller poller = new RegionTpsPoller(new AnchorTracker(), (world, chunkX, chunkZ) -> null, SamplingCosts.create(),
                new ErrorReporter(Logger.getAnonymousLogger()), () -> Duration.ofMillis(5));
        IllegalStateException failure = new IllegalStateException("listener registration failed");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> StartupRollback.startOrRollBack(() -> {
            poller.start();
            assertEquals(1, pollerThreads().size());
            throw failure;
        }, poller::stop));
        assertSame(failure, thrown);
        assertFalse(poller.isRunning());
        assertTrue(pollerThreads().isEmpty());
    }

    @Test
    void anErrorIsRethrownAsIs() {
        AtomicInteger rollBacks = new AtomicInteger();
        LinkageError failure = new LinkageError("missing class");
        LinkageError thrown = assertThrows(LinkageError.class, () -> StartupRollback.startOrRollBack(() -> {
            throw failure;
        }, rollBacks::incrementAndGet));
        assertSame(failure, thrown);
        assertEquals(1, rollBacks.get());
    }

    @Test
    void aFailingRollBackIsKeptAsSuppressed() {
        IllegalStateException failure = new IllegalStateException("start failed");
        IllegalArgumentException rollBackFailure = new IllegalArgumentException("stop failed");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> StartupRollback.startOrRollBack(() -> {
            throw failure;
        }, () -> {
            throw rollBackFailure;
        }));
        assertSame(failure, thrown);
        assertEquals(List.of(rollBackFailure), List.of(thrown.getSuppressed()));
    }

    @Test
    void aSuccessfulStartIsNotRolledBack() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger rollBacks = new AtomicInteger();
        StartupRollback.startOrRollBack(starts::incrementAndGet, rollBacks::incrementAndGet);
        assertEquals(1, starts.get());
        assertEquals(0, rollBacks.get());
    }

    private static List<Thread> pollerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(RegionTpsPoller.THREAD_NAME) && thread.isAlive())
                .toList();
    }
}
