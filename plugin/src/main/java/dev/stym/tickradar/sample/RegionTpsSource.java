package dev.stym.tickradar.sample;

@FunctionalInterface
public interface RegionTpsSource {

    double[] regionTps(String world, int chunkX, int chunkZ);
}
