package dev.stym.tickradar.alert.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.alert.AlertEvent;
import dev.stym.tickradar.alert.discord.DiscordAlert.Coordinates;
import dev.stym.tickradar.alert.discord.DiscordAlert.Kind;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import dev.stym.tickradar.engine.AlertKind;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.BlockPosition;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiscordAlertTest {

    private static final Instant AT = Instant.parse("2026-09-24T10:00:00Z");
    private static final Optional<BlockPosition> POSITION = Optional.of(new BlockPosition(1204, 64, -3380));

    @ParameterizedTest
    @EnumSource(AlertKind.class)
    void everyAlertKindKeepsItsNameAndLevel(AlertKind kind) {
        DiscordAlert alert = DiscordAlert.from(event(kind), false);
        assertEquals(kind.name(), alert.kind().name());
        assertEquals(event(kind).level(), alert.kind().level());
    }

    @Test
    void anEndedRegionIsSentAtTheRecoveredLevel() {
        assertEquals(Kind.ENDED, DiscordAlert.kindOf(AlertKind.ENDED));
        assertEquals(AlertLevel.RECOVERED, Kind.ENDED.level());
    }

    @Test
    void aRegionAlertKeepsItsValuesButNotItsCoordinatesByDefault() {
        DiscordAlert alert = DiscordAlert.from(event(AlertKind.CRITICAL), false);
        assertEquals(new DiscordAlert(Kind.CRITICAL, "R12", "world", 5, 52.1, null, AT), alert);
        assertFalse(alert.isGlobal());
        assertFalse(alert.hasCoordinates());
    }

    @Test
    void coordinatesAreKeptOnlyWhenTheyAreAllowed() {
        DiscordAlert alert = DiscordAlert.from(event(AlertKind.WARNING), true);
        assertEquals(new Coordinates(1204, -3380), alert.coordinates());
    }

    @Test
    void aRegionWithoutPositionHasNoCoordinatesEvenWhenAllowed() {
        AlertEvent event = new AlertEvent("R3", AlertKind.RECOVERED, 20, 1, "world_nether", Optional.empty(), AT);
        assertNull(DiscordAlert.from(event, true).coordinates());
    }

    @Test
    void theGlobalRegionHasNoWorldNoPlayersAndNoCoordinates() {
        AlertEvent event = new AlertEvent(Anchor.GLOBAL_ID, AlertKind.CRITICAL, 61.5, 7, "world", POSITION, AT);
        DiscordAlert alert = DiscordAlert.from(event, true);
        assertTrue(alert.isGlobal());
        assertEquals(DiscordAlert.global(Kind.CRITICAL, 61.5, AT), alert);
    }

    private static AlertEvent event(AlertKind kind) {
        return new AlertEvent("R12", kind, 52.1, 5, "world", POSITION, AT);
    }
}
