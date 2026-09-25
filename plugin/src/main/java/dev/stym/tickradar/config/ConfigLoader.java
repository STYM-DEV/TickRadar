package dev.stym.tickradar.config;

import dev.stym.tickradar.config.ConfigSnapshot.AlertChannel;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import dev.stym.tickradar.config.ConfigSnapshot.Alerts;
import dev.stym.tickradar.config.ConfigSnapshot.Discord;
import dev.stym.tickradar.config.ConfigSnapshot.DisplaySettings;
import dev.stym.tickradar.config.ConfigSnapshot.Displays;
import dev.stym.tickradar.config.ConfigSnapshot.Sampling;
import dev.stym.tickradar.config.ConfigSnapshot.SyntheticPoint;
import dev.stym.tickradar.engine.Thresholds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigLoader {

    public static final String FILE_NAME = "config.yml";
    public static final int CURRENT_VERSION = 1;

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");
    private static final String DEFAULT_USERNAME = "TickRadar";
    private static final int WORLD_BORDER = 30_000_000;
    private static final List<String> DEFAULT_NOTIFY = List.of("console", "players", "discord");
    private static final List<String> DEFAULT_DISCORD_LEVELS = List.of("critical", "recovered");

    private ConfigLoader() {
    }

    public static ConfigSnapshot defaults() {
        LoadResult<ConfigSnapshot> result = load(FILE_NAME, "");
        if (result instanceof LoadResult.Loaded<ConfigSnapshot> loaded) {
            return loaded.value();
        }
        throw new IllegalStateException("The default settings cannot be built");
    }

    public static LoadResult<ConfigSnapshot> load(String fileName, String content) {
        return switch (YamlSection.parse(fileName, content)) {
            case LoadResult.Unreadable<YamlSection>(String error) -> new LoadResult.Unreadable<>(error);
            case LoadResult.Loaded<YamlSection>(YamlSection root, List<String> ignored) -> {
                ConfigSnapshot snapshot = read(root);
                yield new LoadResult.Loaded<>(snapshot, root.finish());
            }
        };
    }

    private static ConfigSnapshot read(YamlSection root) {
        int version = root.integer("config-version", CURRENT_VERSION, 1, Integer.MAX_VALUE);
        if (version > CURRENT_VERSION) {
            root.warn("config-version", "is " + version + ", newer than this TickRadar (" + CURRENT_VERSION + "); unknown keys are ignored");
        }
        YamlSection debug = root.section("debug");
        return new ConfigSnapshot(
                language(root),
                sampling(root.section("sampling")),
                thresholds(root.section("thresholds")),
                displays(root.section("display")),
                alerts(root.section("alerts")),
                discord(root.section("discord")),
                root.section("admin").integer("regions-per-page", 8, 1, 50),
                root.bool("metrics", true, false),
                syntheticAnchors(debug),
                debug.integerOrDefault("region-tps-interval-seconds", ConfigSnapshot.DEFAULT_REGION_TPS_INTERVAL,
                        ConfigSnapshot.MIN_REGION_TPS_INTERVAL, ConfigSnapshot.MAX_REGION_TPS_INTERVAL));
    }

    private static String language(YamlSection root) {
        String language = root.string("language", ConfigSnapshot.AUTO_LANGUAGE).trim().toLowerCase(Locale.ROOT);
        if (language.equals(ConfigSnapshot.AUTO_LANGUAGE) || LANGUAGE.matcher(language).matches()) {
            return language;
        }
        root.warn("language", "must be auto or a language code such as en or fr; using auto");
        return ConfigSnapshot.AUTO_LANGUAGE;
    }

    private static Sampling sampling(YamlSection section) {
        return new Sampling(
                section.integer("interval-ticks", 20, Sampling.MIN_INTERVAL, Sampling.MAX_INTERVAL),
                section.integer("history-seconds", 60, Sampling.MIN_HISTORY, Sampling.MAX_HISTORY));
    }

    private static Thresholds thresholds(YamlSection section) {
        double warning = section.decimal("warning", Thresholds.DEFAULT.warning());
        double critical = section.decimal("critical", Thresholds.DEFAULT.critical());
        if (Thresholds.isValid(warning, critical)) {
            return new Thresholds(warning, critical);
        }
        section.warn("warning", "and 'thresholds.critical' must satisfy 0 < warning < critical; using "
                + Thresholds.DEFAULT.warning() + " and " + Thresholds.DEFAULT.critical());
        return Thresholds.DEFAULT;
    }

    private static Displays displays(YamlSection section) {
        return new Displays(display(section.section("bossbar")), display(section.section("actionbar")), display(section.section("tab")));
    }

    private static DisplaySettings display(YamlSection section) {
        return new DisplaySettings(section.bool("enabled", true, true), section.bool("default-on", false, false));
    }

    private static Alerts alerts(YamlSection section) {
        return new Alerts(
                section.bool("enabled", true, true),
                section.integer("trigger-samples", 5, 1, 100),
                section.integer("recovery-samples", 10, 1, 1000),
                section.integer("cooldown-seconds", 300, 0, 86_400),
                enums(section, "notify", DEFAULT_NOTIFY, AlertChannel.class),
                section.bool("global-region", true, true));
    }

    private static Discord discord(YamlSection section) {
        String username = section.string("username", DEFAULT_USERNAME).trim();
        return new Discord(
                section.string("webhook-url", "").trim(),
                enums(section, "levels", DEFAULT_DISCORD_LEVELS, AlertLevel.class),
                section.bool("include-coordinates", false, false),
                username.isEmpty() ? DEFAULT_USERNAME : username);
    }

    private static <E extends Enum<E>> Set<E> enums(YamlSection section, String key, List<String> defaults, Class<E> type) {
        Set<E> values = EnumSet.noneOf(type);
        for (String name : section.strings(key, defaults)) {
            try {
                values.add(Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                section.warn(key, "contains the unknown value '" + name + "'; it is ignored");
            }
        }
        return values;
    }

    private static List<SyntheticPoint> syntheticAnchors(YamlSection debug) {
        List<SyntheticPoint> points = new ArrayList<>();
        for (YamlSection entry : debug.sections("synthetic-anchors")) {
            String world = entry.string("world", "").trim();
            int x = entry.integer("x", 0, -WORLD_BORDER, WORLD_BORDER);
            int z = entry.integer("z", 0, -WORLD_BORDER, WORLD_BORDER);
            if (world.isEmpty()) {
                entry.warn("world", "is missing; synthetic measurement point ignored");
            } else {
                points.add(new SyntheticPoint(world, x, z));
            }
        }
        return points;
    }
}
