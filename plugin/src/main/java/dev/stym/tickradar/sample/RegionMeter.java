package dev.stym.tickradar.sample;

import dev.stym.tickradar.engine.TpsReading;
import org.bukkit.Server;
import org.bukkit.World;

public final class RegionMeter {

    private static final int CURRENT_REGION_TPS_1M = 0;

    private final Server server;
    private final boolean regionTpsAvailable;
    private final SamplingCosts costs;

    public RegionMeter(Server server, boolean regionTpsAvailable, SamplingCosts costs) {
        this.server = server;
        this.regionTpsAvailable = regionTpsAvailable;
        this.costs = costs;
    }

    public static boolean detectRegionTps() {
        try {
            Server.class.getMethod("getRegionTPS", World.class, int.class, int.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public boolean regionTpsAvailable() {
        return regionTpsAvailable;
    }

    public double measureRegionMspt() {
        long start = System.nanoTime();
        try {
            double mspt = server.getAverageTickTime();
            costs.msptCall().record(System.nanoTime() - start);
            return mspt;
        } finally {
            costs.measure().record(System.nanoTime() - start);
        }
    }

    public double measureGlobalMspt() {
        return server.getAverageTickTime();
    }

    public TpsReading readCurrentRegionTps(long nowNanos) {
        double tps1m = server.getTPS()[CURRENT_REGION_TPS_1M];
        return new TpsReading(tps1m, tps1m, nowNanos);
    }
}
