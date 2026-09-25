package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoundedValuesTest {

    private final Settings settings = new ConfigFiles(Path.of("unused"), RoundedValuesTest.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);
    private final RoundedValues rounded = new RoundedValues();

    @Test
    void theFirstSnapshotIsAChange() {
        assertTrue(rounded.changeTo(settings, snapshot("R1", 3, 19.8, 23.4, 20, 41, 39)));
    }

    @Test
    void valuesThatRoundTheSameAreNoChange() {
        rounded.changeTo(settings, snapshot("R1", 3, 19.8, 23.4, 20, 41, 39));
        assertFalse(rounded.changeTo(settings, snapshot("R1", 3, 19.83, 23.41, 20.04, 40.96, 39.01)));
    }

    @Test
    void anyShownValueThatChangesIsAChange() {
        rounded.changeTo(settings, snapshot("R1", 3, 19.8, 23.4, 20, 41, 39));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 3, 19.8, 23.4, 20, 41, 39)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, 19.8, 23.4, 20, 41, 39)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, 19.7, 23.4, 20, 41, 39)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, 19.7, 23.5, 20, 41, 39)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, 19.7, 23.5, 20.1, 41, 39)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, 19.7, 23.5, 20.1, 41.1, 39)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, 19.7, 23.5, 20.1, 41.1, 39.1)));
        assertTrue(rounded.changeTo(settings, snapshot("R2", 1, Double.NaN, 23.5, 20.1, 41.1, 39.1)));
    }

    @Test
    void aStatusChangeWithTheSameValuesIsAChange() {
        rounded.changeTo(settings, snapshot("R1", 3, 19.8, 23.4, 20, 41, 39));
        RegionSnapshot unavailable = new RegionSnapshot("R1", "world", Optional.empty(), 3, 19.8, 19.8, 23.4,
                new RegionStats(20, 41, 39, 60), 0, true);
        assertTrue(rounded.changeTo(settings, unavailable));
    }

    private static RegionSnapshot snapshot(String id, int players, double tps, double mspt, double average, double max, double p95) {
        return new RegionSnapshot(id, "world", Optional.empty(), players, tps, tps, mspt, new RegionStats(average, max, p95, 60), 0,
                false);
    }
}
