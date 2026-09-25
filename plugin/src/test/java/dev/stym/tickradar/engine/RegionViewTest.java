package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegionViewTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void regionsAreSortedBySlowestMsptFirst() {
        RegionView view = RegionView.of(0, snapshot("G", 3), List.of(snapshot("R1", 12), snapshot("R2", 48), snapshot("R3", 30)));
        assertEquals(List.of("R2", "R3", "R1"), ids(view.regions()));
    }

    @Test
    void theGlobalRegionComesFirst() {
        RegionView view = RegionView.of(0, snapshot("G", 3), List.of(snapshot("R1", 12), snapshot("R2", 48)));
        assertEquals(List.of("G", "R2", "R1"), ids(view.globalFirst()));
    }

    @Test
    void unmeasuredRegionsComeLast() {
        RegionView view = RegionView.of(0, null, List.of(unavailable("R1"), snapshot("R2", 5), snapshot("R3", Double.NaN)));
        assertEquals(List.of("R2", "R1", "R3"), ids(view.regions()));
    }

    @Test
    void equalMsptKeepsTheOldestRegionFirst() {
        RegionView view = RegionView.of(0, null, List.of(snapshot("R10", 20), snapshot("R9", 20), snapshot("R2", 20)));
        assertEquals(List.of("R2", "R9", "R10"), ids(view.regions()));
    }

    @Test
    void withoutGlobalOnlyTheRegionsAreListed() {
        RegionView view = RegionView.of(0, null, List.of(snapshot("R1", 12)));
        assertEquals(List.of("R1"), ids(view.globalFirst()));
        assertEquals(1, view.count());
    }

    @Test
    void theWorstRegionIsTheSlowestFreshOne() {
        RegionSnapshot stale = new RegionSnapshot("R1", "world", Optional.empty(), 1, 20, 20, 60, RegionStats.EMPTY, 0, false);
        RegionSnapshot fresh = new RegionSnapshot("R2", "world", Optional.empty(), 1, 20, 20, 30, RegionStats.EMPTY, 10 * SECOND, false);
        RegionView view = RegionView.of(0, null, List.of(stale, fresh));
        assertEquals("R2", view.worst(11 * SECOND, 3 * SECOND).orElseThrow().id());
        assertTrue(view.worst(20 * SECOND, 3 * SECOND).isEmpty());
    }

    private static List<String> ids(List<RegionSnapshot> snapshots) {
        return snapshots.stream().map(RegionSnapshot::id).toList();
    }

    static RegionSnapshot snapshot(String id, double mspt) {
        return new RegionSnapshot(id, "world", Optional.of(new BlockPosition(100, 64, -200)), 2, 20, 20, mspt,
                new RegionStats(mspt, mspt, mspt, 10), 0, false);
    }

    private static RegionSnapshot unavailable(String id) {
        return new RegionSnapshot(id, "world", Optional.empty(), 1, Double.NaN, Double.NaN, Double.NaN, RegionStats.EMPTY, 0, true);
    }
}
