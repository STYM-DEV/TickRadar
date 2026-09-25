package dev.stym.tickradar.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.stym.tickradar.alert.discord.DiscordTestResult;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import dev.stym.tickradar.sample.PlayerSampler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TickRadarCommand {

    public static final String PERM_USE = "tickradar.use";
    public static final String PERM_STATUS = "tickradar.admin.status";
    public static final String PERM_RELOAD = "tickradar.admin.reload";
    public static final String PERM_REGIONS = "tickradar.admin.regions";
    public static final String PERM_TELEPORT = PlayerSampler.PERM_TELEPORT;
    public static final String PERM_DISCORD = "tickradar.admin.discord";
    static final long SHOWN_RATE_LIMIT_WAIT_MILLIS = 100;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double BLOCK_CENTER = 0.5;

    private final CommandServices services;
    private final RegionList regionList;

    public TickRadarCommand(CommandServices services) {
        this.services = services;
        this.regionList = new RegionList(services.renderer());
    }

    public void register(Commands commands) {
        commands.register(root(), "Region-aware TPS/MSPT for Folia", List.of("tradar"));
    }

    LiteralCommandNode<CommandSourceStack> root() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tickradar")
                .requires(source -> source.getSender().hasPermission(PERM_USE))
                .executes(context -> run(() -> region(context.getSource().getSender())));
        for (DisplayKind kind : DisplayKind.values()) {
            root.then(display(kind));
        }
        return root
                .then(Commands.literal("regions")
                        .requires(source -> source.getSender().hasPermission(PERM_REGIONS))
                        .executes(context -> run(() -> regions(context.getSource().getSender(), 1)))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> run(() -> regions(context.getSource().getSender(),
                                        IntegerArgumentType.getInteger(context, "page"))))))
                .then(Commands.literal("tp")
                        .requires(source -> source.getSender().hasPermission(PERM_TELEPORT))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    services.metrics().regionIds().stream()
                                            .filter(id -> id.regionMatches(true, 0, builder.getRemaining(), 0, builder.getRemaining().length()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> run(() -> teleport(context.getSource().getSender(),
                                        StringArgumentType.getString(context, "id"))))))
                .then(Commands.literal("alerts")
                        .requires(source -> source.getSender().hasPermission(PlayerSampler.PERM_ALERTS))
                        .executes(context -> run(() -> alerts(context.getSource().getSender(), null)))
                        .then(Commands.literal("on").executes(context -> run(() -> alerts(context.getSource().getSender(), true))))
                        .then(Commands.literal("off").executes(context -> run(() -> alerts(context.getSource().getSender(), false)))))
                .then(Commands.literal("status")
                        .requires(source -> source.getSender().hasPermission(PERM_STATUS))
                        .executes(context -> run(() -> status(context.getSource().getSender()))))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission(PERM_RELOAD))
                        .executes(context -> run(() -> reload(context.getSource().getSender()))))
                .then(Commands.literal("discord")
                        .requires(source -> source.getSender().hasPermission(PERM_DISCORD))
                        .executes(context -> run(() -> help(context.getSource().getSender())))
                        .then(Commands.literal("test")
                                .executes(context -> run(() -> discordTest(context.getSource().getSender())))))
                .then(Commands.argument("subcommand", StringArgumentType.greedyString())
                        .suggests((context, builder) -> builder.buildFuture())
                        .executes(context -> run(() -> help(context.getSource().getSender()))))
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> display(DisplayKind kind) {
        return Commands.literal(kind.key())
                .requires(source -> source.getSender().hasPermission(PlayerSampler.PERM_DISPLAY))
                .executes(context -> run(() -> toggle(context.getSource().getSender(), kind, null)))
                .then(Commands.literal("on").executes(context -> run(() -> toggle(context.getSource().getSender(), kind, true))))
                .then(Commands.literal("off").executes(context -> run(() -> toggle(context.getSource().getSender(), kind, false))));
    }

    private int run(Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            services.errors().report("a /tickradar command", error);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void region(CommandSender sender) {
        Settings settings = services.settings().current();
        String language = languageOf(sender, settings);
        if (!(sender instanceof Player player)) {
            Optional<RegionSnapshot> global = services.metrics().global();
            sender.sendMessage(global.map(snapshot -> regionMessage(settings, language, "command.global", snapshot))
                    .orElseGet(() -> message(settings, language, "command.not-measured")));
            return;
        }
        Optional<RegionSnapshot> snapshot = services.metrics().regionOf(player.getUniqueId());
        if (snapshot.isEmpty()) {
            sender.sendMessage(message(settings, language, "command.not-measured"));
        } else if (snapshot.get().unavailable()) {
            sender.sendMessage(message(settings, language, "command.unavailable"));
        } else {
            sender.sendMessage(regionMessage(settings, language, "command.region", snapshot.get()));
        }
    }

    private void toggle(CommandSender sender, DisplayKind kind, Boolean requested) {
        Settings settings = services.settings().current();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(settings, languageOf(sender, settings), "command.players-only"));
            return;
        }
        Optional<PlayerSampler> sampler = services.samplers().get(player.getUniqueId());
        if (sampler.isEmpty()) {
            sender.sendMessage(message(settings, languageOf(sender, settings), "command.not-measured"));
            return;
        }
        services.scheduler().runForPlayer(player, () -> {
            Settings current = services.settings().current();
            String language = languageOf(player, current);
            String key = switch (sampler.get().toggle(kind, requested)) {
                case SHOWN -> "command.display-on";
                case HIDDEN -> "command.display-off";
                case DISABLED -> "command.display-disabled";
            };
            Component name = services.renderer().text(current, language, "display.names." + kind.key());
            player.sendMessage(services.renderer().message(current, language, key, Placeholder.component("display", name)));
        });
    }

    private void alerts(CommandSender sender, Boolean requested) {
        Settings settings = services.settings().current();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(settings, languageOf(sender, settings), "command.players-only"));
            return;
        }
        Optional<PlayerSampler> sampler = services.samplers().get(player.getUniqueId());
        if (sampler.isEmpty()) {
            sender.sendMessage(message(settings, languageOf(sender, settings), "command.not-measured"));
            return;
        }
        services.scheduler().runForPlayer(player, () -> run(() -> {
            Settings current = services.settings().current();
            String key = sampler.get().toggleAlerts(requested) ? "command.alerts-on" : "command.alerts-off";
            player.sendMessage(message(current, languageOf(player, current), key));
        }));
    }

    private void regions(CommandSender sender, int requestedPage) {
        Settings settings = services.settings().current();
        String language = languageOf(sender, settings);
        RegionsPage page = RegionsPage.of(services.metrics().view(System.nanoTime()), requestedPage,
                settings.config().regionsPerPage());
        boolean canTeleport = sender.hasPermission(PERM_TELEPORT);
        for (Component line : regionList.lines(settings, language, page, canTeleport)) {
            sender.sendMessage(line);
        }
    }

    private void teleport(CommandSender sender, String requestedId) {
        Settings settings = services.settings().current();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message(settings, languageOf(sender, settings), "command.players-only"));
            return;
        }
        TeleportTarget target = TeleportTarget.resolve(requestedId, services.metrics()::region);
        services.scheduler().runForPlayer(player, () -> run(() -> teleportOnPlayerThread(player, target)));
    }

    private void teleportOnPlayerThread(Player player, TeleportTarget target) {
        Settings settings = services.settings().current();
        String language = languageOf(player, settings);
        TagResolver region = Placeholder.unparsed("region", target.id());
        Optional<Location> destination = target.outcome() == TeleportTarget.Outcome.FOUND
                ? destination(player, target) : Optional.empty();
        if (destination.isEmpty()) {
            String key = target.outcome() == TeleportTarget.Outcome.GLOBAL ? "command.tp-global" : "command.tp-unknown";
            player.sendMessage(message(settings, language, key, region));
            return;
        }
        player.teleportAsync(destination.get(), PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((moved, error) -> {
            if (error != null) {
                services.errors().report("/tickradar tp", error);
            }
            boolean success = error == null && Boolean.TRUE.equals(moved);
            services.scheduler().runForPlayer(player, () -> run(() -> {
                Settings current = services.settings().current();
                String key = success ? "command.tp-done" : "command.tp-failed";
                player.sendMessage(message(current, languageOf(player, current), key, region));
            }));
        });
    }

    private static Optional<Location> destination(Player player, TeleportTarget target) {
        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            return Optional.empty();
        }
        Location current = player.getLocation();
        BlockPosition position = target.position();
        return Optional.of(new Location(world, position.x() + BLOCK_CENTER, position.y(), position.z() + BLOCK_CENTER,
                current.getYaw(), current.getPitch()));
    }

    private void status(CommandSender sender) {
        Settings settings = services.settings().current();
        ConfigSnapshot config = settings.config();
        StatusReport report = new StatusReport(
                services.regionTps(),
                config.sampling().intervalTicks(),
                config.sampling().historySeconds(),
                config.regionTpsIntervalSeconds(),
                services.costs().sample().summary(),
                services.costs().playerSample().summary(),
                services.costs().display().summary(),
                services.costs().measure().summary(),
                services.costs().msptCall().summary(),
                services.costs().tpsCall().summary(),
                services.costs().global().summary(),
                services.costs().tpsThread().summary(),
                services.costs().globalTps().summary(),
                services.metrics().regionCount(),
                services.online().size(),
                services.syntheticAnchors().getAsInt(),
                services.discord().status().summary(),
                services.metrics().activeAlerts(),
                services.renderer().displayFallbackTemplates(),
                services.costs().playerTickPeak().recent(System.nanoTime()),
                services.costs().playerTickPeak().highest());
        sender.sendMessage(message(settings, languageOf(sender, settings), "command.status-header",
                Placeholder.unparsed("version", services.version())));
        for (String line : report.lines()) {
            sender.sendMessage(Component.text(line));
        }
    }

    private void reload(CommandSender sender) {
        Settings settings = services.settings().current();
        String language = languageOf(sender, settings);
        Reply reply = Reply.to(sender, services.console(), services.scheduler(), message(settings, language, "command.reload-rcon"));
        services.scheduler().runAsync(() -> {
            if (services.stopping().getAsBoolean()) {
                return;
            }
            try {
                Component result = reloadNow(language);
                if (!services.stopping().getAsBoolean()) {
                    reply.send(result);
                }
            } catch (Throwable error) {
                services.errors().report("/tickradar reload", error);
            }
        });
    }

    private void discordTest(CommandSender sender) {
        Settings settings = services.settings().current();
        String language = languageOf(sender, settings);
        Reply reply = Reply.to(sender, services.console(), services.scheduler(),
                message(settings, language, "command.discord-test-rcon"));
        CompletableFuture<DiscordTestResult> test = services.discord().sendTest();
        if (!test.isDone() && !(sender instanceof RemoteConsoleCommandSender)) {
            sender.sendMessage(message(settings, language, "command.discord-test-sending"));
        }
        test.whenComplete((result, error) -> {
            if (services.stopping().getAsBoolean()) {
                return;
            }
            run(() -> reply.send(error == null
                    ? discordTestMessage(services.settings().current(), language, result)
                    : message(services.settings().current(), language, "command.discord-test-failed",
                    Placeholder.unparsed("error", error.getClass().getSimpleName()))));
        });
    }

    private Component discordTestMessage(Settings settings, String language, DiscordTestResult result) {
        return message(settings, language, discordTestKey(result),
                Placeholder.unparsed("http", Integer.toString(result.httpStatus())),
                Placeholder.unparsed("wait", ValueFormat.oneDecimal(result.rateLimitWait().toNanos() / NANOS_PER_SECOND,
                        services.renderer().decimalSeparator(settings, language))),
                Placeholder.unparsed("error", result.detail()));
    }

    static String discordTestKey(DiscordTestResult result) {
        boolean withStatus = result.httpStatus() != DiscordTestResult.NO_STATUS;
        return switch (result.outcome()) {
            case SENT -> result.rateLimitWait().toMillis() >= SHOWN_RATE_LIMIT_WAIT_MILLIS
                    ? "command.discord-test-sent-waited" : "command.discord-test-sent";
            case NOT_CONFIGURED -> "command.discord-test-not-configured";
            case INVALID_URL -> "command.discord-test-invalid-url";
            case REJECTED, DISABLED -> withStatus ? "command.discord-test-rejected" : "command.discord-test-not-configured";
            case FAILED -> "command.discord-test-failed";
            case DROPPED -> "command.discord-test-dropped";
            case STOPPED -> "command.discord-test-stopped";
        };
    }

    private Component reloadNow(String language) {
        LoadResult<Settings> result = services.settings().reload();
        Settings current = services.settings().current();
        return switch (result) {
            case LoadResult.Loaded<Settings>(Settings loaded, List<String> warnings) -> {
                services.afterReload().run();
                yield warnings.isEmpty()
                        ? message(current, language, "command.reload-done")
                        : message(current, language, "command.reload-done-warnings",
                        Placeholder.unparsed("warnings", Integer.toString(warnings.size())));
            }
            case LoadResult.Unreadable<Settings>(String error) ->
                    message(current, language, "command.reload-failed", Placeholder.unparsed("error", error));
        };
    }

    private void help(CommandSender sender) {
        Settings settings = services.settings().current();
        String language = languageOf(sender, settings);
        List<String> keys = new ArrayList<>();
        keys.add("command.help.region");
        if (sender.hasPermission(PlayerSampler.PERM_DISPLAY)) {
            keys.add("command.help.display");
        }
        if (sender.hasPermission(PERM_REGIONS)) {
            keys.add("command.help.regions");
        }
        if (sender.hasPermission(PERM_TELEPORT)) {
            keys.add("command.help.tp");
        }
        if (sender.hasPermission(PlayerSampler.PERM_ALERTS)) {
            keys.add("command.help.alerts");
        }
        if (sender.hasPermission(PERM_STATUS)) {
            keys.add("command.help.status");
        }
        if (sender.hasPermission(PERM_RELOAD)) {
            keys.add("command.help.reload");
        }
        if (sender.hasPermission(PERM_DISCORD)) {
            keys.add("command.help.discord");
        }
        sender.sendMessage(message(settings, language, "command.help.header"));
        for (String key : keys) {
            sender.sendMessage(services.renderer().text(settings, language, key));
        }
    }

    private Component regionMessage(Settings settings, String language, String key, RegionSnapshot snapshot) {
        return services.renderer().message(settings, language, key, services.renderer().region(settings, language, snapshot));
    }

    private Component message(Settings settings, String language, String key, TagResolver... resolvers) {
        return services.renderer().message(settings, language, key, resolvers);
    }

    private static String languageOf(CommandSender sender, Settings settings) {
        ConfigSnapshot config = settings.config();
        if (!config.isAutoLanguage()) {
            return settings.lang().resolve(config.language());
        }
        return sender instanceof Player player ? settings.lang().resolve(player.locale().getLanguage()) : settings.lang().resolve(null);
    }
}
