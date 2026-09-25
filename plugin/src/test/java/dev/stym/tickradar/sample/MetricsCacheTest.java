package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.engine.AlertRules;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionView;
import dev.stym.tickradar.engine.Thresholds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetricsCacheTest {

    private static final long INTERVAL = 1_000_000_000L;

    private final AnchorTracker anchors = new AnchorTracker();
    private final Anchor global = Anchor.global(0);
    private final MetricsCache cache = new MetricsCache(anchors, global);

    @Test
    void theViewIsMemoizedForHalfASecond() {
        measure(0, 20);
        RegionView first = cache.view(0);
        measure(100_000, 30);
        assertSame(first, cache.view(MetricsCache.VIEW_MEMO_NANOS - 1));
        assertEquals(2, cache.view(MetricsCache.VIEW_MEMO_NANOS).count());
    }

    @Test
    void theViewListsGlobalThenTheSlowestRegion() {
        global.record(4, null, 2, 0, 60);
        measure(0, 20);
        measure(100_000, 45);
        List<String> ids = cache.view(0).globalFirst().stream().map(RegionSnapshot::id).toList();
        assertEquals(List.of("G", "R2", "R1"), ids);
    }

    @Test
    void activeAlertsCountsPendingOnesTooAndIgnoresOkRegions() {
        AlertRules rules = AlertRules.of(new Thresholds(40, 50), 1, 1, 300);
        long now = 1_000_000_000L;
        Anchor okAnchor = anchors.attach(UUID.randomUUID(), true, "world", 0, 0, 0, INTERVAL, (x, z) -> x == 0).anchor();
        okAnchor.record(10, new BlockPosition(0, 64, 0), 1, now, 60);
        okAnchor.advanceAlert(10, rules, now);
        Anchor slowAnchor = anchors.attach(UUID.randomUUID(), true, "world", 100, 0, 0, INTERVAL, (x, z) -> x == 100).anchor();
        slowAnchor.record(45, new BlockPosition(1600, 64, 0), 1, now, 60);
        slowAnchor.advanceAlert(45, rules, now);
        slowAnchor.record(10, new BlockPosition(1600, 64, 0), 1, now + 1, 60);
        slowAnchor.advanceAlert(10, rules, now + 1);
        slowAnchor.record(45, new BlockPosition(1600, 64, 0), 1, now + 2, 60);
        slowAnchor.advanceAlert(45, rules, now + 2);
        assertTrue(slowAnchor.alertState().pending());
        assertTrue(slowAnchor.alertState().isActive());
        global.record(60, null, 2, now, 60);
        global.advanceAlert(60, rules, now);
        assertEquals(2, cache.activeAlerts());
    }

    @Test
    void theGlobalRegionIsFoundByItsId() {
        global.record(4, null, 2, 0, 60);
        assertEquals("G", cache.region("G").orElseThrow().id());
    }

    private void measure(int chunkX, double mspt) {
        Anchor anchor = anchors.attach(UUID.randomUUID(), true, "world", chunkX, 0, 0, INTERVAL,
                (x, z) -> x == chunkX).anchor();
        anchor.record(mspt, new BlockPosition(chunkX * 16, 64, 0), 1, 0, 60);
    }
}
