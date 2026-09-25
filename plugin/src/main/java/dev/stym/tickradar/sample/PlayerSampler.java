package dev.stym.tickradar.sample;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.PlayerDisplay;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.SampleSlots;
import dev.stym.tickradar.player.DisplayPrefs;
import dev.stym.tickradar.player.PermissionCache;
import dev.stym.tickradar.player.Permissions;
import dev.stym.tickradar.player.PlayerState;
import dev.stym.tickradar.schedule.TaskScheduler;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlayerSampler {

    public static final String PERM_DISPLAY = Permissions.DISPLAY;
    public static final String PERM_ALERTS = Permissions.ALERTS;
    public static final String PERM_TELEPORT = Permissions.TELEPORT;

    public enum ToggleResult {
        SHOWN,
        HIDDEN,
        DISABLED
    }

    private final SamplingContext context;
    private final Player player;
    private final UUID id;
    private final PlayerDisplay display;
    private final PermissionCache permissions;
    private final Consumer<PlayerSampler> onRetired;
    private final Location location = new Location(null, 0, 0, 0);
    private volatile TaskScheduler.Task task;
    private volatile SampleSlots.Lease lease;
    private DisplayPrefs prefs;
    private Anchor anchor;
    private BlockPosition position;
    private PlayerState published;
    private Locale languageLocale;
    private long languageGeneration;
    private String language;

    PlayerSampler(SamplingContext context, Player player, DisplayPrefs prefs, Consumer<PlayerSampler> onRetired) {
        this.context = context;
        this.player = player;
        this.id = player.getUniqueId();
        this.display = new PlayerDisplay(context.renderer());
        this.permissions = new PermissionCache(player::hasPermission);
        this.prefs = prefs;
        this.onRetired = onRetired;
    }

    boolean start(int intervalTicks) {
        return schedule(context.slots().acquire(intervalTicks), context.scheduler().currentRegionTick());
    }

    void cancel() {
        TaskScheduler.Task scheduled = task;
        if (scheduled != null) {
            scheduled.cancel();
        }
        releaseSlot();
    }

    public ToggleResult toggle(DisplayKind kind, Boolean requested) {
        Settings settings = context.settings().get();
        ConfigSnapshot config = settings.config();
        ConfigSnapshot.DisplaySettings displaySettings = config.displays().of(kind);
        if (!displaySettings.enabled()) {
            return ToggleResult.DISABLED;
        }
        boolean shown = requested != null ? requested : !prefs.isShown(kind, displaySettings.defaultOn());
        prefs = prefs.with(kind, shown);
        prefs.write(player, context.prefsKey());
        long now = System.nanoTime();
        Permissions current = permissions.refresh();
        republish(current);
        display.update(player, settings, languageOf(settings), current.display(), prefs,
                anchor == null ? null : anchor.snapshot(), now);
        return shown ? ToggleResult.SHOWN : ToggleResult.HIDDEN;
    }

    public boolean toggleAlerts(Boolean requested) {
        boolean on = requested != null ? requested : !prefs.alertsOn();
        prefs = prefs.withAlerts(on);
        prefs.write(player, context.prefsKey());
        republish(permissions.refresh());
        return on;
    }

    private void republish(Permissions current) {
        PlayerState known = context.online().get(id).orElse(null);
        if (known == null) {
            return;
        }
        published = new PlayerState(id, player, known.anchorId(), known.language(), current.display(), current.alerts(),
                current.teleport(), prefs);
        context.online().publish(published);
    }

    private boolean schedule(SampleSlots.Lease slot, long regionTick) {
        lease = slot;
        long delay = slot.firstDelay(regionTick == TaskScheduler.NO_TICK ? 0 : regionTick);
        return scheduleTask(slot, delay);
    }

    private boolean scheduleTask(SampleSlots.Lease slot, long delayTicks) {
        task = context.scheduler().runForPlayerAtFixedRate(player, this::tick, this::retire, delayTicks, slot.interval());
        if (task == null) {
            slot.release();
            return false;
        }
        return true;
    }

    private void retire() {
        releaseSlot();
        onRetired.accept(this);
    }

    private void releaseSlot() {
        SampleSlots.Lease current = lease;
        if (current != null) {
            current.release();
        }
    }

    private void tick(TaskScheduler.Task running) {
        if (context.isStopping()) {
            running.cancel();
            return;
        }
        long start = System.nanoTime();
        try {
            Settings settings = context.settings().get();
            int configuredInterval = settings.config().sampling().intervalTicks();
            SampleSlots.Lease slot = lease;
            long regionTick = context.scheduler().currentRegionTick();
            if (configuredInterval != slot.interval()) {
                changeInterval(running, slot, configuredInterval, regionTick);
                return;
            }
            sample(settings, start, regionTick);
            realignIfDrifted(running, slot, regionTick);
        } catch (Throwable error) {
            context.errors().report("the sampling of a player", error);
        } finally {
            long duration = System.nanoTime() - start;
            context.costs().sample().record(duration);
            context.costs().playerSample().record(duration);
        }
    }

    private void changeInterval(TaskScheduler.Task running, SampleSlots.Lease slot, int configuredInterval, long regionTick) {
        running.cancel();
        slot.release();
        if (!schedule(context.slots().acquire(configuredInterval), regionTick)) {
            onRetired.accept(this);
        }
    }

    private void realignIfDrifted(TaskScheduler.Task running, SampleSlots.Lease slot, long regionTick) {
        if (regionTick == TaskScheduler.NO_TICK) {
            return;
        }
        long delay = slot.realignDelay(regionTick);
        if (delay == 0) {
            return;
        }
        running.cancel();
        if (!scheduleTask(slot, delay)) {
            onRetired.accept(this);
        }
    }

    private void sample(Settings settings, long nowNanos, long regionTick) {
        player.getLocation(location);
        Permissions current = permissions.next();
        String playerLanguage = languageOf(settings);
        anchor = context.regions().observe(id, true, location.getWorld(), positionOf(location), nowNanos);
        if (regionTick != TaskScheduler.NO_TICK) {
            context.costs().playerTickPeak().record(anchor.countSampleOnTick(regionTick), nowNanos);
        }
        publish(playerLanguage, current);
        long displayStart = System.nanoTime();
        display.update(player, settings, playerLanguage, current.display(), prefs, anchor.snapshot(), displayStart);
        context.costs().display().record(System.nanoTime() - displayStart);
    }

    private BlockPosition positionOf(Location at) {
        BlockPosition last = position;
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        if (last == null || last.x() != x || last.y() != y || last.z() != z) {
            position = new BlockPosition(x, y, z);
        }
        return position;
    }

    private void publish(String playerLanguage, Permissions current) {
        PlayerState last = published;
        if (last != null && context.online().isCurrent(last) && last.prefs() == prefs && last.anchorId().equals(anchor.id())
                && last.language().equals(playerLanguage) && last.canDisplay() == current.display()
                && last.canReceiveAlerts() == current.alerts() && last.canTeleport() == current.teleport()) {
            return;
        }
        published = new PlayerState(id, player, anchor.id(), playerLanguage, current.display(), current.alerts(),
                current.teleport(), prefs);
        context.online().publish(published);
    }

    private String languageOf(Settings settings) {
        ConfigSnapshot config = settings.config();
        Locale locale = config.isAutoLanguage() ? player.locale() : null;
        if (language == null || !Objects.equals(locale, languageLocale) || settings.generation() != languageGeneration) {
            language = settings.lang().resolve(locale == null ? config.language() : locale.getLanguage());
            languageLocale = locale;
            languageGeneration = settings.generation();
        }
        return language;
    }
}
