package dev.stym.tickradar.engine;

@FunctionalInterface
public interface OwnershipProbe {

    boolean ownsChunk(int chunkX, int chunkZ);
}
