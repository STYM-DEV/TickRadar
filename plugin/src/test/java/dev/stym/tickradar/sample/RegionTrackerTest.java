package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class RegionTrackerTest {

    private static final double MSPT = 12.5;
    private static final BlockPosition POSITION = new BlockPosition(16, 64, 16);

    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final ErrorReporter errors = new ErrorReporter(capturingLogger());
    private final SamplingCosts costs = SamplingCosts.create();

    @Test
    void theFallbackTpsIsPublishedWithTheMeasure() {
        RegionSnapshot snapshot = observeWith(() -> new double[] {19.0, 18.0, 17.0});
        assertEquals(MSPT, snapshot.mspt5s());
        assertEquals(19.0, snapshot.tps5s());
        assertEquals(19.0, snapshot.tps1m());
        assertEquals(1, costs.tpsCall().summary().count());
    }

    @Test
    void anUnsupportedFallbackTpsKeepsTheMeasureAndWarnsAboutTheTps() {
        RegionSnapshot snapshot = observeWith(() -> {
            throw new UnsupportedOperationException("no tps");
        });
        assertMeasureKeptWithUnknownTps(snapshot);
        assertEquals(1, logged.size());
        assertTrue(logged.getFirst().getMessage().contains("TPS of region"));
    }

    @Test
    void aFailingFallbackTpsKeepsTheMeasureAndIsReported() {
        RegionSnapshot snapshot = observeWith(() -> {
            throw new IllegalStateException("boom");
        });
        assertMeasureKeptWithUnknownTps(snapshot);
        assertEquals(1, logged.size());
        assertTrue(logged.getFirst().getMessage().contains(RegionTracker.FALLBACK_TPS_TASK));
    }

    private static void assertMeasureKeptWithUnknownTps(RegionSnapshot snapshot) {
        assertFalse(snapshot.unavailable());
        assertEquals(MSPT, snapshot.mspt5s());
        assertFalse(snapshot.isTpsKnown());
    }

    private RegionSnapshot observeWith(Supplier<double[]> tps) {
        Server server = server(tps);
        Settings settings = new Settings(ConfigLoader.defaults(), null, 1);
        RegionTracker tracker = new RegionTracker(new AnchorTracker(), new RegionMeter(server, false, costs),
                new FoliaRegionTps(server), () -> settings, errors, costs, event -> {
        });
        Anchor anchor = tracker.observe(UUID.randomUUID(), true, world(), POSITION, 1_000);
        return anchor.snapshot();
    }

    private static Server server(Supplier<double[]> tps) {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] {Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAverageTickTime" -> MSPT;
                    case "getTPS" -> tps.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
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
}
