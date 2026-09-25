package dev.stym.tickradar.alert;

@FunctionalInterface
public interface AlertSink {

    void accept(AlertEvent event);
}
