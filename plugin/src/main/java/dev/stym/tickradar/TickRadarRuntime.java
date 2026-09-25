package dev.stym.tickradar;

import dev.stym.tickradar.alert.AlertDispatcher;
import dev.stym.tickradar.alert.discord.DiscordNotifier;
import dev.stym.tickradar.alert.discord.DiscordSettings;
import dev.stym.tickradar.command.CommandServices;
import dev.stym.tickradar.command.TickRadarCommand;
import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertChannel;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.config.SettingsService;
import dev.stym.tickradar.display.DisplayWarmUp;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.SampleSlots;
import dev.stym.tickradar.metrics.BStatsBootstrap;
import dev.stym.tickradar.papi.PlaceholderValues;
import dev.stym.tickradar.papi.TickRadarExpansion;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.sample.ErrorReporter;
import dev.stym.tickradar.sample.FoliaRegionTps;
import dev.stym.tickradar.sample.GlobalSampler;
import dev.stym.tickradar.sample.MetricsCache;
import dev.stym.tickradar.sample.PlayerConnectionListener;
import dev.stym.tickradar.sample.PlayerSamplers;
import dev.stym.tickradar.sample.RegionMeter;
import dev.stym.tickradar.sample.RegionTpsPoller;
import dev.stym.tickradar.sample.RegionTracker;
import dev.stym.tickradar.sample.SamplingContext;
import dev.stym.tickradar.sample.SamplingCosts;
import dev.stym.tickradar.sample.SamplingWarmUp;
import dev.stym.tickradar.sample.SyntheticAnchors;
import dev.stym.tickradar.schedule.FoliaTaskScheduler;
import dev.stym.tickradar.schedule.TaskScheduler;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Duration;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

final class TickRadarRuntime {

    private static final String PREFS_KEY = "prefs";
    private static final String TAB_PLUGIN = "TAB";
    private static final Runnable NOTHING = () -> {
    };

    private final TickRadarPlugin plugin;
    private final Logger logger;
    private final TaskScheduler scheduler;
    private final SettingsService settings;
    private final PlayerSamplers samplers;
    private final GlobalSampler globalSampler;
    private final RegionTpsPoller regionTpsPoller;
    private final SyntheticAnchors syntheticAnchors;
    private final Renderer renderer;
    private final ErrorReporter errors;
    private final PlaceholderValues placeholders;
    private final AlertDispatcher alerts;
    private final DiscordNotifier discord;
    private final Object discordSettingsLock = new Object();
    private final boolean regionTps;
    private long appliedDiscordGeneration = Long.MIN_VALUE;
    private volatile boolean stopping;
    private volatile Runnable unregisterPlaceholders = NOTHING;
    private volatile Runnable stopMetrics = NOTHING;

