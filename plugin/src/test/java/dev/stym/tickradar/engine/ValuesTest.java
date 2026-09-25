package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ValuesTest {

    @Test
    void formatsWithOneDecimalWhateverTheLocale() {
        assertEquals("23.4", ValueFormat.mspt(23.44));
        assertEquals("23.5", ValueFormat.mspt(23.46));
        assertEquals("0.0", ValueFormat.mspt(0));
        assertEquals("1204.0", ValueFormat.mspt(1204));
        assertEquals("-3.5", ValueFormat.oneDecimal(-3.5));
    }

    @Test
    void theDecimalSeparatorFollowsTheLanguageOfTheMessage() {
        assertEquals(',', ValueFormat.decimalSeparator("fr"));
        assertEquals(',', ValueFormat.decimalSeparator("de"));
        assertEquals('.', ValueFormat.decimalSeparator("en"));
        assertEquals('.', ValueFormat.decimalSeparator(null));
        assertEquals('.', ValueFormat.decimalSeparator(" "));
        assertEquals("23,4", ValueFormat.mspt(23.44, ','));
        assertEquals("-3,5", ValueFormat.oneDecimal(-3.5, ','));
        assertEquals("23.4", ValueFormat.mspt(23.44, '.'));
        assertEquals(ValueFormat.MISSING, ValueFormat.mspt(Double.NaN, ','));
    }

    @Test
    void theJvmLocaleChangesNothing() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals("23.4", ValueFormat.mspt(23.44));
            assertEquals('.', ValueFormat.decimalSeparator("en"));
            Locale.setDefault(Locale.US);
            assertEquals(',', ValueFormat.decimalSeparator("fr"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void tpsIsCappedAtTwenty() {
        assertEquals("20.0", ValueFormat.tps(20.03));
        assertEquals("19.8", ValueFormat.tps(19.8));
    }

    @Test
    void tenthsChangeExactlyWhenTheShownValueChanges() {
        double[] values = {0, 0.04, 0.05, 0.149, 0.15, 1.25, 12.349, 19.96, 20.0, 23.45, 59.99};
        for (double first : values) {
            for (double second : values) {
                assertEquals(ValueFormat.mspt(first).equals(ValueFormat.mspt(second)),
                        ValueFormat.msptTenths(first) == ValueFormat.msptTenths(second), first + " / " + second);
                assertEquals(ValueFormat.tps(first).equals(ValueFormat.tps(second)),
                        ValueFormat.tpsTenths(first) == ValueFormat.tpsTenths(second), first + " / " + second);
            }
        }
        assertEquals(ValueFormat.MISSING_TENTHS, ValueFormat.msptTenths(Double.NaN));
        assertEquals(ValueFormat.MISSING_TENTHS, ValueFormat.tpsTenths(-1));
        assertEquals(200, ValueFormat.tpsTenths(20.4));
    }

    @Test
    void unusableValuesAreShownAsMissing() {
        assertEquals("\u2014", ValueFormat.MISSING);
        assertEquals(ValueFormat.MISSING, ValueFormat.tps(Double.NaN));
        assertEquals(ValueFormat.MISSING, ValueFormat.mspt(-1));
        assertEquals(ValueFormat.MISSING, ValueFormat.mspt(Double.POSITIVE_INFINITY));
    }

    @Test
    void thresholdsClassifyTheMspt() {
        Thresholds thresholds = new Thresholds(40, 50);
        assertEquals(HealthStatus.OK, thresholds.status(39.99));
        assertEquals(HealthStatus.WARNING, thresholds.status(40));
        assertEquals(HealthStatus.CRITICAL, thresholds.status(50));
    }

    @Test
    void thresholdsMustBeOrderedAndPositive() {
        assertTrue(Thresholds.isValid(1, 2));
        assertFalse(Thresholds.isValid(0, 2));
        assertFalse(Thresholds.isValid(50, 40));
        assertFalse(Thresholds.isValid(40, 40));
        assertFalse(Thresholds.isValid(Double.NaN, 40));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(50, 40));
    }

    @Test
    void tpsReadingsMustBeFiniteAndPositive() {
        assertTrue(new TpsReading(0, 20, 0).isValid());
        assertFalse(new TpsReading(-1, 20, 0).isValid());
        assertFalse(new TpsReading(Double.NaN, 20, 0).isValid());
        assertFalse(new TpsReading(20, Double.POSITIVE_INFINITY, 0).isValid());
    }

    @Test
    void tpsReadingsAreOrderedByTheirReadingTime() {
        TpsReading earlier = new TpsReading(20, 20, 100);
        TpsReading later = new TpsReading(19, 19, 200);
        assertTrue(later.isNotOlderThan(earlier));
        assertTrue(earlier.isNotOlderThan(null));
        assertFalse(earlier.isNotOlderThan(later));
    }

    @Test
    void blockPositionsGiveTheirChunk() {
        BlockPosition position = new BlockPosition(-1, 64, 31);
        assertEquals(-1, position.chunkX());
        assertEquals(1, position.chunkZ());
    }
}
