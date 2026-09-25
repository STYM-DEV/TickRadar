package dev.stym.tickradar.engine;

public record RegionStats(double average, double max, double p95, int count) {

    public static final RegionStats EMPTY = new RegionStats(Double.NaN, Double.NaN, Double.NaN, 0);
}
