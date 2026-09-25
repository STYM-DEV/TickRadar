package dev.stym.tickradar.display;

import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import dev.stym.tickradar.engine.Thresholds;
import dev.stym.tickradar.player.DisplayPrefs;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;

public final class DisplayWarmUp {

    static final String BOSS_BAR_IMPLEMENTATION_LOOKUP = "net.kyori.adventure.bossbar.BossBarImpl$ImplementationAccessor";

    private static final long SECOND_NANOS = 1_000_000_000L;
    private static final String REGION = "R0";
    private static final String WORLD = "tickradar-warm-up";
    private static final DisplayPrefs EVERY_DISPLAY = DisplayPrefs.NONE
            .with(DisplayKind.BOSSBAR, true)
            .with(DisplayKind.ACTIONBAR, true)
            .with(DisplayKind.TAB, true);

    private DisplayWarmUp() {
    }

    public static void run(Renderer renderer, Settings settings) {
        loadBossBarImplementationLookup();
        List<RegionSnapshot> snapshots = snapshots(settings.config().thresholds());
        for (String language : settings.lang().languages()) {
            renderOffScreen(renderer, settings, language, snapshots);
        }
    }

    static boolean loadBossBarImplementationLookup() {
        try {
            Class.forName(BOSS_BAR_IMPLEMENTATION_LOOKUP, true, BossBar.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    static List<RegionSnapshot> snapshots(Thresholds thresholds) {
        double calm = thresholds.warning() / 2;
        return List.of(
                measured(1, calm),
                measured(1, calm + 1.3),
                measured(3, thresholds.warning()),
                measured(3, thresholds.critical()),
                unavailable());
    }

    private static void renderOffScreen(Renderer renderer, Settings settings, String language, List<RegionSnapshot> snapshots) {
        PlayerDisplay display = new PlayerDisplay(renderer);
        Audience nobody = Audience.empty();
        long now = System.nanoTime();
        for (RegionSnapshot snapshot : snapshots) {
            display.update(nobody, settings, language, true, EVERY_DISPLAY, snapshot, now);
            now += SECOND_NANOS;
        }
        display.update(nobody, settings, language, true, DisplayPrefs.NONE, snapshots.getFirst(), now);
    }

    private static RegionSnapshot measured(int players, double mspt) {
        return new RegionSnapshot(REGION, WORLD, Optional.of(new BlockPosition(0, 64, 0)), players, 19.9, 19.8, mspt,
                new RegionStats(mspt * 0.9, mspt * 1.2, mspt * 1.1, 60), 0, false);
    }

    private static RegionSnapshot unavailable() {
        return new RegionSnapshot(REGION, WORLD, Optional.empty(), 1, Double.NaN, Double.NaN, Double.NaN, RegionStats.EMPTY, 0,
                true);
    }
}
