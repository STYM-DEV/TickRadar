package dev.stym.tickradar.sample;

import dev.stym.tickradar.engine.CostMeter;
import dev.stym.tickradar.engine.TickPeak;

public record SamplingCosts(CostMeter sample, CostMeter measure, CostMeter msptCall, CostMeter tpsCall, CostMeter global,
                            CostMeter tpsThread, CostMeter globalTps, CostMeter playerSample,
                            CostMeter display, TickPeak playerTickPeak) {

    static final long TICK_PEAK_WINDOW_NANOS = 30_000_000_000L;

    public static SamplingCosts create() {
        return new SamplingCosts(new CostMeter(), new CostMeter(), new CostMeter(), new CostMeter(), new CostMeter(),
                new CostMeter(), new CostMeter(), new CostMeter(), new CostMeter(),
                new TickPeak(System.nanoTime(), TICK_PEAK_WINDOW_NANOS));
    }
}
