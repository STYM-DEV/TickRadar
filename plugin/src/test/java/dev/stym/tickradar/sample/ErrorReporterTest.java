package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ErrorReporterTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger(ErrorReporterTest.class.getName() + "." + UUID.randomUUID());
    private final ErrorReporter reporter = new ErrorReporter(logger);

    {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    void aFirstFailureIsLoggedAtWarningLevelWithTheThrowableAttached() {
        RuntimeException failure = new RuntimeException("boom");
        reporter.report("the warm-up of the displays", failure);
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.getFirst().getLevel());
        assertEquals(failure, records.getFirst().getThrown());
        assertTrue(records.getFirst().getMessage().contains("the warm-up of the displays"));
    }

    @Test
    void theSameTaskAndExceptionTypeIsReportedOnlyOnce() {
        reporter.report("the warm-up of the sampling", new IllegalStateException("first"));
        reporter.report("the warm-up of the sampling", new IllegalStateException("second"));
        reporter.report("the warm-up of the sampling", new IllegalStateException("third"));
        assertEquals(1, records.size());
    }

    @Test
    void aDifferentExceptionTypeForTheSameTaskIsReportedAgain() {
        reporter.report("the warm-up of the sampling", new IllegalStateException("first"));
        reporter.report("the warm-up of the sampling", new IllegalArgumentException("second"));
        assertEquals(2, records.size());
    }

    @Test
    void theSameExceptionTypeForADifferentTaskIsReportedAgain() {
        reporter.report("the warm-up of the displays", new IllegalStateException("first"));
        reporter.report("the warm-up of the sampling", new IllegalStateException("second"));
        assertEquals(2, records.size());
    }

    @Test
    void reportingNeverThrowsEvenForAnErrorInsteadOfAnException() {
        reporter.report("the warm-up of the displays", new OutOfMemoryError("simulated"));
        assertEquals(1, records.size());
    }

    @Test
    void warnOnceLogsAGivenKeyOnlyOnce() {
        reporter.warnOnce("tab-plugin", "the TAB plugin is installed");
        reporter.warnOnce("tab-plugin", "the TAB plugin is installed");
        assertEquals(1, records.size());
        assertEquals("the TAB plugin is installed", records.getFirst().getMessage());
    }

    @Test
    void reportAndWarnOnceShareNoKeySpace() {
        reporter.report("tab-plugin", new IllegalStateException("boom"));
        reporter.warnOnce("tab-plugin", "unrelated warning");
        assertEquals(2, records.size());
    }
}
