package dev.stym.tickradar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.alert.discord.DiscordStatus;
import dev.stym.tickradar.engine.CostMeter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StatusReportTest {

    private static final CostMeter.Summary NONE = new CostMeter.Summary(0, 0, 0, 0);

    @Test
    void linesUseTheStableKeysReadByTheTestVerdicts() {
        StatusReport report = new StatusReport(true, 20, 60, 5,
                new CostMeter.Summary(1200, 12.34, 31, 80.5),
                new CostMeter.Summary(200, 25.06, 48, 80.5),
                new CostMeter.Summary(200, 9.94, 21, 60),
                new CostMeter.Summary(300, 40.2, 75, 90),
                new CostMeter.Summary(300, 5, 6, 7),
                new CostMeter.Summary(300, 35, 70, 80),
                new CostMeter.Summary(60, 8, 11, 12),
                new CostMeter.Summary(12, 1500, 2400, 2600),
                new CostMeter.Summary(1, 1400, 1400, 1400),
                3, 2, 1, "off", 2, 4, 2, 5);
        assertEquals(List.of(
                "measurement=region regionTPS=yes interval=20 history=60 tps_interval=5",
                "sample_avg_us=12.3 sample_p99_us=31.0 sample_max_us=80.5 samples=1200"
                        + " player_sample_avg_us=25.1 player_sample_p99_us=48.0 display_avg_us=9.9 display_p99_us=21.0"
                        + " display_fallback_templates=4 player_tick_peak=2 player_tick_peak_max=5",
                "measure_avg_us=40.2 measure_p99_us=75.0 measure_max_us=90.0 measures=300",
                "mspt_call_avg_us=5.0 mspt_call_p99_us=6.0 mspt_call_max_us=7.0 tps_call_avg_us=35.0 tps_call_p99_us=70.0 tps_call_max_us=80.0"
                        + " tps_thread_avg_us=1500.0 tps_thread_p99_us=2400.0 tps_thread_max_us=2600.0 tps_passes=12",
                "global_avg_us=8.0 global_p99_us=11.0 global_max_us=12.0 global_samples=60"
                        + " global_tps_avg_us=1400.0 global_tps_p99_us=1400.0 global_tps_max_us=1400.0 global_tps_reads=1",
                "anchors=3 players=2 synthetic=1 alerts_active=2",
                "discord=off"), report.lines());
    }

    @Test
    void existingKeysKeepTheirOrderBeforeTheTpsThreadKeys() {
        assertEquals(List.of("mspt_call_avg_us", "mspt_call_p99_us", "mspt_call_max_us", "tps_call_avg_us", "tps_call_p99_us",
                "tps_call_max_us", "tps_thread_avg_us", "tps_thread_p99_us", "tps_thread_max_us", "tps_passes"), keysOfLine(3));
    }

    @Test
    void playerSampleAndDisplayKeysComeAfterTheExistingSampleKeys() {
        assertEquals(List.of("sample_avg_us", "sample_p99_us", "sample_max_us", "samples", "player_sample_avg_us",
                "player_sample_p99_us", "display_avg_us", "display_p99_us", "display_fallback_templates", "player_tick_peak",
                "player_tick_peak_max"), keysOfLine(1));
    }

    @Test
    void theFallbackTemplateCountComesBeforeTheTickPeaks() {
        StatusReport report = new StatusReport(true, 20, 60, 5, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, 0, 0, 0, "off", 0, 3, 0, 0);
        assertTrue(report.lines().get(1).endsWith(" display_p99_us=0.0 display_fallback_templates=3 player_tick_peak=0"
                + " player_tick_peak_max=0"));
    }

    @Test
    void activeAlertsComeAfterTheExistingAnchorKeys() {
        assertEquals(List.of("anchors", "players", "synthetic", "alerts_active"), keysOfLine(5));
    }

    @Test
    void theDiscordCountersComeAfterTheDiscordState() {
        String summary = new DiscordStatus(DiscordStatus.State.RATE_LIMITED, 3, 4, 5, 6, 429, "https://discord.com/api/webhooks/1234.../****")
                .summary();
        StatusReport report = new StatusReport(true, 20, 60, 5, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, 0, 0, 0, summary, 0, 0, 0, 0);
        String line = report.lines().get(6);
        assertEquals("discord=rate-limited discord_queue=3 discord_dropped=4 discord_sent=5 discord_failed=6 discord_last_http=429", line);
        assertEquals(List.of("discord", "discord_queue", "discord_dropped", "discord_sent", "discord_failed", "discord_last_http"),
                Arrays.stream(line.split(" ")).map(pair -> pair.substring(0, pair.indexOf('='))).toList());
        assertFalse(line.contains("webhooks"));
    }

    @Test
    void theJvmLocaleNeverAddsADecimalComma() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            StatusReport france = new StatusReport(true, 20, 60, 5,
                    new CostMeter.Summary(1200, 12.34, 31, 80.5), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, 0, 0, 0, "off", 0, 0, 0, 0);
            for (String line : france.lines()) {
                assertFalse(line.contains(","), line);
            }
            Locale.setDefault(Locale.GERMANY);
            StatusReport germany = new StatusReport(true, 20, 60, 5,
                    new CostMeter.Summary(1200, 12.34, 31, 80.5), NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, 0, 0, 0, "off", 0, 0, 0, 0);
            for (String line : germany.lines()) {
                assertFalse(line.contains(","), line);
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void missingRegionTpsIsShown() {
        StatusReport report = new StatusReport(false, 40, 120, 5, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, 0, 0, 0, "off", 0, 0, 0, 0);
        assertEquals("measurement=region regionTPS=no interval=40 history=120 tps_interval=5", report.lines().getFirst());
    }

    private static List<String> keysOfLine(int index) {
        String line = new StatusReport(true, 20, 60, 5, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, 0, 0, 0, "off", 0, 0, 0, 0)
                .lines().get(index);
        return Arrays.stream(line.split(" ")).map(pair -> pair.substring(0, pair.indexOf('='))).toList();
    }
}
