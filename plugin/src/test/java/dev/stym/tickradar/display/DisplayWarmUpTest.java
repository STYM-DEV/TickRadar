package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.Thresholds;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DisplayWarmUpTest {

    @Test
    void theAdventureBossBarLookupItPreloadsStillExists() {
        assertTrue(DisplayWarmUp.loadBossBarImplementationLookup(), DisplayWarmUp.BOSS_BAR_IMPLEMENTATION_LOOKUP);
    }

    @Test
    void theOffScreenSamplesCoverEveryStatusInTheSingularAndThePlural() {
        Thresholds thresholds = new Thresholds(12.5, 30);
        List<RegionSnapshot> snapshots = DisplayWarmUp.snapshots(thresholds);
        Set<HealthStatus> statuses = EnumSet.noneOf(HealthStatus.class);
        Set<Boolean> onePlayer = new HashSet<>();
        for (RegionSnapshot snapshot : snapshots) {
            statuses.add(snapshot.status(thresholds));
            onePlayer.add(snapshot.players() == 1);
        }
        assertEquals(EnumSet.allOf(HealthStatus.class), statuses);
        assertEquals(Set.of(true, false), onePlayer);
    }

    @Test
    void theWarmUpRendersEveryLanguageWithoutFallingBackToTheParser() {
        Renderer renderer = new Renderer();
        Settings settings = settings("thresholds:\n  warning: 12.5\n  critical: 30\n");
        renderer.prepareDisplays(settings);
        DisplayWarmUp.run(renderer, settings);
        assertEquals(0, renderer.displayFallbackTemplates());
    }

    @Test
    void theWarmUpAlsoWorksWithoutPreparedTemplates() {
        Renderer renderer = new Renderer();
        DisplayWarmUp.run(renderer, settings(""));
        assertEquals(0, renderer.displayFallbackTemplates());
    }

    private static Settings settings(String yaml) {
        Settings defaults = new ConfigFiles(Path.of("unused"), DisplayWarmUpTest.class.getClassLoader()::getResourceAsStream)
                .builtInDefaults(1);
        ConfigSnapshot config = ((LoadResult.Loaded<ConfigSnapshot>) ConfigLoader.load(ConfigLoader.FILE_NAME, yaml)).value();
        return new Settings(config, defaults.lang(), 1);
    }
}
