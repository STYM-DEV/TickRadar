package dev.stym.tickradar.command;

import dev.stym.tickradar.alert.discord.DiscordNotifier;
import dev.stym.tickradar.config.SettingsService;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.sample.ErrorReporter;
import dev.stym.tickradar.sample.MetricsCache;
import dev.stym.tickradar.sample.PlayerSamplers;
import dev.stym.tickradar.sample.SamplingCosts;
import dev.stym.tickradar.schedule.TaskScheduler;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.bukkit.command.CommandSender;

public record CommandServices(
        String version,
        CommandSender console,
        SettingsService settings,
        Renderer renderer,
        TaskScheduler scheduler,
        PlayerSamplers samplers,
        OnlinePlayers online,
        MetricsCache metrics,
        SamplingCosts costs,
        ErrorReporter errors,
        DiscordNotifier discord,
        boolean regionTps,
        IntSupplier syntheticAnchors,
        BooleanSupplier stopping,
        Runnable afterReload) {
}
