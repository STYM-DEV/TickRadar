package dev.stym.tickradar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigSnapshot.AlertChannel;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import dev.stym.tickradar.config.ConfigSnapshot.SyntheticPoint;
import dev.stym.tickradar.engine.Thresholds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

    static String resource(String name) throws IOException {
        try (InputStream in = ConfigLoaderTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static LoadResult.Loaded<ConfigSnapshot> loaded(String yaml) {
        return Loads.loaded(ConfigLoader.load("config.yml", yaml));
    }

    private static String unreadable(String yaml) {
        return assertInstanceOf(LoadResult.Unreadable.class, ConfigLoader.load("config.yml", yaml)).error();
    }

    @Test
    void shippedFileLoadsWithoutWarningAndEqualsTheDefaults() throws IOException {
        LoadResult.Loaded<ConfigSnapshot> shipped = loaded(resource("config.yml"));
        assertEquals(List.of(), shipped.warnings());
        assertEquals(ConfigLoader.defaults(), shipped.value());
    }

    @Test
    void defaultsMatchTheSpecification() {
        ConfigSnapshot defaults = ConfigLoader.defaults();
        assertEquals("auto", defaults.language());
        assertTrue(defaults.isAutoLanguage());
        assertEquals(20, defaults.sampling().intervalTicks());
        assertEquals(60, defaults.sampling().historySeconds());
        assertEquals(60, defaults.sampling().windowCapacity());
        assertEquals(1_000_000_000L, defaults.sampling().intervalNanos());
        assertEquals(Thresholds.DEFAULT, defaults.thresholds());
        assertTrue(defaults.displays().bossbar().enabled());
        assertFalse(defaults.displays().bossbar().defaultOn());
        assertTrue(defaults.alerts().enabled());
        assertEquals(5, defaults.alerts().triggerSamples());
        assertEquals(10, defaults.alerts().recoverySamples());
        assertEquals(300, defaults.alerts().cooldownSeconds());
        assertEquals(Set.of(AlertChannel.CONSOLE, AlertChannel.PLAYERS, AlertChannel.DISCORD), defaults.alerts().channels());
        assertEquals("", defaults.discord().webhookUrl());
        assertFalse(defaults.discord().isConfigured());
        assertEquals(Set.of(AlertLevel.CRITICAL, AlertLevel.RECOVERED), defaults.discord().levels());
        assertFalse(defaults.discord().includeCoordinates());
        assertEquals("TickRadar", defaults.discord().username());
        assertEquals(8, defaults.regionsPerPage());
        assertTrue(defaults.metrics());
        assertEquals(List.of(), defaults.syntheticAnchors());
        assertEquals(5, defaults.regionTpsIntervalSeconds());
    }

    @Test
    void theRegionTpsIntervalIsAHiddenDebugKey() throws IOException {
        assertFalse(resource("config.yml").contains("region-tps-interval-seconds"));
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                debug:
                  region-tps-interval-seconds: 12
                """);
        assertEquals(12, result.value().regionTpsIntervalSeconds());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void anInvalidRegionTpsIntervalFallsBackToTheDefault() {
        for (String value : List.of("0", "-3", "61", "3600", "abc", "2.5")) {
            LoadResult.Loaded<ConfigSnapshot> result = loaded("debug:\n  region-tps-interval-seconds: " + value + "\n");
            assertEquals(5, result.value().regionTpsIntervalSeconds(), value);
            assertEquals(1, result.warnings().size(), value);
            assertTrue(result.warnings().getFirst().startsWith("config.yml:2: 'debug.region-tps-interval-seconds' must be"), value);
            assertTrue(result.warnings().getFirst().endsWith("using 5"), value);
        }
    }

    @Test
    void theRegionTpsIntervalLivesNextToTheSyntheticAnchors() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                debug:
                  region-tps-interval-seconds: 1
                  synthetic-anchors:
                    - {world: world, x: 10, z: 20}
                """);
        assertEquals(1, result.value().regionTpsIntervalSeconds());
        assertEquals(List.of(new SyntheticPoint("world", 10, 20)), result.value().syntheticAnchors());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void intervalAndHistoryAreBoundedWithAWarningAtTheirLine() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                sampling:
                  interval-ticks: 5
                  history-seconds: 9000
                """);
        assertEquals(10, result.value().sampling().intervalTicks());
        assertEquals(300, result.value().sampling().historySeconds());
        assertEquals(600, result.value().sampling().windowCapacity());
        assertEquals(List.of(
                "config.yml:2: 'sampling.interval-ticks' must be between 10 and 200; using 10",
                "config.yml:3: 'sampling.history-seconds' must be between 10 and 300; using 300"), result.warnings());
    }

    @Test
    void inconsistentThresholdsFallBackToTheDefaults() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                thresholds:
                  warning: 60
                  critical: 50
                """);
        assertEquals(Thresholds.DEFAULT, result.value().thresholds());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().startsWith("config.yml:2: 'thresholds.warning' and 'thresholds.critical'"));
    }

    @Test
    void customThresholdsAreKept() {
        assertEquals(new Thresholds(5, 7.5), loaded("thresholds: {warning: 5, critical: 7.5}").value().thresholds());
    }

    @Test
    void invalidValuesTakeTheSafestDefault() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                language: klingon-empire
                metrics: maybe
                display:
                  bossbar:
                    default-on: sometimes
                discord:
                  include-coordinates: perhaps
                admin:
                  regions-per-page: many
                """);
        ConfigSnapshot config = result.value();
        assertEquals("auto", config.language());
        assertFalse(config.metrics());
        assertFalse(config.displays().bossbar().defaultOn());
        assertFalse(config.discord().includeCoordinates());
        assertEquals(8, config.regionsPerPage());
        assertEquals(5, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("config.yml:1: 'language'"));
        assertTrue(result.warnings().get(1).startsWith("config.yml:2: 'metrics' must be true or false; using false"));
        assertTrue(result.warnings().get(2).startsWith("config.yml:5: 'display.bossbar.default-on'"));
        assertTrue(result.warnings().get(3).startsWith("config.yml:7: 'discord.include-coordinates'"));
        assertTrue(result.warnings().get(4).startsWith("config.yml:9: 'admin.regions-per-page' must be a whole number"));
    }

    @Test
    void unknownKeysAreReportedWithTheirLine() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                sampling:
                  interval-ticks: 40
                  speed: 3
                watch-points:
                  spawn: {world: world, x: 0, z: 0}
                """);
        assertEquals(40, result.value().sampling().intervalTicks());
        assertEquals(List.of(
                "config.yml:3: unknown key 'sampling.speed' ignored",
                "config.yml:4: unknown key 'watch-points' ignored"), result.warnings());
    }

    @Test
    void unknownListValuesAreIgnored() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                alerts:
                  notify: [console, pigeon]
                discord:
                  levels: [critical, apocalypse]
                """);
        assertEquals(Set.of(AlertChannel.CONSOLE), result.value().alerts().channels());
        assertEquals(Set.of(AlertLevel.CRITICAL), result.value().discord().levels());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void aValueWhereASectionIsExpectedKeepsTheDefaults() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("sampling: 20\n");
        assertEquals(20, result.value().sampling().intervalTicks());
        assertEquals(List.of("config.yml:1: 'sampling' must be a section of settings; using the defaults"), result.warnings());
    }

    @Test
    void duplicateKeysAreReported() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                metrics: true
                metrics: false
                """);
        assertFalse(result.value().metrics());
        assertEquals(List.of("config.yml:2: 'metrics' is set twice; the last value is used"), result.warnings());
    }

    @Test
    void brokenYamlIsUnreadableAndGivesItsLine() {
        String error = unreadable("""
                sampling:
                  interval-ticks: 20
                 history-seconds: [60
                """);
        assertTrue(error.startsWith("config.yml:"), error);
        assertFalse(error.contains("\n"));
    }

    @Test
    void aFileThatIsNotASetOfSettingsIsUnreadable() {
        assertEquals("config.yml:1: the file must be a list of 'key: value' settings", unreadable("- just\n- a list\n"));
    }

    @Test
    void anEmptyFileGivesTheDefaults() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("");
        assertEquals(ConfigLoader.defaults(), result.value());
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void newerConfigVersionIsReported() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("config-version: 7\n");
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().getFirst().contains("newer than this TickRadar"));
    }

    @Test
    void hiddenSyntheticAnchorsAreRead() {
        LoadResult.Loaded<ConfigSnapshot> result = loaded("""
                debug:
                  synthetic-anchors:
                    - {world: world, x: 0, z: 0}
                    - {world: world_nether, x: 10000, z: -10000}
                    - {x: 5, z: 5}
                    - just-a-string
                """);
        assertEquals(List.of(new SyntheticPoint("world", 0, 0), new SyntheticPoint("world_nether", 10_000, -10_000)),
                result.value().syntheticAnchors());
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("config.yml:5: 'debug.synthetic-anchors.world' is missing"));
        assertTrue(result.warnings().get(1).startsWith("config.yml:6: 'debug.synthetic-anchors' entries must be sections"));
    }

    @Test
    void theWebhookUrlNeverAppearsInTheTextOfTheSettings() {
        String secret = "https://discord.com/api/webhooks/123456/very-secret-token";
        ConfigSnapshot config = loaded("discord:\n  webhook-url: \"" + secret + "\"\n").value();
        assertTrue(config.discord().isConfigured());
        assertEquals(secret, config.discord().webhookUrl());
        assertFalse(config.toString().contains("very-secret-token"));
        assertFalse(config.discord().toString().contains("very-secret-token"));
    }
}
