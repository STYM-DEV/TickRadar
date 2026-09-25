package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TickPeakTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long ORIGIN = 500 * SECOND;

    private final TickPeak peak = new TickPeak(ORIGIN, 30 * SECOND);

    @Test
    void nothingRecordedIsZero() {
        assertEquals(0, peak.recent(ORIGIN));
        assertEquals(0, peak.highest());
    }

    @Test
    void theRecentPeakIsTheLargestCountOfTheLastWindows() {
        peak.record(1, ORIGIN + SECOND);
        peak.record(3, ORIGIN + 2 * SECOND);
        peak.record(2, ORIGIN + 3 * SECOND);
        assertEquals(3, peak.recent(ORIGIN + 4 * SECOND));
        peak.record(1, ORIGIN + 31 * SECOND);
        assertEquals(3, peak.recent(ORIGIN + 40 * SECOND));
        assertEquals(1, peak.recent(ORIGIN + 61 * SECOND));
        assertEquals(0, peak.recent(ORIGIN + 91 * SECOND));
        assertEquals(3, peak.highest());
    }

    @Test
    void aLateRecordFromAnOlderWindowIsIgnoredForTheRecentPeak() {
        peak.record(2, ORIGIN + 65 * SECOND);
        peak.record(9, ORIGIN + 5 * SECOND);
        assertEquals(2, peak.recent(ORIGIN + 66 * SECOND));
        assertEquals(9, peak.highest());
    }

    @Test
    void emptyCountsAndInvalidWindowsAreIgnoredOrRefused() {
        peak.record(0, ORIGIN);
        assertEquals(0, peak.highest());
        assertThrows(IllegalArgumentException.class, () -> new TickPeak(ORIGIN, 0));
    }
}
