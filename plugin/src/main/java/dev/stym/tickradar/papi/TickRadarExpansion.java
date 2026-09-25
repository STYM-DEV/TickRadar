package dev.stym.tickradar.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class TickRadarExpansion extends PlaceholderExpansion {

    public static final String PLACEHOLDER_API = "PlaceholderAPI";
    private static final String IDENTIFIER = "tickradar";
    private static final String AUTHOR = "STYM";

    private final String version;
    private final PlaceholderValues values;

    private TickRadarExpansion(String version, PlaceholderValues values) {
        this.version = version;
        this.values = values;
    }

    public static Runnable register(String version, PlaceholderValues values) {
        TickRadarExpansion expansion = new TickRadarExpansion(version, values);
        if (!expansion.register()) {
            return () -> {
            };
        }
        return expansion::unregister;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return AUTHOR;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return values.value(player == null ? null : player.getUniqueId(), params);
    }
}