    private TickRadarRuntime(TickRadarPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scheduler = new FoliaTaskScheduler(plugin);
        this.settings = new SettingsService(new ConfigFiles(plugin.getDataFolder().toPath(), plugin::getResource), logger);
        this.regionTps = RegionMeter.detectRegionTps();
        settings.loadAtStartup();

        this.errors = new ErrorReporter(logger);
        SamplingCosts costs = SamplingCosts.create();
        AnchorTracker anchors = new AnchorTracker();
        Anchor global = Anchor.global(System.nanoTime());
        RegionMeter meter = new RegionMeter(plugin.getServer(), regionTps, costs);
        FoliaRegionTps regionTpsSource = new FoliaRegionTps(plugin.getServer());
        OnlinePlayers online = new OnlinePlayers();
        this.renderer = new Renderer();
        this.discord = new DiscordNotifier(logger);
        this.alerts = new AlertDispatcher(settings::current, online, scheduler, renderer, logger, errors, () -> stopping);
        RegionTracker regions = new RegionTracker(anchors, meter, regionTpsSource, settings::current, errors, costs, alerts);
        SamplingContext context = new SamplingContext(scheduler, settings::current, () -> stopping, regions, online, costs, errors,
                alerts, renderer, new NamespacedKey(plugin, PREFS_KEY), new SampleSlots());
        MetricsCache metrics = new MetricsCache(anchors, global);
        this.placeholders = new PlaceholderValues(settings::current, online, metrics, System::nanoTime);

        this.samplers = new PlayerSamplers(context);
        this.globalSampler = new GlobalSampler(context, meter, anchors, global);
        this.regionTpsPoller = new RegionTpsPoller(anchors, regionTpsSource, costs, errors,
                () -> Duration.ofSeconds(settings.current().config().regionTpsIntervalSeconds()));
        this.syntheticAnchors = new SyntheticAnchors(context, plugin.getServer(), logger);

        TickRadarCommand command = new TickRadarCommand(new CommandServices(
                plugin.getPluginMeta().getVersion(), plugin.getServer().getConsoleSender(), settings, renderer, scheduler, samplers,
                online, metrics, costs, errors, discord, regionTps, syntheticAnchors::size, () -> stopping,
                this::afterReload));
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> command.register(event.registrar()));
    }

    static TickRadarRuntime start(TickRadarPlugin plugin) {
        TickRadarRuntime runtime = new TickRadarRuntime(plugin);
        StartupRollback.startOrRollBack(runtime::startServices, runtime::stop);
        return runtime;
    }

    void stop() {
        stopping = true;
        runQuietly("the removal of the PlaceholderAPI expansion", unregisterPlaceholders);
        runQuietly("the shutdown of bStats", stopMetrics);
        globalSampler.stop();
        regionTpsPoller.stop();
        syntheticAnchors.stop();
        samplers.cancelAll();
        scheduler.cancelAll();
        alerts.unregister(discord);
        runQuietly("the shutdown of the Discord alerts", discord::stop);
    }

    private void startServices() {
        if (!regionTps) {
            logger.warning("Server#getRegionTPS is missing on this server: the 1-minute TPS of each region, read once a minute, "
                    + "is shown instead of the 5-second TPS.");
        }
        plugin.getServer().getPluginManager().registerEvents(new PlayerConnectionListener(samplers), plugin);
        prepareAndWarmUp();
        globalSampler.start();
        if (regionTps) {
            regionTpsPoller.start();
        }
        scheduler.runGlobal(syntheticAnchors::restart);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            samplers.start(player);
        }
        startIntegrations();
        alerts.register(AlertChannel.DISCORD, discord);
        applyDiscordSettingsAsync();
        Settings current = settings.current();
        warnAboutTabPlugin(current);
        logger.info("Ready: measurement=region, regionTPS=" + (regionTps ? "yes" : "no")
                + ", interval=" + current.config().sampling().intervalTicks());
    }

    private void startIntegrations() {
        if (plugin.getServer().getPluginManager().isPluginEnabled(TickRadarExpansion.PLACEHOLDER_API)) {
            unregisterPlaceholders = TickRadarExpansion.register(plugin.getPluginMeta().getVersion(), placeholders);
        }
        if (settings.current().config().metrics()) {
            stopMetrics = BStatsBootstrap.start(plugin, settings::current);
        }
    }

    private void runQuietly(String task, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            errors.report(task, error);
        }
    }

    private void afterReload() {
        scheduler.runGlobal(syntheticAnchors::restart);
        if (regionTps && !stopping) {
            regionTpsPoller.restart();
        }
        prepareAndWarmUp();
        applyDiscordSettingsAsync();
    }

    private void applyDiscordSettingsAsync() {
        scheduler.runAsync(() -> runQuietly("the configuration of the Discord alerts", this::applyDiscordSettings));
    }

    private void applyDiscordSettings() {
        synchronized (discordSettingsLock) {
            Settings current = settings.current();
            if (stopping || current.generation() <= appliedDiscordGeneration) {
                return;
            }
            ConfigSnapshot config = current.config();
            discord.apply(DiscordSettings.from(config.discord(), config.isAutoLanguage() ? null : config.language()));
            appliedDiscordGeneration = current.generation();
        }
    }

    private void prepareAndWarmUp() {
        scheduler.runAsync(() -> {
            Settings current = settings.current();
            runUnlessStopping("the preparation of the display templates", () -> renderer.prepareDisplays(current));
            runUnlessStopping("the warm-up of the displays", () -> DisplayWarmUp.run(renderer, current));
            runUnlessStopping("the warm-up of the sampling", () -> SamplingWarmUp.run(current.config()));
        });
    }

    private void runUnlessStopping(String task, Runnable action) {
        if (!stopping) {
            runQuietly(task, action);
        }
    }

    private void warnAboutTabPlugin(Settings current) {
        if (current.config().displays().tab().enabled() && plugin.getServer().getPluginManager().getPlugin(TAB_PLUGIN) != null) {
            logger.warning("The TAB plugin is installed: set display.tab.enabled to false in config.yml and use the "
                    + "%tickradar_...% placeholders in TAB instead, or both plugins will overwrite the tab footer.");
        }
    }
}
