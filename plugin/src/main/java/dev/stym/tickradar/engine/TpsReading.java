package dev.stym.tickradar.engine;

public record TpsReading(double tps5s, double tps1m, long readAtNanos) {

    public boolean isValid() {
        return ValueFormat.isUsable(tps5s) && ValueFormat.isUsable(tps1m);
    }

    public boolean isNotOlderThan(TpsReading other) {
        return other == null || readAtNanos - other.readAtNanos() >= 0;
    }
}
