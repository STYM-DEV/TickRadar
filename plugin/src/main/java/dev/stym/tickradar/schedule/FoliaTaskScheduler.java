package dev.stym.tickradar.schedule;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class FoliaTaskScheduler implements TaskScheduler {

    private final Plugin plugin;
    private final Server server;

    public FoliaTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    @Override
    public long currentRegionTick() {
        try {
            return server.getCurrentTick();
        } catch (IllegalStateException notOnARegion) {
            return NO_TICK;
        }
    }

    @Override
    public Task runAsync(Runnable task) {
        return wrap(server.getAsyncScheduler().runNow(plugin, scheduled -> task.run()));
    }

    @Override
    public void runGlobal(Runnable task) {
        server.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public Task runGlobalAtFixedRate(Consumer<Task> task, long delayTicks, long periodTicks) {
        return wrap(server.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.accept(scheduled::cancel),
                Math.max(1, delayTicks), periodTicks));
    }

    @Override
    public boolean runForPlayer(Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
            return true;
        }
        return runForPlayerNextTick(player, task);
    }

    @Override
    public boolean runForPlayerNextTick(Player player, Runnable task) {
        return player.getScheduler().execute(plugin, task, null, 1);
    }

    @Override
    public Task runForPlayerAtFixedRate(Player player, Consumer<Task> task, Runnable retired, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = player.getScheduler().runAtFixedRate(plugin, running -> task.accept(running::cancel), retired,
                Math.max(1, delayTicks), periodTicks);
        return scheduled == null ? null : wrap(scheduled);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        server.getRegionScheduler().execute(plugin, location, task);
    }

    @Override
    public Task runAtChunkAtFixedRate(World world, int chunkX, int chunkZ, Consumer<Task> task, long delayTicks, long periodTicks) {
        return wrap(server.getRegionScheduler().runAtFixedRate(plugin, world, chunkX, chunkZ,
                scheduled -> task.accept(scheduled::cancel), Math.max(1, delayTicks), periodTicks));
    }

    @Override
    public void cancelAll() {
        server.getGlobalRegionScheduler().cancelTasks(plugin);
        server.getAsyncScheduler().cancelTasks(plugin);
    }

    private static Task wrap(ScheduledTask scheduled) {
        return scheduled::cancel;
    }
}
