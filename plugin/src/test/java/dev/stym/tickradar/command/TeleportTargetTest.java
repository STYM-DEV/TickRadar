package dev.stym.tickradar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TeleportTargetTest {

    private static final RegionSnapshot R12 = RegionsPageTest.snapshot("R12", 30);
    private static final RegionSnapshot UNMEASURED = new RegionSnapshot("R13", "world", Optional.empty(), 1, 20, 20, 5,
            RegionStats.EMPTY, 0, false);
    private static final Function<String, Optional<RegionSnapshot>> REGIONS =
            id -> Optional.ofNullable(Map.of("R12", R12, "R13", UNMEASURED).get(id));

    @Test
    void aKnownRegionIsFoundWhateverTheCase() {
        assertEquals(new TeleportTarget(TeleportTarget.Outcome.FOUND, "R12", "world", new BlockPosition(1204, 64, -3380)),
                TeleportTarget.resolve(" r12 ", REGIONS));
    }

    @Test
    void anUnknownOrExpiredRegionIsReported() {
        assertEquals(new TeleportTarget(TeleportTarget.Outcome.UNKNOWN, "R99", null, null), TeleportTarget.resolve("R99", REGIONS));
    }

    @Test
    void aRegionWithoutLocationIsUnknown() {
        assertEquals(new TeleportTarget(TeleportTarget.Outcome.UNKNOWN, "R13", null, null), TeleportTarget.resolve("R13", REGIONS));
    }

    @Test
    void theGlobalRegionHasNoLocation() {
        assertEquals(new TeleportTarget(TeleportTarget.Outcome.GLOBAL, "G", null, null), TeleportTarget.resolve("g", REGIONS));
    }
}
