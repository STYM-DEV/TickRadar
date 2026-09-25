package dev.stym.tickradar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import dev.stym.tickradar.engine.RegionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegionsPageTest {

    @Test
    void theFirstPageStartsWithTheGlobalRegion() {
        RegionsPage page = RegionsPage.of(view(10), 1, 4);
        assertEquals(List.of("G", "R1", "R2", "R3"), ids(page));
        assertEquals(1, page.page());
        assertEquals(3, page.pages());
        assertTrue(page.hasNext());
    }

    @Test
    void theLastPageHoldsTheRemainingRegions() {
        RegionsPage page = RegionsPage.of(view(10), 3, 4);
        assertEquals(List.of("R8", "R9", "R10"), ids(page));
        assertFalse(page.hasNext());
    }

    @Test
    void aPageBeyondTheEndShowsTheLastPage() {
        assertEquals(3, RegionsPage.of(view(10), 99, 4).page());
    }

    @Test
    void anEmptyViewHasOneEmptyPage() {
        RegionsPage page = RegionsPage.of(RegionView.EMPTY, 2, 8);
        assertTrue(page.isEmpty());
        assertEquals(1, page.pages());
        assertFalse(page.hasNext());
    }

    @Test
    void anExactMultipleHasNoExtraPage() {
        assertEquals(2, RegionsPage.of(view(7), 1, 4).pages());
    }

    private static RegionView view(int regions) {
        List<RegionSnapshot> snapshots = new ArrayList<>();
        for (int i = 1; i <= regions; i++) {
            snapshots.add(snapshot("R" + i, 100 - i));
        }
        return RegionView.of(0, snapshot("G", 1), snapshots);
    }

    private static List<String> ids(RegionsPage page) {
        return page.entries().stream().map(RegionSnapshot::id).toList();
    }

    static RegionSnapshot snapshot(String id, double mspt) {
        return new RegionSnapshot(id, "world", Optional.of(new BlockPosition(1204, 64, -3380)), 5, 19.8, 19.9, mspt,
                new RegionStats(mspt, mspt + 1, mspt, 10), 0, false);
    }
}
