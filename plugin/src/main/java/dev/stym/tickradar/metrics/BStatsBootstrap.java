package dev.stym.tickradar.metrics;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertChannel;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.Settings;
import java.util.StringJoiner;
import java.util.function.Supplier;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

public final class BStatsBootstrap {

    public static final int BSTATS_PLUGIN_ID = 34279;
    static final String PLUGIN_ID_PROPERTY = "tickradar.bstats.plugin-id";
    private static final String YES = "yes";
    private static final String NO = "no";
    private static final String NONE = "none";

    private BStatsBootstrap() {
    }

    public static Runnable start(JavaPlugin plugin, Supplier<Settings> settings) {
        int pluginId = pluginId();
        if (pluginId <= 0) {
            return () -> {
            };
        }
        Metrics metrics = new Metrics(plugin, pluginId);
        metrics.addCustomChart(new SimplePie("language", () -> settings.get().config().language()));
        metrics.addCustomChart(new SimplePie("discord_alerts", () -> discordAlerts(settings.get().config())));
        metrics.addCustomChart(new SimplePie("displays_enabled", () -> displaysEnabled(settings.get().config())));
        return metrics::shutdown;
    }

    static int pluginId() {
        return Integer.getInteger(PLUGIN_ID_PROPERTY, BSTATS_PLUGIN_ID);
    }

    static String discordAlerts(ConfigSnapshot config) {
        boolean sent = config.alerts().enabled() && config.alerts().channels().contains(AlertChannel.DISCORD)
                && config.discord().isConfigured();
        return sent ? YES : NO;
    }

    static String displaysEnabled(ConfigSnapshot config) {
        StringJoiner enabled = new StringJoiner("+");
        for (DisplayKind kind : DisplayKind.values()) {
            if (config.displays().of(kind).enabled()) {
                enabled.add(kind.key());
            }
        }
        return enabled.length() == 0 ? NONE : enabled.toString();
    }
}
