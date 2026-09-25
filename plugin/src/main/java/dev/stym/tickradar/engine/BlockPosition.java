package dev.stym.tickradar.engine;

public record BlockPosition(int x, int y, int z) {

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }
}
