package dev.stym.tickradar.engine;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class ValueFormat {

    public static final String MISSING = "\u2014";
    public static final long MISSING_TENTHS = Long.MIN_VALUE;
    public static final char DECIMAL_POINT = '.';
    public static final char DECIMAL_COMMA = ',';
    private static final double MAX_TPS = 20.0;

    private ValueFormat() {
    }

    public static long tpsTenths(double tps) {
        return isUsable(tps) ? tenths(Math.min(tps, MAX_TPS)) : MISSING_TENTHS;
    }

    public static long msptTenths(double mspt) {
        return isUsable(mspt) ? tenths(mspt) : MISSING_TENTHS;
    }

    private static long tenths(double value) {
        return Math.round(value * 10);
    }

    public static String tps(double tps) {
        return isUsable(tps) ? oneDecimal(Math.min(tps, MAX_TPS)) : MISSING;
    }

    public static String mspt(double mspt) {
        return isUsable(mspt) ? oneDecimal(mspt) : MISSING;
    }

    public static String mspt(double mspt, char decimalSeparator) {
        return withDecimalSeparator(mspt(mspt), decimalSeparator);
    }

    public static String oneDecimal(double value) {
        long tenths = tenths(value);
        long absolute = Math.abs(tenths);
        StringBuilder out = new StringBuilder(12);
        if (tenths < 0) {
            out.append('-');
        }
        return out.append(absolute / 10).append(DECIMAL_POINT).append((char) ('0' + absolute % 10)).toString();
    }

    public static String oneDecimal(double value, char decimalSeparator) {
        return withDecimalSeparator(oneDecimal(value), decimalSeparator);
    }

    public static String withDecimalSeparator(String formatted, char decimalSeparator) {
        return decimalSeparator == DECIMAL_POINT ? formatted : formatted.replace(DECIMAL_POINT, decimalSeparator);
    }

    public static char decimalSeparator(String language) {
        if (language == null || language.isBlank()) {
            return DECIMAL_POINT;
        }
        char separator = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(language.strip())).getDecimalSeparator();
        return separator == DECIMAL_COMMA ? DECIMAL_COMMA : DECIMAL_POINT;
    }

    public static boolean isUsable(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
