package dev.stym.tickradar.sample;

import dev.stym.tickradar.alert.AlertSink;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.SampleSlots;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.schedule.TaskScheduler;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;

public record SamplingContext(
        TaskScheduler scheduler,
        Supplier<Settings> settings,
        BooleanSupplier stopping,
        RegionTracker regions,
        OnlinePlayers online,
        SamplingCosts costs,
        ErrorReporter errors,
        AlertSink alerts,
        Renderer renderer,
        NamespacedKey prefsKey,
        SampleSlots slots) {

    public boolean isStopping() {
        return stopping.getAsBoolean();
    }
}
