package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import dev.stym.tickradar.player.DisplayPrefs;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerDisplayTest {

    private static final long SECOND = 1_000_000_000L;
    private static final DisplayPrefs EVERY_DISPLAY = DisplayPrefs.NONE
            .with(DisplayKind.BOSSBAR, true)
            .with(DisplayKind.ACTIONBAR, true)
            .with(DisplayKind.TAB, true);
    private static final DisplayPrefs BOSSBAR_ONLY = DisplayPrefs.NONE.with(DisplayKind.BOSSBAR, true);
    private static final DisplayPrefs ACTIONBAR_ONLY = DisplayPrefs.NONE.with(DisplayKind.ACTIONBAR, true);
    private static final DisplayPrefs FOOTER_ONLY = DisplayPrefs.NONE.with(DisplayKind.TAB, true);

    private final Renderer renderer = new Renderer();
    private final Settings settings = settings(1);
    private final AtomicInteger lookups = new AtomicInteger();
    private final AtomicInteger renders = new AtomicInteger();
    private final List<String> templates = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private final List<String> bossBarChanges = new ArrayList<>();
    private final Player player = recordingPlayer(sent);
    private final PlayerDisplay display = new PlayerDisplay((settings, language, template, status, onePlayer) -> {
        lookups.incrementAndGet();
        templates.add(template);
        Function<DisplayValues, Component> compiled = renderer.displayTemplate(settings, language, template, status, onePlayer);
        return values -> {
            renders.incrementAndGet();
            return compiled.apply(values);
        };
    });
    private long now = 7 * SECOND;

    @BeforeEach
    void listenToTheBossBar() {
        display.bossBar().addListener(new BossBar.Listener() {
            @Override
            public void bossBarNameChanged(BossBar bar, Component oldName, Component newName) {
                bossBarChanges.add("name");
            }

            @Override
            public void bossBarProgressChanged(BossBar bar, float oldProgress, float newProgress) {
                bossBarChanges.add("progress");
            }

            @Override
            public void bossBarColorChanged(BossBar bar, BossBar.Color oldColor, BossBar.Color newColor) {
                bossBarChanges.add("color");
            }
        });
    }

    @Test
    void theFirstSampleRendersAndSendsTheThreeDisplays() {
        update(EVERY_DISPLAY, snapshot(23.4));
        assertEquals(3, renders.get());
        assertEquals(List.of("showBossBar", "sendActionBar", "sendPlayerListFooter"), sent);
        assertEquals(List.of("name", "progress"), bossBarChanges);
    }

    @Test
    void anUnchangedTextIsNeitherRenderedNorSentAgain() {
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(23.4));
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(23.4));
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(23.41));
        assertEquals(2, renders.get());
        assertEquals(List.of("showBossBar", "sendPlayerListFooter"), sent);
        assertEquals(List.of("name", "progress"), bossBarChanges);
    }

    @Test
    void theTemplateIsLookedUpOnlyWhenItsKeyChanges() {
        update(EVERY_DISPLAY, snapshot(23.4));
        update(EVERY_DISPLAY, snapshot(31.2));
        update(EVERY_DISPLAY, snapshot(12.5));
        assertEquals(3, lookups.get());
        update(EVERY_DISPLAY, snapshot(45));
        assertEquals(6, lookups.get());
    }

    @Test
    void aChangedTextIsRenderedAndSentOnce() {
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(23.4));
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(31.2));
        assertEquals(4, renders.get());
        assertEquals(List.of("showBossBar", "sendPlayerListFooter", "sendPlayerListFooter"), sent);
        assertEquals(List.of("name", "progress", "name", "progress"), bossBarChanges);
    }

    @Test
    void aValueAbsentFromTheTemplatesChangesNothingOnScreen() {
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(23.4, 20, 39));
        update(BOSSBAR_ONLY.with(DisplayKind.TAB, true), snapshot(23.4, 20, 44));
        assertEquals(4, renders.get());
        assertEquals(List.of("showBossBar", "sendPlayerListFooter"), sent);
        assertEquals(List.of("name", "progress"), bossBarChanges);
    }

    @Test
    void theActionBarIsResentEveryTwoSecondsBeforeItFades() {
        for (int second = 0; second < 5; second++) {
            update(ACTIONBAR_ONLY, snapshot(20 + second));
        }
        assertEquals(List.of("sendActionBar", "sendActionBar", "sendActionBar"), sent);
        assertEquals(3, renders.get());
    }

    @Test
    void aSlowIntervalResendsTheActionBarAtEverySample() {
        for (int sample = 0; sample < 3; sample++) {
            display.update(player, settings, "en", true, ACTIONBAR_ONLY, snapshot(23.4), now);
            now += 2 * SECOND;
        }
        assertEquals(List.of("sendActionBar", "sendActionBar", "sendActionBar"), sent);
    }

    @Test
    void aLaggingServerResendsTheActionBarSooner() {
        for (int sample = 0; sample < 3; sample++) {
            display.update(player, settings, "en", true, ACTIONBAR_ONLY, snapshot(23.4), now);
            now += 1_400_000_000L;
        }
        assertEquals(List.of("sendActionBar", "sendActionBar", "sendActionBar"), sent);
    }

    @Test
    void aStatusChangeSendsTheActionBarAtOnce() {
        update(ACTIONBAR_ONLY, snapshot(23.4));
        update(ACTIONBAR_ONLY, snapshot(45));
        assertEquals(List.of("sendActionBar", "sendActionBar"), sent);
        assertEquals(2, renders.get());
    }

    @Test
    void aFrenchStatusChangeSendsTheActionBarAtOnceWithADecimalComma() {
        List<Component> actionBars = new ArrayList<>();
        Player frenchPlayer = recordingPlayer(new ArrayList<>(), actionBars);
        display.update(frenchPlayer, settings, "fr", true, ACTIONBAR_ONLY, snapshot(23.4), now);
        now += SECOND;
        display.update(frenchPlayer, settings, "fr", true, ACTIONBAR_ONLY, snapshot(45), now);
        assertEquals(2, actionBars.size());
        assertEquals("19,8 TPS · 45,0 ms (max 41,0 ms)", plain(actionBars.get(1)));
    }

    @Test
    void theActionBarKeepsItsCadenceAfterAStatusChange() {
        update(ACTIONBAR_ONLY, snapshot(23.4));
        update(ACTIONBAR_ONLY, snapshot(45));
        update(ACTIONBAR_ONLY, snapshot(46));
        update(ACTIONBAR_ONLY, snapshot(47));
        assertEquals(List.of("sendActionBar", "sendActionBar", "sendActionBar"), sent);
    }

    @Test
    void aStatusChangeBackToOkSendsTheActionBarAtOnce() {
        update(ACTIONBAR_ONLY, snapshot(45));
        update(ACTIONBAR_ONLY, snapshot(23.4));
        assertEquals(List.of("sendActionBar", "sendActionBar"), sent);
    }

    @Test
    void theSameStatusIsNotSentBeforeTheCadence() {
        for (int sample = 0; sample < 4; sample++) {
            display.update(player, settings, "en", true, ACTIONBAR_ONLY, snapshot(20 + sample), now);
            now += 400_000_000L;
        }
        assertEquals(List.of("sendActionBar"), sent);
        assertEquals(1, renders.get());
    }

    @Test
    void anActionBarShownAgainIsSentAtOnce() {
        update(ACTIONBAR_ONLY, snapshot(23.4));
        update(DisplayPrefs.NONE, snapshot(23.4));
        update(ACTIONBAR_ONLY, snapshot(23.4));
        assertEquals(List.of("sendActionBar", "sendActionBar"), sent);
        assertEquals(1, renders.get());
    }

    @Test
    void theProgressMovesByWholeSteps() {
        assertEquals(0.47f, PlayerDisplay.progress(snapshot(23.4)));
        assertEquals(0.47f, PlayerDisplay.progress(snapshot(23.6)));
        assertEquals(1f, PlayerDisplay.progress(snapshot(80)));
    }

    @Test
    void aProgressInsideTheSameStepIsNotSentAgain() {
        update(BOSSBAR_ONLY, snapshot(23.4));
        update(BOSSBAR_ONLY, snapshot(23.6));
        assertEquals(List.of("name", "progress", "name"), bossBarChanges);
    }

    @Test
    void theColorFollowsTheStatus() {
        update(BOSSBAR_ONLY, snapshot(23.4));
        update(BOSSBAR_ONLY, snapshot(45));
        assertEquals(BossBar.Color.YELLOW, display.bossBar().color());
        assertEquals(List.of("name", "progress", "name", "progress", "color"), bossBarChanges);
    }

    @Test
    void theSameRoundedMsptWithAnotherStatusIsRenderedAgain() {
        update(EVERY_DISPLAY, snapshot(39.96));
        update(EVERY_DISPLAY, snapshot(40.0));
        assertEquals(6, renders.get());
        assertEquals(6, lookups.get());
        assertEquals(List.of("showBossBar", "sendActionBar", "sendPlayerListFooter", "sendActionBar", "sendPlayerListFooter"), sent);
        assertEquals(BossBar.Color.YELLOW, display.bossBar().color());
        assertEquals(List.of("name", "progress", "color"), bossBarChanges);
    }

    @Test
    void aReloadedThresholdChangesTheStatusOfTheSameSnapshot() {
        RegionSnapshot same = snapshot(30.0);
        display.update(player, settings, "en", true, FOOTER_ONLY, same, now);
        display.update(player, thresholds(2, 20.0, 45.0), "en", true, FOOTER_ONLY, same, now + SECOND);
        assertEquals(2, renders.get());
        assertEquals(List.of("sendPlayerListFooter", "sendPlayerListFooter"), sent);
    }

    @Test
    void anotherLanguageIsRenderedAgain() {
        update(EVERY_DISPLAY, snapshot(23.4));
        display.update(player, settings, "fr", true, EVERY_DISPLAY, snapshot(23.4), now);
        assertEquals(5, renders.get());
        assertEquals(List.of("name", "progress", "name"), bossBarChanges);
    }

    @Test
    void aReloadedConfigurationIsRenderedAgain() {
        update(BOSSBAR_ONLY, snapshot(23.4));
        display.update(player, settings(2), "en", true, BOSSBAR_ONLY, snapshot(23.4), now);
        assertEquals(2, renders.get());
        assertEquals(List.of("name", "progress"), bossBarChanges);
    }

    @Test
    void nothingIsRenderedWithoutAnyDisplay() {
        update(DisplayPrefs.NONE, snapshot(23.4));
        display.update(player, settings, "en", false, EVERY_DISPLAY, snapshot(23.4), now);
        update(EVERY_DISPLAY, null);
        assertEquals(0, renders.get());
        assertEquals(List.of(), sent);
    }

    @Test
    void aFooterShownAgainIsResentWithoutRenderingAgain() {
        update(FOOTER_ONLY, snapshot(23.4));
        update(DisplayPrefs.NONE, snapshot(23.4));
        update(FOOTER_ONLY, snapshot(23.4));
        assertEquals(1, renders.get());
        assertEquals(List.of("sendPlayerListFooter", "sendPlayerListFooter", "sendPlayerListFooter"), sent);
    }

    @Test
    void aHiddenBossBarIsShownAgainWithoutRenderingAgain() {
        update(BOSSBAR_ONLY, snapshot(23.4));
        update(DisplayPrefs.NONE, snapshot(23.4));
        update(BOSSBAR_ONLY, snapshot(23.4));
        assertEquals(1, renders.get());
        assertEquals(List.of("showBossBar", "hideBossBar", "showBossBar"), sent);
    }

    @Test
    void anUnavailableRegionUsesItsOwnTemplate() {
        RegionSnapshot unavailable = new RegionSnapshot("R12", "world", Optional.empty(), 1, Double.NaN, Double.NaN, Double.NaN,
                new RegionStats(Double.NaN, Double.NaN, Double.NaN, 0), 0, true);
        update(EVERY_DISPLAY, unavailable);
        assertEquals(List.of("display.unavailable", "display.unavailable", "display.unavailable"), templates);
    }

    private void update(DisplayPrefs prefs, RegionSnapshot snapshot) {
        display.update(player, settings, "en", true, prefs, snapshot, now);
        now += SECOND;
    }

    private static Settings settings(long generation) {
        return new ConfigFiles(Path.of("unused"), PlayerDisplayTest.class.getClassLoader()::getResourceAsStream)
                .builtInDefaults(generation);
    }

    private static Settings thresholds(long generation, double warning, double critical) {
        Settings defaults = settings(generation);
        ConfigSnapshot config = ((LoadResult.Loaded<ConfigSnapshot>) ConfigLoader.load(ConfigLoader.FILE_NAME,
                "thresholds:\n  warning: " + warning + "\n  critical: " + critical + "\n")).value();
        return new Settings(config, defaults.lang(), generation);
    }

    private static RegionSnapshot snapshot(double mspt) {
        return snapshot(mspt, 20, 39);
    }

    private static RegionSnapshot snapshot(double mspt, double average, double p95) {
        return new RegionSnapshot("R12", "world", Optional.of(new BlockPosition(1204, 64, -3380)), 3, 19.8, 19.9, mspt,
                new RegionStats(average, 41, p95, 60), 0, false);
    }

    private static Player recordingPlayer(List<String> sent) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "showBossBar", "hideBossBar", "sendActionBar", "sendPlayerListFooter" -> sent.add(method.getName());
                        default -> {
                        }
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    private static Player recordingPlayer(List<String> sent, List<Component> actionBars) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "showBossBar", "hideBossBar", "sendPlayerListFooter" -> sent.add(method.getName());
                        case "sendActionBar" -> {
                            sent.add(method.getName());
                            actionBars.add((Component) arguments[0]);
                        }
                        default -> {
                        }
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
