package dev.stym.tickradar.alert.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.alert.discord.DiscordAlert.Coordinates;
import dev.stym.tickradar.alert.discord.DiscordAlert.Kind;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscordPayloadTest {

    private static final Instant AT = Instant.parse("2026-09-24T10:15:30.123456Z");
    private static final Coordinates NEAR = new Coordinates(1204, -3380);

    @Test
    void theCriticalTextMatchesTheCadrage() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, NEAR, AT);
        assertEquals("Region R12 in world \"world\" (5 players) is at 52.1 ms (critical).",
                DiscordPayload.description(alert, settings(false)));
    }

    @Test
    void everyKindHasItsEnglishText() {
        assertEquals("Region R3 in world \"world\" (1 player) is at 41.0 ms (warning).",
                text(DiscordAlert.region(Kind.WARNING, "R3", "world", 1, 41, NEAR, AT), false));
        assertEquals("Region R3 in world \"world\" has recovered: 31.0 ms.",
                text(DiscordAlert.region(Kind.RECOVERED, "R3", "world", 4, 31, NEAR, AT), false));
        assertEquals("Region R3 in world \"world\" is no longer observed: no players are left there.",
                text(DiscordAlert.region(Kind.ENDED, "R3", "world", 0, 60, NEAR, AT), false));
        assertEquals("The global region G is at 55.5 ms (critical).",
                text(DiscordAlert.global(Kind.CRITICAL, 55.5, AT), false));
        assertEquals("The global region G has recovered: 12.0 ms.",
                text(DiscordAlert.global(Kind.RECOVERED, 12, AT), false));
        assertEquals("Test message from TickRadar: Discord alerts reach this channel.",
                text(DiscordAlert.test(AT), false));
    }

    @Test
    void coordinatesAreAbsentByDefault() {
        String json = DiscordPayload.json(List.of(DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, NEAR, AT)),
                settings(false));
        assertFalse(json.contains("1204"));
        assertFalse(json.contains("-3380"));
    }

    @Test
    void coordinatesArePresentWhenIncluded() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, NEAR, AT);
        assertEquals("Region R12 in world \"world\" around X 1204, Z -3380 (5 players) is at 52.1 ms (critical).",
                text(alert, true));
        assertTrue(DiscordPayload.json(List.of(alert), settings(true)).contains("around X 1204, Z -3380"));
    }

    @Test
    void missingCoordinatesStayAbsentEvenWhenIncluded() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, null, AT);
        assertEquals("Region R12 in world \"world\" (5 players) is at 52.1 ms (critical).", text(alert, true));
    }

    @Test
    void frenchTextsAreUsedWhenTheLanguageIsForced() {
        DiscordSettings french = new DiscordSettings(null, null, EnumSet.allOf(AlertLevel.class), false, "TickRadar",
                DiscordTexts.forLanguage("fr"));
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, NEAR, AT);
        assertEquals("La région R12 du monde «\u00A0world\u00A0» (5 joueurs) est à 52,1 ms (critique).",
                DiscordPayload.description(alert, french));
        assertEquals(DiscordTexts.ENGLISH, DiscordTexts.forLanguage("auto"));
        assertEquals(DiscordTexts.ENGLISH, DiscordTexts.forLanguage("en"));
        assertEquals(DiscordTexts.ENGLISH, DiscordTexts.forLanguage(null));
    }

    @Test
    void mentionsAreAlwaysDisabled() {
        String json = DiscordPayload.json(List.of(DiscordAlert.test(AT)), settings(false));
        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"));
    }

    @Test
    void thePayloadHasTheExpectedShape() {
        String json = DiscordPayload.json(List.of(DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, NEAR, AT)),
                settings(false));
        assertEquals("{\"username\":\"TickRadar\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{\"description\":"
                + "\"Region R12 in world \\\"world\\\" (5 players) is at 52.1 ms (critical).\",\"color\":" + DiscordPayload.COLOR_CRITICAL
                + ",\"timestamp\":\"2026-09-24T10:15:30.123Z\"}]}", json);
    }

    @Test
    void textIsEscapedForJson() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R1", "we\"ird\\na\nme\t\u0001\u2028", 2, 50, null, AT);
        String json = DiscordPayload.json(List.of(alert), settings(false));
        assertTrue(json.contains("we\\\"ird\\\\\\\\na\\nme\\t\\u0001\\u2028"), json);
        assertFalse(json.contains("\n"));
        assertFalse(json.contains("\u0001"));
    }

    @Test
    void jsonQuotingHandlesSurrogates() {
        assertEquals("\"😀\"", Json.quote("😀"));
        assertEquals("\"\uFFFDx\"", Json.quote("\uD83Dx"));
        assertEquals("\"\\u007f\\b\\f\\r\"", Json.quote("\u007f\b\f\r"));
    }

    @Test
    void markdownInNamesIsEscaped() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R1", "world_the_end*[x](y)", 2, 50, null, AT);
        assertEquals("Region R1 in world \"world\\_the\\_end\\*\\[x\\]\\(y\\)\" (2 players) is at 50.0 ms (critical).",
                text(alert, false));
        assertEquals("La région R1 du monde «\u00A0world\\_the\\_end\\*\\[x\\]\\(y\\)\u00A0» (2 joueurs) est à 50,0 ms (critique).",
                DiscordPayload.description(alert, french(false)));
    }

    @Test
    void frenchTextsFollowTheFrenchTypography() {
        assertEquals("La région R12 du monde «\u00A0world\u00A0», vers X 1204, Z -3380 (5 joueurs), est à 52,1 ms (critique).",
                DiscordPayload.description(DiscordAlert.region(Kind.CRITICAL, "R12", "world", 5, 52.1, NEAR, AT), french(true)));
        assertEquals("La région R3 du monde «\u00A0world\u00A0» (1 joueur) est à 41,0 ms (ralentie).",
                DiscordPayload.description(DiscordAlert.region(Kind.WARNING, "R3", "world", 1, 41, NEAR, AT), french(false)));
        assertEquals("La région R3 du monde «\u00A0world\u00A0», vers X 1204, Z -3380, est revenue à la normale\u00A0: 31,0 ms.",
                DiscordPayload.description(DiscordAlert.region(Kind.RECOVERED, "R3", "world", 4, 31, NEAR, AT), french(true)));
        assertEquals("La région R3 du monde «\u00A0world\u00A0» n'est plus observée\u00A0: il n'y reste aucun joueur.",
                DiscordPayload.description(DiscordAlert.region(Kind.ENDED, "R3", "world", 0, 60, NEAR, AT), french(false)));
        assertEquals("La région globale G est revenue à la normale\u00A0: 12,0 ms.",
                DiscordPayload.description(DiscordAlert.global(Kind.RECOVERED, 12, AT), french(true)));
    }

    @Test
    void englishTextsWithCoordinatesNameBothAxes() {
        assertEquals("Region R3 in world \"world\" around X 1204, Z -3380 has recovered: 31.0 ms.",
                text(DiscordAlert.region(Kind.RECOVERED, "R3", "world", 4, 31, NEAR, AT), true));
        assertEquals("Region R3 in world \"world\" around X 1204, Z -3380 is no longer observed: no players are left there.",
                text(DiscordAlert.region(Kind.ENDED, "R3", "world", 0, 60, NEAR, AT), true));
    }

    @Test
    void aWorldNameCannotInjectATemplateValue() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R1", "{mspt}{x}", 2, 50, NEAR, AT);
        assertEquals("Region R1 in world \"{mspt}{x}\" around X 1204, Z -3380 (2 players) is at 50.0 ms (critical).",
                text(alert, true));
    }

    @Test
    void usernameIsOmittedWhenDiscordWouldRefuseIt() {
        assertEquals("TickRadar", DiscordPayload.username(" TickRadar "));
        assertEquals("", DiscordPayload.username("My Discord bot"));
        assertEquals("", DiscordPayload.username("clyde"));
        assertEquals("", DiscordPayload.username("everyone"));
        assertEquals("", DiscordPayload.username("   "));
        assertEquals("ab", DiscordPayload.username("a\u0000b"));
        assertEquals(DiscordPayload.MAX_USERNAME, DiscordPayload.username("x".repeat(200)).length());
        DiscordSettings unnamed = new DiscordSettings(null, null, EnumSet.allOf(AlertLevel.class), false, "discord", null);
        assertFalse(DiscordPayload.json(List.of(DiscordAlert.test(AT)), unnamed).contains("username"));
    }

    @Test
    void aMessageHoldsAtMostTenEmbeds() {
        List<DiscordAlert> alerts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            alerts.add(DiscordAlert.region(Kind.CRITICAL, "R" + i, "world", 2, 50, null, AT));
        }
        assertEquals(10, DiscordPayload.fittingCount(alerts, settings(false)));
        assertEquals(10, count(DiscordPayload.json(alerts, settings(false)), "\"description\""));
    }

    @Test
    void aMessageKeepsItsTextUnderTheDiscordLimit() {
        List<DiscordAlert> alerts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            alerts.add(DiscordAlert.region(Kind.CRITICAL, "R" + i, "w".repeat(600), 2, 50, null, AT));
        }
        int fitting = DiscordPayload.fittingCount(alerts, settings(false));
        assertEquals(3, fitting);
        int total = 0;
        for (DiscordAlert alert : alerts.subList(0, fitting)) {
            total += DiscordPayload.description(alert, settings(false)).length();
        }
        assertTrue(total <= DiscordPayload.MAX_TEXT);
    }

    @Test
    void aSingleOversizedTextIsTruncated() {
        DiscordAlert alert = DiscordAlert.region(Kind.CRITICAL, "R1", "w".repeat(5000), 2, 50, null, AT);
        assertEquals(1, DiscordPayload.fittingCount(List.of(alert), settings(false)));
        String description = DiscordPayload.description(alert, settings(false));
        assertEquals(DiscordPayload.MAX_TEXT, description.length());
        assertTrue(description.endsWith("..."));
    }

    @Test
    void levelsDecideWhichKindsAreAccepted() {
        DiscordSettings defaults = new DiscordSettings(null, null, EnumSet.of(AlertLevel.CRITICAL, AlertLevel.RECOVERED),
                false, "TickRadar", null);
        assertFalse(defaults.accepts(Kind.WARNING));
        assertTrue(defaults.accepts(Kind.CRITICAL));
        assertTrue(defaults.accepts(Kind.RECOVERED));
        assertTrue(defaults.accepts(Kind.ENDED));
        assertTrue(defaults.accepts(Kind.TEST));
    }

    @Test
    void retryAfterIsReadFromTheBody() {
        assertEquals(3.25, Json.number("{\"message\": \"You are being rate limited.\", \"retry_after\": 3.25,"
                + " \"global\": false}", "retry_after").orElseThrow());
        assertEquals(2.0, Json.number("{\"retry_after\":2}", "retry_after").orElseThrow());
        assertTrue(Json.number("{}", "retry_after").isEmpty());
        assertTrue(Json.number("", "retry_after").isEmpty());
    }

    private static String text(DiscordAlert alert, boolean includeCoordinates) {
        return DiscordPayload.description(alert, settings(includeCoordinates));
    }

    private static DiscordSettings settings(boolean includeCoordinates) {
        return new DiscordSettings(null, null, EnumSet.allOf(AlertLevel.class), includeCoordinates, "TickRadar",
                DiscordTexts.ENGLISH);
    }

    private static DiscordSettings french(boolean includeCoordinates) {
        return new DiscordSettings(null, null, EnumSet.allOf(AlertLevel.class), includeCoordinates, "TickRadar",
                DiscordTexts.FRENCH);
    }

    private static int count(String text, String part) {
        int count = 0;
        int index = text.indexOf(part);
        while (index >= 0) {
            count++;
            index = text.indexOf(part, index + part.length());
        }
        return count;
    }
}
