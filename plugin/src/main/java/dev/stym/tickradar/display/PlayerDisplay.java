package dev.stym.tickradar.display;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import dev.stym.tickradar.player.DisplayPrefs;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class PlayerDisplay {

    private static final float FULL_TICK_MS = 50f;
    private static final float PROGRESS_STEPS = 100f;
    private static final long TICK_NANOS = 50_000_000L;
    private static final long ACTION_BAR_OPAQUE_NANOS = 40 * TICK_NANOS;

    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private final RenderedText bossBarName;
    private final RenderedText actionBar;
    private final RenderedText footer;
    private final RoundedValues rounded = new RoundedValues();
    private boolean bossBarShown;
    private boolean footerShown;
    private boolean actionBarShown;
    private long actionBarSentNanos;
    private long actionBarUpdateNanos;
    private HealthStatus actionBarStatus;
    private RegionSnapshot valuesSnapshot;
    private long valuesGeneration;
    private DisplayValues values;

    public PlayerDisplay(Renderer renderer) {
        this(renderer::displayTemplate);
    }

    PlayerDisplay(DisplayRenderer renderer) {
        this.bossBarName = new RenderedText(renderer);
        this.actionBar = new RenderedText(renderer);
        this.footer = new RenderedText(renderer);
    }

    public void update(Audience audience, Settings settings, String language, boolean canDisplay, DisplayPrefs prefs,
                       RegionSnapshot snapshot, long nowNanos) {
        ConfigSnapshot config = settings.config();
        boolean hasSnapshot = snapshot != null;
        boolean showBossBar = hasSnapshot && isShown(config, canDisplay, prefs, DisplayKind.BOSSBAR);
        boolean showActionBar = hasSnapshot && isShown(config, canDisplay, prefs, DisplayKind.ACTIONBAR);
        boolean showFooter = hasSnapshot && isShown(config, canDisplay, prefs, DisplayKind.TAB);
        DisplayValues current = showBossBar || showActionBar || showFooter ? values(settings, snapshot) : null;
        updateBossBar(audience, showBossBar, settings, language, snapshot, current);
        updateActionBar(audience, showActionBar, settings, language, snapshot, current, nowNanos);
        updateFooter(audience, showFooter, settings, language, snapshot, current);
    }

    public static boolean isShown(ConfigSnapshot config, boolean canDisplay, DisplayPrefs prefs, DisplayKind kind) {
        ConfigSnapshot.DisplaySettings display = config.displays().of(kind);
        return canDisplay && display.enabled() && prefs.isShown(kind, display.defaultOn());
    }

    BossBar bossBar() {
        return bossBar;
    }

    private DisplayValues values(Settings settings, RegionSnapshot snapshot) {
        if (snapshot != valuesSnapshot || settings.generation() != valuesGeneration) {
            boolean changed = rounded.changeTo(settings, snapshot);
            if (changed || values == null || settings.generation() != valuesGeneration) {
                values = DisplayValues.of(settings, snapshot);
            }
            valuesSnapshot = snapshot;
            valuesGeneration = settings.generation();
        }
        return values;
    }

    private void updateBossBar(Audience audience, boolean show, Settings settings, String language, RegionSnapshot snapshot,
                               DisplayValues current) {
        if (!show) {
            if (bossBarShown) {
                audience.hideBossBar(bossBar);
                bossBarShown = false;
            }
            return;
        }
        if (bossBarName.refresh(settings, language, template(snapshot, DisplayTemplates.BOSSBAR), current)) {
            bossBar.name(bossBarName.component());
        }
        float progress = progress(snapshot);
        if (progress != bossBar.progress()) {
            bossBar.progress(progress);
        }
        BossBar.Color color = color(current.status());
        if (color != bossBar.color()) {
            bossBar.color(color);
        }
        if (!bossBarShown) {
            audience.showBossBar(bossBar);
            bossBarShown = true;
        }
    }

    private void updateActionBar(Audience audience, boolean show, Settings settings, String language, RegionSnapshot snapshot,
                                 DisplayValues current, long nowNanos) {
        if (!show) {
            actionBarShown = false;
            return;
        }
        boolean due = !actionBarShown || current.status() != actionBarStatus || actionBarDue(settings, nowNanos);
        actionBarUpdateNanos = nowNanos;
        if (!due) {
            return;
        }
        actionBar.refresh(settings, language, template(snapshot, DisplayTemplates.ACTIONBAR), current);
        audience.sendActionBar(actionBar.component());
        actionBarShown = true;
        actionBarSentNanos = nowNanos;
        actionBarStatus = current.status();
    }

    private boolean actionBarDue(Settings settings, long nowNanos) {
        long nominalInterval = settings.config().sampling().intervalTicks() * TICK_NANOS;
        long spacing = Math.max(nominalInterval, nowNanos - actionBarUpdateNanos);
        return nowNanos - actionBarSentNanos + spacing / 2 >= ACTION_BAR_OPAQUE_NANOS;
    }

    private void updateFooter(Audience audience, boolean show, Settings settings, String language, RegionSnapshot snapshot,
                              DisplayValues current) {
        if (!show) {
            if (footerShown) {
                audience.sendPlayerListFooter(Component.empty());
                footerShown = false;
            }
            return;
        }
        boolean changed = footer.refresh(settings, language, template(snapshot, DisplayTemplates.TAB_FOOTER), current);
        if (changed || !footerShown) {
            audience.sendPlayerListFooter(footer.component());
            footerShown = true;
        }
    }

    private static String template(RegionSnapshot snapshot, String template) {
        return snapshot.unavailable() ? DisplayTemplates.UNAVAILABLE : template;
    }

    static float progress(RegionSnapshot snapshot) {
        if (snapshot.unavailable() || !ValueFormat.isUsable(snapshot.mspt5s())) {
            return 0f;
        }
        float exact = Math.clamp((float) snapshot.mspt5s() / FULL_TICK_MS, 0f, 1f);
        return Math.round(exact * PROGRESS_STEPS) / PROGRESS_STEPS;
    }

    private static BossBar.Color color(HealthStatus status) {
        return switch (status) {
            case OK -> BossBar.Color.GREEN;
            case WARNING -> BossBar.Color.YELLOW;
            case CRITICAL -> BossBar.Color.RED;
            case UNAVAILABLE -> BossBar.Color.WHITE;
        };
    }
}
