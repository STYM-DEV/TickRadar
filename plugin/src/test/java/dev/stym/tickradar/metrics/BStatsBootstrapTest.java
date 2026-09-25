package dev.stym.tickradar.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.LoadResult;
import org.junit.jupiter.api.Test;

class BStatsBootstrapTest {

    @Test
    void metricsAreOnByDefaultAndCanBeTurnedOff() {
        assertEquals(true, config("").metrics());
        assertEquals(false, config("metrics: false\n").metrics());
    }

    @Test
    void aNonPositivePluginIdStartsNothing() {
        System.setProperty(BStatsBootstrap.PLUGIN_ID_PROPERTY, "0");
        try {
            assertEquals(0, BStatsBootstrap.pluginId());
            BStatsBootstrap.start(null, () -> null).run();
        } finally {
            System.clearProperty(BStatsBootstrap.PLUGIN_ID_PROPERTY);
        }
    }

    @Test
    void discordAlertsNeedTheChannelAndAWebhook() {
        assertEquals("no", BStatsBootstrap.discordAlerts(config("")));
        assertEquals("yes", BStatsBootstrap.discordAlerts(config("discord:\n  webhook-url: \"https://discord.com/api/webhooks/1/x\"\n")));
        assertEquals("no", BStatsBootstrap.discordAlerts(config(
                "alerts:\n  notify: [console]\ndiscord:\n  webhook-url: \"https://discord.com/api/webhooks/1/x\"\n")));
    }

    @Test
    void enabledDisplaysAreListed() {
        assertEquals("bossbar+actionbar+tab", BStatsBootstrap.displaysEnabled(config("")));
        assertEquals("actionbar", BStatsBootstrap.displaysEnabled(config(
                "display:\n  bossbar:\n    enabled: false\n  tab:\n    enabled: false\n")));
        assertEquals("none", BStatsBootstrap.displaysEnabled(config(
                "display:\n  bossbar:\n    enabled: false\n  actionbar:\n    enabled: false\n  tab:\n    enabled: false\n")));
    }

    private static ConfigSnapshot config(String yaml) {
        return ((LoadResult.Loaded<ConfigSnapshot>) ConfigLoader.load(ConfigLoader.FILE_NAME, yaml)).value();
    }
}
