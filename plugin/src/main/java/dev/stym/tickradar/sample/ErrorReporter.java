package dev.stym.tickradar.sample;

import dev.stym.tickradar.engine.WarnOnce;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorReporter {

    private final Logger logger;
    private final WarnOnce once = new WarnOnce();

    public ErrorReporter(Logger logger) {
        this.logger = logger;
    }

    public void report(String task, Throwable error) {
        if (once.firstTime(task + "|" + error.getClass().getName())) {
            logger.log(Level.WARNING, "Unexpected error in " + task + " (reported once per type; TickRadar keeps running)", error);
        }
    }

    public void warnOnce(String key, String message) {
        if (once.firstTime(key)) {
            logger.warning(message);
        }
    }
}
