package dev.stym.tickradar.config;

import dev.stym.tickradar.engine.AlertRules;
import dev.stym.tickradar.engine.SampleWindow;
import dev.stym.tickradar.engine.Thresholds;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ConfigSnapshot(
        String language,
        Sampling sampling,
        Thresholds thresholds,
        Displays displays,
        Alerts alerts,
        Discord discord,
        int regionsPerPage,
        boolean metrics,
        List<SyntheticPoint> syntheticAnchors,
        int regionTpsIntervalSeconds) {

    public static final String AUTO_LANGUAGE = "auto";
    public static final int DEFAULT_REGION_TPS_INTERVAL = 5;
    public static final int MIN_REGION_TPS_INTERVAL = 1;
    public static final int MAX_REGION_TPS_INTERVAL = 60;

    public ConfigSnapshot {
        syntheticAnchors = List.copyOf(syntheticAnchors);
    }

    public boolean isAutoLanguage() {
        return AUTO_LANGUAGE.equals(language);
    }

    public AlertRules alertRules() {
        return AlertRules.of(thresholds, alerts.triggerSamples(), alerts.recoverySamples(), alerts.cooldownSeconds());
    }

    public record Sampling(int intervalTicks, int historySeconds) {

        public static final int MIN_INTERVAL = 10;
        public static final int MAX_INTERVAL = 200;
        public static final int MIN_HISTORY = 10;
        public static final int MAX_HISTORY = 300;
        private static final long NANOS_PER_TICK = 50_000_000L;
        private static final int TICKS_PER_SECOND = 20;

        public long intervalNanos() {
            return intervalTicks * NANOS_PER_TICK;
        }

        public int windowCapacity() {
            int samples = (historySeconds * TICKS_PER_SECOND + intervalTicks - 1) / intervalTicks;
            return Math.clamp(samples, 1, SampleWindow.MAX_CAPACITY);
        }
    }

    public enum DisplayKind {
        BOSSBAR,
        ACTIONBAR,
        TAB;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record DisplaySettings(boolean enabled, boolean defaultOn) {
    }

    public record Displays(DisplaySettings bossbar, DisplaySettings actionbar, DisplaySettings tab) {

        public DisplaySettings of(DisplayKind kind) {
            return switch (kind) {
                case BOSSBAR -> bossbar;
                case ACTIONBAR -> actionbar;
                case TAB -> tab;
            };
        }
    }

    public enum AlertChannel {
        CONSOLE,
        PLAYERS,
        DISCORD
    }

    public enum AlertLevel {
        WARNING,
        CRITICAL,
        RECOVERED
    }

    public record Alerts(boolean enabled, int triggerSamples, int recoverySamples, int cooldownSeconds,
                         Set<AlertChannel> channels, boolean globalRegion) {

        public Alerts {
            channels = Set.copyOf(channels);
        }
    }

    public record Discord(String webhookUrl, Set<AlertLevel> levels, boolean includeCoordinates, String username) {

        public Discord {
            levels = Set.copyOf(levels);
        }

        public boolean isConfigured() {
            return !webhookUrl.isBlank();
        }

        @Override
        public String toString() {
            return "Discord[webhookUrl=" + (isConfigured() ? "****" : "") + ", levels=" + levels
                    + ", includeCoordinates=" + includeCoordinates + ", username=" + username + "]";
        }
    }

    public record SyntheticPoint(String world, int x, int z) {
    }
}
