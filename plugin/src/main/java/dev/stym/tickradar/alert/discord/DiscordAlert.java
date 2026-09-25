package dev.stym.tickradar.alert.discord;

import dev.stym.tickradar.alert.AlertEvent;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import dev.stym.tickradar.engine.AlertKind;
import java.time.Instant;
import java.util.Objects;

public record DiscordAlert(Kind kind, String regionId, String world, int players, double mspt, Coordinates coordinates,
                           Instant at) {

    public static final String GLOBAL_REGION_ID = "G";

    public enum Kind {
        WARNING(AlertLevel.WARNING),
        CRITICAL(AlertLevel.CRITICAL),
        RECOVERED(AlertLevel.RECOVERED),
        ENDED(AlertLevel.RECOVERED),
        TEST(null);

        private final AlertLevel level;

        Kind(AlertLevel level) {
            this.level = level;
        }

        public AlertLevel level() {
            return level;
        }
    }

    public record Coordinates(int x, int z) {
    }

    public DiscordAlert {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        regionId = regionId == null ? "" : regionId;
        world = world == null ? "" : world;
        players = Math.max(0, players);
    }

    public static DiscordAlert region(Kind kind, String regionId, String world, int players, double mspt,
                                      Coordinates coordinates, Instant at) {
        return new DiscordAlert(kind, regionId, world, players, mspt, coordinates, at);
    }

    public static DiscordAlert global(Kind kind, double mspt, Instant at) {
        return new DiscordAlert(kind, GLOBAL_REGION_ID, "", 0, mspt, null, at);
    }

    public static DiscordAlert from(AlertEvent event, boolean includeCoordinates) {
        Kind kind = kindOf(event.kind());
        if (event.isGlobal()) {
            return global(kind, event.mspt(), event.at());
        }
        Coordinates coordinates = includeCoordinates
                ? event.position().map(position -> new Coordinates(position.x(), position.z())).orElse(null)
                : null;
        return region(kind, event.regionId(), event.world(), event.players(), event.mspt(), coordinates, event.at());
    }

    static Kind kindOf(AlertKind kind) {
        return switch (kind) {
            case WARNING -> Kind.WARNING;
            case CRITICAL -> Kind.CRITICAL;
            case RECOVERED -> Kind.RECOVERED;
            case ENDED -> Kind.ENDED;
        };
    }

    public static DiscordAlert test(Instant at) {
        return new DiscordAlert(Kind.TEST, "", "", 0, Double.NaN, null, at);
    }

    public boolean isGlobal() {
        return GLOBAL_REGION_ID.equals(regionId);
    }

    public boolean hasCoordinates() {
        return coordinates != null;
    }
}
