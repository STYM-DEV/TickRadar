package dev.stym.tickradar.papi;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.player.PlayerState;
import dev.stym.tickradar.sample.MetricsCache;
import dev.stym.tickradar.sample.RegionTracker;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class PlaceholderValues {

    static final String NONE = "";
    private static final String RAW_SUFFIX = "_raw";
    private static final Set<String> TEXT_PLACEHOLDERS = Set.of("region_status", "region_color");

    private final Supplier<Settings> settings;
    private final OnlinePlayers online;
    private final MetricsCache metrics;
    private final LongSupplier clock;

    public PlaceholderValues(Supplier<Settings> settings, OnlinePlayers online, MetricsCache metrics, LongSupplier clock) {
        this.settings = settings;
        this.online = online;
        this.metrics = metrics;
        this.clock = clock;
    }

    public String value(UUID player, String params) {
        boolean raw = params.endsWith(RAW_SUFFIX);
        String name = raw ? params.substring(0, params.length() - RAW_SUFFIX.length()) : params;
        if (raw && TEXT_PLACEHOLDERS.contains(name)) {
            return null;
        }
        Settings current = settings.get();
        long now = clock.getAsLong();
        long maxAge = RegionTracker.freshNanos(current.config());
        return switch (name) {
            case "region_tps" -> tps(regionOf(player, now, maxAge), RegionSnapshot::tps5s, raw);
            case "region_tps_1m" -> tps(regionOf(player, now, maxAge), RegionSnapshot::tps1m, raw);
            case "region_mspt" -> mspt(regionOf(player, now, maxAge).map(RegionSnapshot::mspt5s), raw);
            case "region_mspt_max" -> mspt(regionOf(player, now, maxAge).map(snapshot -> snapshot.history().max()), raw);
            case "region_players" -> regionOf(player, now, maxAge).map(snapshot -> Integer.toString(snapshot.players())).orElse(NONE);
            case "region_status" -> status(current, player, now, maxAge);
            case "region_color" -> regionOf(player, now, maxAge).map(snapshot -> color(snapshot.status(current.config().thresholds())))
                    .orElse(NONE);
            case "global_tps" -> tps(fresh(metrics.global(), now, maxAge), RegionSnapshot::tps5s, raw);
            case "global_mspt" -> mspt(fresh(metrics.global(), now, maxAge).map(RegionSnapshot::mspt5s), raw);
            case "worst_mspt" -> mspt(metrics.view(now).worst(now, maxAge).map(RegionSnapshot::mspt5s), raw);
            case "regions" -> Integer.toString(metrics.view(now).count());
            default -> null;
        };
    }

    static String color(HealthStatus status) {
        return switch (status) {
            case OK -> "&a";
            case WARNING -> "&e";
            case CRITICAL -> "&c";
            case UNAVAILABLE -> "&7";
        };
    }

    private Optional<RegionSnapshot> regionOf(UUID player, long now, long maxAge) {
        if (player == null) {
            return Optional.empty();
        }
        if (online.get(player).isEmpty()) {
            return Optional.empty();
        }
        return fresh(metrics.regionOf(player), now, maxAge);
    }

    private String status(Settings current, UUID player, long now, long maxAge) {
        Optional<RegionSnapshot> region = regionOf(player, now, maxAge);
        if (region.isEmpty()) {
            return NONE;
        }
        String language = online.get(player).map(PlayerState::language).orElse(null);
        String key = "status." + region.get().status(current.config().thresholds()).key();
        String text = current.lang().text(current.lang().resolve(language), key);
        return MiniMessage.miniMessage().stripTags(text);
    }

    private static Optional<RegionSnapshot> fresh(Optional<RegionSnapshot> snapshot, long now, long maxAge) {
        return snapshot.filter(value -> value.isFresh(now, maxAge));
    }

    private static String tps(Optional<RegionSnapshot> snapshot, ToDoubleFunction<RegionSnapshot> value, boolean raw) {
        if (snapshot.isEmpty()) {
            return NONE;
        }
        double tps = value.applyAsDouble(snapshot.get());
        if (!ValueFormat.isUsable(tps)) {
            return NONE;
        }
        return raw ? Double.toString(tps) : ValueFormat.tps(tps);
    }

    private static String mspt(Optional<Double> mspt, boolean raw) {
        if (mspt.isEmpty() || !ValueFormat.isUsable(mspt.get())) {
            return NONE;
        }
        return raw ? Double.toString(mspt.get()) : ValueFormat.mspt(mspt.get());
    }
}
