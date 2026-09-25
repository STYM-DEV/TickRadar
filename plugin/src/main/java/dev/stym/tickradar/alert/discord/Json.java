package dev.stym.tickradar.alert.discord;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Json {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char REPLACEMENT = '\uFFFD';

    private Json() {
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                out.append(c).append(text.charAt(++i));
            } else if (Character.isSurrogate(c)) {
                out.append(REPLACEMENT);
            } else {
                appendEscaped(out, c);
            }
        }
        return out.append('"').toString();
    }

    static OptionalDouble number(String json, String field) {
        if (json == null || json.isEmpty()) {
            return OptionalDouble.empty();
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field)
                + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? OptionalDouble.of(Double.parseDouble(matcher.group(1))) : OptionalDouble.empty();
    }

    private static void appendEscaped(StringBuilder out, char c) {
        switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            default -> appendPlain(out, c);
        }
    }

    private static void appendPlain(StringBuilder out, char c) {
        if (c < 0x20 || c == 0x7f || c == '\u2028' || c == '\u2029') {
            out.append("\\u")
                    .append(HEX[(c >> 12) & 0xF])
                    .append(HEX[(c >> 8) & 0xF])
                    .append(HEX[(c >> 4) & 0xF])
                    .append(HEX[c & 0xF]);
        } else {
            out.append(c);
        }
    }
}
