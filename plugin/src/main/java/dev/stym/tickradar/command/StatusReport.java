package dev.stym.tickradar.command;

import dev.stym.tickradar.engine.CostMeter;
import dev.stym.tickradar.engine.ValueFormat;
import java.util.List;

public record StatusReport(
        boolean regionTps,
        int intervalTicks,
        int historySeconds,
        int tpsIntervalSeconds,
        CostMeter.Summary sample,
        CostMeter.Summary playerSample,
        CostMeter.Summary display,
        CostMeter.Summary measure,
        CostMeter.Summary msptCall,
        CostMeter.Summary tpsCall,
        CostMeter.Summary global,
        CostMeter.Summary tpsThread,
        CostMeter.Summary globalTps,
        int anchors,
        int players,
        int syntheticAnchors,
        String discord,
        int activeAlerts,
        int displayFallbackTemplates,
        int playerTickPeak,
        int playerTickPeakMax) {

    public List<String> lines() {
        return List.of(
                "measurement=region regionTPS=" + (regionTps ? "yes" : "no") + " interval=" + intervalTicks
                        + " history=" + historySeconds + " tps_interval=" + tpsIntervalSeconds,
                costs("sample", sample) + " samples=" + sample.count() + " " + averageAndP99("player_sample", playerSample)
                        + " " + averageAndP99("display", display) + " display_fallback_templates=" + displayFallbackTemplates
                        + " player_tick_peak=" + playerTickPeak + " player_tick_peak_max=" + playerTickPeakMax,
                costs("measure", measure) + " measures=" + measure.count(),
                costs("mspt_call", msptCall) + " " + costs("tps_call", tpsCall) + " " + costs("tps_thread", tpsThread)
                        + " tps_passes=" + tpsThread.count(),
                costs("global", global) + " global_samples=" + global.count() + " " + costs("global_tps", globalTps)
                        + " global_tps_reads=" + globalTps.count(),
                "anchors=" + anchors + " players=" + players + " synthetic=" + syntheticAnchors + " alerts_active=" + activeAlerts,
                "discord=" + discord);
    }

    private static String averageAndP99(String name, CostMeter.Summary summary) {
        return name + "_avg_us=" + ValueFormat.oneDecimal(summary.averageMicros())
                + " " + name + "_p99_us=" + ValueFormat.oneDecimal(summary.p99Micros());
    }

    private static String costs(String name, CostMeter.Summary summary) {
        return averageAndP99(name, summary) + " " + name + "_max_us=" + ValueFormat.oneDecimal(summary.maxMicros());
    }
}
