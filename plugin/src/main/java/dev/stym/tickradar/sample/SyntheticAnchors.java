package dev.stym.tickradar.sample;

import dev.stym.tickradar.config.ConfigSnapshot.SyntheticPoint;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.schedule.TaskScheduler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;

public final class SyntheticAnchors {

    private record Running(UUID observer, TaskScheduler.Task task) {
    }

    private final SamplingContext context;
    private final Server server;
    private final Logger logger;
    private final List<Running> running = new ArrayList<>();

    public SyntheticAnchors(SamplingContext context, Server server, Logger logger) {
        this.context = context;
        this.server = server;
        this.logger = logger;
    }

    public synchronized void restart() {
        stop();
        if (context.isStopping()) {
            return;
        }
        int interval = context.settings().get().config().sampling().intervalTicks();
        for (SyntheticPoint point : context.settings().get().config().syntheticAnchors()) {
            World world = server.getWorld(point.world());
            if (world == null) {
                logger.warning("debug.synthetic-anchors: unknown world '" + point.world() + "'; synthetic measurement point ignored");
                continue;
            }
            UUID observer = observerOf(point);
            BlockPosition position = new BlockPosition(point.x(), world.getSeaLevel(), point.z());
            TaskScheduler.Task task = context.scheduler().runAtChunkAtFixedRate(world, position.chunkX(), position.chunkZ(),
                    current -> tick(current, observer, world, position), interval, interval);
            running.add(new Running(observer, task));
        }
        if (!running.isEmpty()) {
            logger.info("Synthetic measurement points (testing only): " + running.size());
        }
    }

    public synchronized void stop() {
        for (Running point : running) {
            point.task().cancel();
            context.regions().detach(point.observer());
        }
        running.clear();
    }

    public synchronized int size() {
        return running.size();
    }

    static UUID observerOf(SyntheticPoint point) {
        String name = "tickradar:synthetic:" + point.world() + ":" + point.x() + ":" + point.z();
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private void tick(TaskScheduler.Task current, UUID observer, World world, BlockPosition position) {
        if (context.isStopping()) {
            current.cancel();
            return;
        }
        long start = System.nanoTime();
        try {
            context.regions().observe(observer, false, world, position, start);
        } catch (Throwable error) {
            context.errors().report("the sampling of a synthetic measurement point", error);
        } finally {
            context.costs().sample().record(System.nanoTime() - start);
        }
    }
}
