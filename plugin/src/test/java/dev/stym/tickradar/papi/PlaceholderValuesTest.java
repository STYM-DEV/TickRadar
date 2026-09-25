package dev.stym.tickradar.papi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.TpsReading;
import dev.stym.tickradar.player.DisplayPrefs;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.player.PlayerState;
import dev.stym.tickradar.sample.MetricsCache;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlaceholderValuesTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long NOW = 100 * SECOND;

    private final Settings settings = new ConfigFiles(Path.of("unused"), PlaceholderValuesTest.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);
    private final AnchorTracker anchors = new AnchorTracker();
    private final Anchor global = Anchor.global(0);
    private final OnlinePlayers online = new OnlinePlayers();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private long clock = NOW;
    private final PlaceholderValues values = new PlaceholderValues(() -> settings, online, new MetricsCache(anchors, global),
            () -> clock);

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '|', value = {
            "region_tps|19.8",
            "region_tps_raw|19.84",
            "region_tps_1m|19.9",
            "region_tps_1m_raw|19.93",
            "region_mspt|42.1",
            "region_mspt_raw|42.125",
            "region_mspt_max|42.1",
            "region_mspt_max_raw|42.125",
            "region_players|3",
            "region_players_raw|3",
            "region_status|warning",
            "region_color|&e",
            "global_tps|20.0",
            "global_tps_raw|20.02",
            "global_mspt|3.5",
            "global_mspt_raw|3.456",
            "worst_mspt|55.0",
            "worst_mspt_raw|55.0",
            "regions|2",
            "regions_raw|2"})
    void everyPlaceholderOfTheSpecification(String params, String expected) {
        measureTheServer();
        assertEquals(expected, values.value(alice, params));
    }

    @Test
    void theStatusIsTranslatedInThePlayerLanguage() {
        measureTheServer();
        online.publish(new PlayerState(alice, null, "R1", "fr", true, true, true, DisplayPrefs.NONE));
        assertEquals("ralentie", values.value(alice, "region_status"));
    }

    @Test
    void frenchPlayersKeepTheDecimalPointInFormattedAndRawValues() {
        measureTheServer();
        online.publish(new PlayerState(alice, null, "R1", "fr", true, true, true, DisplayPrefs.NONE));
        assertEquals("42.1", values.value(alice, "region_mspt"));
        assertEquals("42.125", values.value(alice, "region_mspt_raw"));
        assertEquals("19.8", values.value(alice, "region_tps"));
        assertEquals("19.84", values.value(alice, "region_tps_raw"));
        assertEquals("ralentie", values.value(alice, "region_status"));
    }

    @Test
    void theJvmLocaleChangesNothing() {
        Locale previous = Locale.getDefault();
        try {
            measureTheServer();
            online.publish(new PlayerState(alice, null, "R1", "fr", true, true, true, DisplayPrefs.NONE));
            Locale.setDefault(Locale.GERMANY);
            assertEquals("42.1", values.value(alice, "region_mspt"));
            assertEquals("42.125", values.value(alice, "region_mspt_raw"));
            Locale.setDefault(Locale.FRANCE);
            assertEquals("42.1", values.value(alice, "region_mspt"));
            assertEquals("42.125", values.value(alice, "region_mspt_raw"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @CsvSource({"region_tps", "region_mspt", "region_mspt_max", "region_players", "region_status", "region_color"})
    void anUnknownPlayerGetsAnEmptyText(String params) {
        measureTheServer();
        assertEquals("", values.value(UUID.randomUUID(), params));
        assertEquals("", values.value(null, params));
    }

    @ParameterizedTest
    @CsvSource({"region_tps", "region_mspt", "global_tps", "global_mspt", "worst_mspt", "region_status"})
    void staleValuesGetAnEmptyText(String params) {
        measureTheServer();
        clock = NOW + 4 * SECOND;
        assertEquals("", values.value(alice, params));
    }

    @Test
    void nothingMeasuredYetGivesEmptyTextsAndNoRegion() {
        assertEquals("", values.value(null, "global_mspt"));
        assertEquals("", values.value(null, "worst_mspt"));
        assertEquals("0", values.value(null, "regions"));
    }

    @Test
    void anUnknownTpsGivesAnEmptyTextButKeepsTheMspt() {
        Anchor anchor = attach(alice, 0);
        anchor.record(12.5, new BlockPosition(0, 64, 0), 1, NOW, 60);
        online.publish(new PlayerState(alice, null, anchor.id(), "en", true, true, true, DisplayPrefs.NONE));
        assertEquals("", values.value(alice, "region_tps"));
        assertEquals("12.5", values.value(alice, "region_mspt"));
    }

    @Test
    void globalValuesNeedNoPlayer() {
        measureTheServer();
        assertEquals("3.5", values.value(null, "global_mspt"));
        assertEquals("55.0", values.value(null, "worst_mspt"));
    }

    @ParameterizedTest
    @CsvSource({"unknown", "region_status_raw", "region_color_raw", "raw", "region"})
    void unknownPlaceholdersAreLeftToPlaceholderApi(String params) {
        measureTheServer();
        assertNull(values.value(alice, params));
    }

    @Test
    void theColorFollowsTheStatus() {
        assertEquals("&a", PlaceholderValues.color(HealthStatus.OK));
        assertEquals("&c", PlaceholderValues.color(HealthStatus.CRITICAL));
        assertEquals("&7", PlaceholderValues.color(HealthStatus.UNAVAILABLE));
    }

    private void measureTheServer() {
        global.publishTps(new TpsReading(20.02, 19.99, NOW));
        global.record(3.456, null, 5, NOW, 60);
        Anchor mine = attach(alice, 0);
        mine.publishTps(new TpsReading(19.84, 19.93, NOW));
        mine.record(42.125, new BlockPosition(0, 64, 0), 3, NOW, 60);
        Anchor other = attach(bob, 100);
        other.record(55.0, new BlockPosition(1600, 64, 0), 2, NOW, 60);
        online.publish(new PlayerState(alice, null, mine.id(), "en", true, true, true, DisplayPrefs.NONE));
        online.publish(new PlayerState(bob, null, other.id(), "en", true, true, true, DisplayPrefs.NONE));
    }

    private Anchor attach(UUID player, int chunkX) {
        return anchors.attach(player, true, "world", chunkX, 0, NOW, SECOND, (x, z) -> x == chunkX).anchor();
    }
}
