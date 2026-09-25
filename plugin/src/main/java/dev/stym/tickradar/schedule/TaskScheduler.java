package dev.stym.tickradar.schedule;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface TaskScheduler {

    interface Task {

        void cancel();
    }

    long NO_TICK = Long.MIN_VALUE;

    long currentRegionTick();

    Task runAsync(Runnable task);

    void runGlobal(Runnable task);

    Task runGlobalAtFixedRate(Consumer<Task> task, long delayTicks, long periodTicks);

    boolean runForPlayer(Player player, Runnable task);

    boolean runForPlayerNextTick(Player player, Runnable task);

    Task runForPlayerAtFixedRate(Player player, Consumer<Task> task, Runnable retired, long delayTicks, long periodTicks);

    void runAtLocation(Location location, Runnable task);

    Task runAtChunkAtFixedRate(World world, int chunkX, int chunkZ, Consumer<Task> task, long delayTicks, long periodTicks);

    void cancelAll();
}
