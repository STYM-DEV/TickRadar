package dev.stym.tickradar.sample;

import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Server;
import org.bukkit.World;

public final class FoliaRegionTps implements RegionTpsSource {

    private final Server server;
    private final ConcurrentHashMap<String, World> worlds = new ConcurrentHashMap<>();

    public FoliaRegionTps(Server server) {
        this.server = server;
    }

    public void remember(String name, World world) {
        if (!worlds.containsKey(name)) {
            worlds.putIfAbsent(name, world);
        }
    }

    @Override
    public double[] regionTps(String world, int chunkX, int chunkZ) {
        World known = worlds.get(world);
        return known == null ? null : server.getRegionTPS(known, chunkX, chunkZ);
    }
}
