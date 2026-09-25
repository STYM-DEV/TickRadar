package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.LoadResult;
import org.junit.jupiter.api.Test;

class SamplingWarmUpTest {

    @Test
    void theWarmUpRunsWithTheDefaultSettings() {
        assertDoesNotThrow(() -> SamplingWarmUp.run(ConfigLoader.defaults()));
    }

    @Test
    void theWarmUpRunsWithTheLongestIntervalAndCustomAlerts() {
        ConfigSnapshot config = ((LoadResult.Loaded<ConfigSnapshot>) ConfigLoader.load(ConfigLoader.FILE_NAME,
                "sampling:\n  interval-ticks: 200\nthresholds:\n  warning: 5\n  critical: 6\nalerts:\n  enabled: false\n")).value();
        assertDoesNotThrow(() -> SamplingWarmUp.run(config));
    }
}
