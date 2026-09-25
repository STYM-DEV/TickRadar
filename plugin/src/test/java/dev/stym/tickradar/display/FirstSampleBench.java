package dev.stym.tickradar.display;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.OwnershipProbe;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.player.DisplayPrefs;
import dev.stym.tickradar.sample.SamplingWarmUp;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

final class FirstSampleBench {

    static final int FOLLOWING_SAMPLES = 40;

    private static final long SECOND_NANOS = 1_000_000_000L;
    private static final OwnershipProbe ALWAYS_OWNED = (chunkX, chunkZ) -> true;
    private static final DisplayPrefs EVERY_DISPLAY = DisplayPrefs.NONE
            .with(DisplayKind.BOSSBAR, true)
            .with(DisplayKind.ACTIONBAR, true)
            .with(DisplayKind.TAB, true);

    record Timings(long firstNanos, long followingMedianNanos, int firstSampleSends) {

        double ratio() {
            return (double) firstNanos / Math.max(1, followingMedianNanos);
        }

        String describe(String label) {
            return String.format("%s: first sample %.1f us, following samples median %.1f us (x%.1f)", label,
                    firstNanos / 1_000.0, followingMedianNanos / 1_000.0, ratio());
        }
    }

    private final Settings settings = new ConfigFiles(Path.of("unused"), FirstSampleBench.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);
    private final Renderer renderer = new Renderer();
    private final AtomicInteger sends = new AtomicInteger();
    private final Audience player = new Audience() {
        @Override
        public void showBossBar(BossBar bar) {
            sends.incrementAndGet();
        }

        @Override
        public void sendActionBar(Component message) {
            sends.incrementAndGet();
        }

        @Override
        public void sendPlayerListFooter(Component footer) {
            sends.incrementAndGet();
        }
    };

    Settings settings() {
        return settings;
    }

    long warmUpOnAnotherThread() throws InterruptedException {
        long[] duration = new long[1];
        Thread async = new Thread(() -> {
            long start = System.nanoTime();
            renderer.prepareDisplays(settings);
            DisplayWarmUp.run(renderer, settings);
            SamplingWarmUp.run(settings.config());
            duration[0] = System.nanoTime() - start;
        }, "warm-up");
        async.start();
        async.join();
        return duration[0];
    }

    Timings sampleLikeThePlayerTask(String language) {
        ConfigSnapshot config = settings.config();
        AnchorTracker anchors = new AnchorTracker();
        UUID observer = UUID.randomUUID();
        PlayerDisplay display = new PlayerDisplay(renderer);
        long now = 5 * SECOND_NANOS;
        long[] durations = new long[FOLLOWING_SAMPLES + 1];
        int firstSampleSends = 0;
        for (int sample = 0; sample < durations.length; sample++) {
            long start = System.nanoTime();
            Anchor anchor = anchors.attach(observer, true, "world", 0, 0, now, config.sampling().intervalNanos(), ALWAYS_OWNED)
                    .anchor();
            RegionSnapshot snapshot = anchor.record(msptOf(sample), new BlockPosition(3, 64, 3), 1, now,
                    config.sampling().windowCapacity());
            anchor.advanceAlert(snapshot.mspt5s(), config.alertRules(), now);
            display.update(player, settings, language, true, EVERY_DISPLAY, snapshot, now);
            durations[sample] = System.nanoTime() - start;
            if (sample == 0) {
                firstSampleSends = sends.get();
            }
            now += config.sampling().intervalNanos();
        }
        long[] following = Arrays.copyOfRange(durations, 1, durations.length);
        Arrays.sort(following);
        return new Timings(durations[0], following[following.length / 2], firstSampleSends);
    }

    private static double msptOf(int sample) {
        return 12 + (sample % 7) * 0.7;
    }
}
