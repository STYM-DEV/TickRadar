package dev.stym.tickradar.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertChannel;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.AlertKind;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.player.DisplayPrefs;
import dev.stym.tickradar.player.OnlinePlayers;
import dev.stym.tickradar.player.PlayerState;
import dev.stym.tickradar.sample.ErrorReporter;
import dev.stym.tickradar.schedule.TaskScheduler;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class AlertDispatcherTest {

    private static final AlertEvent CRITICAL = new AlertEvent("R12", AlertKind.CRITICAL, 52.1, 5, "world",
            Optional.of(new BlockPosition(1204, 64, -3380)), Instant.EPOCH);
    private static final AlertEvent RECOVERED = new AlertEvent("R12", AlertKind.RECOVERED, 31.0, 5, "world",
            Optional.of(new BlockPosition(1204, 64, -3380)), Instant.EPOCH);

    private final List<String> threads = new CopyOnWriteArrayList<>();
    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final OnlinePlayers online = new OnlinePlayers();
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private Settings settings = settings("");
    private boolean stopping;
    private boolean stopDuringAsync;
    private final AlertDispatcher dispatcher = new AlertDispatcher(() -> settings, online, scheduler, new Renderer(),
            capturingLogger(), new ErrorReporter(capturingLogger()), () -> stopping);

    @Test
    void theConsoleGetsThePlainAlertOffTheCallingThread() {
        dispatcher.accept(CRITICAL);
        assertEquals(List.of("async"), threads);
        assertEquals(1, logged.size());
        assertEquals(Level.WARNING, logged.getFirst().getLevel());
        assertEquals("Region R12 in world \"world\" around X 1204, Z -3380 (5 players) is at 52.1 ms (critical).", logged.getFirst().getMessage());
    }

    @Test
    void aRecoveryIsLoggedAsInformation() {
        dispatcher.accept(RECOVERED);
        assertEquals(Level.INFO, logged.getFirst().getLevel());
        assertEquals("Region R12 in world \"world\" around X 1204, Z -3380 has recovered: 31.0 ms.", logged.getFirst().getMessage());
    }

    @Test
    void playersWithTheTeleportPermissionGetTheAlertWithATeleportLinkOnTheirOwnThread() {
        List<Component> alice = join(true, true, DisplayPrefs.NONE, "en");
        dispatcher.accept(CRITICAL);
        assertEquals(List.of("async", "player"), threads);
        assertEquals(1, alice.size());
        assertEquals("[TickRadar] Region R12 in world \"world\" around X 1204, Z -3380 (5 players) is at 52.1 ms (critical). [Teleport]",
                plain(alice.getFirst()));
        assertTrue(clicks(alice.getFirst()).contains(ClickEvent.runCommand("/tickradar tp R12")));
    }

    @Test
    void playersWithoutTheTeleportPermissionGetTheAlertWithoutALink() {
        List<Component> alice = join(true, false, DisplayPrefs.NONE, "en");
        dispatcher.accept(CRITICAL);
        assertEquals(1, alice.size());
        assertEquals("[TickRadar] Region R12 in world \"world\" around X 1204, Z -3380 (5 players) is at 52.1 ms (critical). ",
                plain(alice.getFirst()));
        assertEquals(List.of(), clicks(alice.getFirst()));
    }

    @Test
    void theRecoveryHasNoTeleportLink() {
        List<Component> alice = join(true, true, DisplayPrefs.NONE, "en");
        dispatcher.accept(RECOVERED);
        assertEquals("[TickRadar] Region R12 in world \"world\" around X 1204, Z -3380 has recovered: 31.0 ms.", plain(alice.getFirst()));
        assertEquals(List.of(), clicks(alice.getFirst()));
    }

    @Test
    void playersWithoutThePermissionOrWhoTurnedAlertsOffGetNothing() {
        List<Component> guest = join(false, true, DisplayPrefs.NONE, "en");
        List<Component> quiet = join(true, true, DisplayPrefs.NONE.withAlerts(false), "en");
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(), guest);
        assertEquals(List.of(), quiet);
        assertEquals(1, logged.size());
    }

    @Test
    void eachPlayerReadsTheAlertInHisLanguage() {
        List<Component> french = join(true, true, DisplayPrefs.NONE, "fr");
        dispatcher.accept(CRITICAL);
        assertEquals("[TickRadar] La région R12 du monde «\u00A0world\u00A0», vers X 1204, Z -3380 (5 joueurs), est à 52,1 ms (critique). [Téléporter]",
                plain(french.getFirst()));
    }

    @Test
    void theFrenchRecoveryUsesADecimalCommaAndFrenchTypography() {
        List<Component> french = join(true, true, DisplayPrefs.NONE, "fr");
        dispatcher.accept(RECOVERED);
        assertEquals("[TickRadar] La région R12 du monde «\u00A0world\u00A0», vers X 1204, Z -3380, est revenue à la normale\u00A0: 31,0 ms.",
                plain(french.getFirst()));
    }

    @Test
    void theWorldNameIsNeverReadAsMiniMessage() {
        dispatcher.accept(new AlertEvent("R12", AlertKind.CRITICAL, 52.1, 1, "<red>w</red>", Optional.of(new BlockPosition(0, 64, 2)),
                Instant.EPOCH));
        assertEquals("Region R12 in world \"<red>w</red>\" around X 0, Z 2 (1 player) is at 52.1 ms (critical).",
                logged.getFirst().getMessage());
    }

    @Test
    void registeredSinksAreCalledOnlyForTheirChannel() {
        List<AlertEvent> discord = new ArrayList<>();
        dispatcher.register(AlertChannel.DISCORD, discord::add);
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(CRITICAL), discord);
        settings = settings("alerts:\n  notify: [console, players]\n");
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(CRITICAL), discord);
    }

    @Test
    void aFailingSinkDoesNotStopTheOthers() {
        List<AlertEvent> received = new ArrayList<>();
        dispatcher.register(AlertChannel.DISCORD, event -> {
            throw new IllegalStateException("boom");
        });
        dispatcher.register(AlertChannel.DISCORD, received::add);
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(CRITICAL), received);
    }

    @Test
    void anUnregisteredSinkIsNoLongerCalled() {
        List<AlertEvent> received = new ArrayList<>();
        AlertSink sink = received::add;
        dispatcher.register(AlertChannel.DISCORD, sink);
        dispatcher.unregister(sink);
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(), received);
    }

    @Test
    void disabledAlertsOrAStoppingPluginSendNothing() {
        List<Component> alice = join(true, true, DisplayPrefs.NONE, "en");
        settings = settings("alerts:\n  enabled: false\n");
        dispatcher.accept(CRITICAL);
        settings = settings("");
        stopping = true;
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(), alice);
        assertEquals(List.of(), logged);
    }

    @Test
    void aPluginStoppingDuringTheDispatchLogsNoError() {
        stopDuringAsync = true;
        dispatcher.accept(CRITICAL);
        assertEquals(List.of(), logged);
    }

    @Test
    void theConsoleChannelCanBeTurnedOff() {
        List<Component> alice = join(true, true, DisplayPrefs.NONE, "en");
        settings = settings("alerts:\n  notify: [players]\n");
        dispatcher.accept(CRITICAL);
        assertEquals(1, alice.size());
        assertEquals(List.of(), logged);
    }

    @Test
    void theGlobalRegionHasItsOwnText() {
        List<Component> alice = join(true, true, DisplayPrefs.NONE, "en");
        dispatcher.accept(new AlertEvent("G", AlertKind.WARNING, 44.0, 3, "", Optional.empty(), Instant.EPOCH));
        assertEquals("[TickRadar] The global region G is at 44.0 ms (warning).", plain(alice.getFirst()));
    }

    @Test
    void anEndedRegionIsNoLongerObserved() {
        dispatcher.accept(new AlertEvent("R12", AlertKind.ENDED, 52.1, 0, "world", Optional.of(new BlockPosition(1204, 64, -3380)),
                Instant.EPOCH));
        assertEquals("Region R12 in world \"world\" around X 1204, Z -3380 is no longer observed: no players are left there.",
                logged.getFirst().getMessage());
    }

    private List<Component> join(boolean canReceiveAlerts, boolean canTeleport, DisplayPrefs prefs, String language) {
        List<Component> received = new ArrayList<>();
        Player player = player(received::add);
        UUID id = UUID.randomUUID();
        online.publish(new PlayerState(id, player, "R12", language, true, canReceiveAlerts, canTeleport, prefs));
        return received;
    }

    private static Settings settings(String config) {
        Settings defaults = new ConfigFiles(Path.of("unused"), AlertDispatcherTest.class.getClassLoader()::getResourceAsStream)
                .builtInDefaults(1);
        ConfigSnapshot snapshot = ((LoadResult.Loaded<ConfigSnapshot>) ConfigLoader.load(ConfigLoader.FILE_NAME, config)).value();
        return new Settings(snapshot, defaults.lang(), 1);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<ClickEvent> clicks(Component component) {
        List<ClickEvent> events = new ArrayList<>();
        collectClicks(component, events);
        return events;
    }

    private static void collectClicks(Component component, List<ClickEvent> events) {
        if (component.clickEvent() != null) {
            events.add(component.clickEvent());
        }
        component.children().forEach(child -> collectClicks(child, events));
    }

    private Logger capturingLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private static Player player(Consumer<Component> messages) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendMessage") && arguments.length == 1 && arguments[0] instanceof Component message) {
                        messages.accept(message);
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    private final class RecordingScheduler implements TaskScheduler {

        @Override
        public long currentRegionTick() {
            return NO_TICK;
        }

        @Override
        public boolean runForPlayerNextTick(Player player, Runnable task) {
            return runForPlayer(player, task);
        }

        @Override
        public Task runAsync(Runnable task) {
            threads.add("async");
            if (stopDuringAsync) {
                stopping = true;
                throw new IllegalStateException("plugin disabled");
            }
            task.run();
            return () -> {
            };
        }

        @Override
        public void runGlobal(Runnable task) {
            threads.add("global");
            task.run();
        }

        @Override
        public Task runGlobalAtFixedRate(Consumer<Task> task, long delayTicks, long periodTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean runForPlayer(Player player, Runnable task) {
            threads.add("player");
            task.run();
            return true;
        }

        @Override
        public Task runForPlayerAtFixedRate(Player player, Consumer<Task> task, Runnable retired, long delayTicks, long periodTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runAtLocation(Location location, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Task runAtChunkAtFixedRate(World world, int chunkX, int chunkZ, Consumer<Task> task, long delayTicks,
                                          long periodTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancelAll() {
        }
    }
}
