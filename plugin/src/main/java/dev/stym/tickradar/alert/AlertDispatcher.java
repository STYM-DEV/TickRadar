package dev.stym.tickradar.alert;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertChannel;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.player.PlayerState;
import dev.stym.tickradar.sample.ErrorReporter;
import dev.stym.tickradar.schedule.TaskScheduler;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class AlertDispatcher implements AlertSink {

    private final Supplier<Settings> settings;
    private final OnlinePlayers online;
    private final TaskScheduler scheduler;
    private final AlertMessages messages;
    private final Logger logger;
    private final ErrorReporter errors;
    private final BooleanSupplier stopping;
    private final Map<AlertChannel, CopyOnWriteArrayList<AlertSink>> sinks = new EnumMap<>(AlertChannel.class);

    public AlertDispatcher(Supplier<Settings> settings, OnlinePlayers online, TaskScheduler scheduler, Renderer renderer,
                           Logger logger, ErrorReporter errors, BooleanSupplier stopping) {
        this.settings = settings;
        this.online = online;
        this.scheduler = scheduler;
        this.messages = new AlertMessages(renderer);
        this.logger = logger;
        this.errors = errors;
        this.stopping = stopping;
        for (AlertChannel channel : AlertChannel.values()) {
            sinks.put(channel, new CopyOnWriteArrayList<>());
        }
    }

    public void register(AlertChannel channel, AlertSink sink) {
        sinks.get(channel).add(sink);
    }

    public void unregister(AlertSink sink) {
        sinks.values().forEach(registered -> registered.removeIf(candidate -> candidate == sink));
    }

    @Override
    public void accept(AlertEvent event) {
        if (stopping.getAsBoolean()) {
            return;
        }
        try {
            Settings current = settings.get();
            ConfigSnapshot.Alerts alerts = current.config().alerts();
            if (!alerts.enabled()) {
                return;
            }
            if (alerts.channels().contains(AlertChannel.CONSOLE)) {
                toConsole(current, event);
            }
            if (alerts.channels().contains(AlertChannel.PLAYERS)) {
                toPlayers(current, event);
            }
            toSinks(alerts, event);
        } catch (Throwable error) {
            if (!stopping.getAsBoolean()) {
                errors.report("the dispatch of an alert", error);
            }
        }
    }

    private void toConsole(Settings current, AlertEvent event) {
        ConfigSnapshot config = current.config();
        String language = current.lang().resolve(config.isAutoLanguage() ? null : config.language());
        String line = messages.forConsole(current, language, event);
        Level level = event.kind().isSlow() ? Level.WARNING : Level.INFO;
        scheduler.runAsync(() -> logger.log(level, line));
    }

    private void toPlayers(Settings current, AlertEvent event) {
        Map<MessageKey, Component> byLanguageAndTeleport = new HashMap<>();
        for (PlayerState state : online.all()) {
            if (!state.canReceiveAlerts() || !state.prefs().alertsOn()) {
                continue;
            }
            MessageKey key = new MessageKey(state.language(), state.canTeleport());
            Component message = byLanguageAndTeleport.computeIfAbsent(key,
                    resolved -> messages.forPlayer(current, resolved.language(), event, resolved.canTeleport()));
            Player player = state.player();
            scheduler.runForPlayer(player, () -> sendTo(player, message));
        }
    }

    private record MessageKey(String language, boolean canTeleport) {
    }

    private void sendTo(Player player, Component message) {
        if (stopping.getAsBoolean()) {
            return;
        }
        try {
            player.sendMessage(message);
        } catch (Throwable error) {
            if (!stopping.getAsBoolean()) {
                errors.report("the delivery of an alert to a player", error);
            }
        }
    }

    private void toSinks(ConfigSnapshot.Alerts alerts, AlertEvent event) {
        for (AlertChannel channel : alerts.channels()) {
            for (AlertSink sink : sinks.get(channel)) {
                try {
                    sink.accept(event);
                } catch (Throwable error) {
                    errors.report("an alert channel (" + channel + ")", error);
                }
            }
        }
    }
}
