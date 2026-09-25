package dev.stym.tickradar.alert.discord;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DiscordPayload {

    public static final int MAX_EMBEDS = 10;
    public static final int MAX_TEXT = 2000;
    static final int MAX_USERNAME = 80;
    static final int COLOR_WARNING = 0xF1C40F;
    static final int COLOR_CRITICAL = 0xE74C3C;
    static final int COLOR_RECOVERED = 0x2ECC71;
    static final int COLOR_ENDED = 0x95A5A6;
    static final int COLOR_TEST = 0x3498DB;
    private static final String ELLIPSIS = "...";
    private static final List<String> FORBIDDEN_USERNAME_PARTS = List.of("discord", "clyde");
    private static final Set<String> FORBIDDEN_USERNAMES = Set.of("everyone", "here");

    private DiscordPayload() {
    }

    public static String json(List<DiscordAlert> alerts, DiscordSettings settings) {
        StringBuilder out = new StringBuilder(256 + alerts.size() * 160);
        out.append('{');
        String username = username(settings.username());
        if (!username.isEmpty()) {
            out.append("\"username\":").append(Json.quote(username)).append(',');
        }
        out.append("\"allowed_mentions\":{\"parse\":[]},\"embeds\":[");
        int count = Math.min(alerts.size(), MAX_EMBEDS);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                out.append(',');
            }
            appendEmbed(out, alerts.get(i), settings);
        }
        return out.append("]}").toString();
    }

    public static int fittingCount(List<DiscordAlert> alerts, DiscordSettings settings) {
        int total = 0;
        int count = 0;
        for (DiscordAlert alert : alerts) {
            if (count == MAX_EMBEDS) {
                break;
            }
            total += description(alert, settings).length();
            if (count > 0 && total > MAX_TEXT) {
                break;
            }
            count++;
        }
        return count;
    }

    static String description(DiscordAlert alert, DiscordSettings settings) {
        String text = settings.texts().render(alert, settings.includeCoordinates());
        if (text.length() <= MAX_TEXT) {
            return text;
        }
        int end = MAX_TEXT - ELLIPSIS.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    static String username(String configured) {
        StringBuilder cleaned = new StringBuilder();
        String source = configured == null ? "" : configured;
        source.codePoints().filter(point -> !Character.isISOControl(point)).forEach(cleaned::appendCodePoint);
        String name = cleaned.toString().trim();
        if (name.length() > MAX_USERNAME) {
            name = name.substring(0, MAX_USERNAME).trim();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        boolean forbidden = FORBIDDEN_USERNAMES.contains(lower)
                || FORBIDDEN_USERNAME_PARTS.stream().anyMatch(lower::contains);
        return forbidden ? "" : name;
    }

    static int color(DiscordAlert.Kind kind) {
        return switch (kind) {
            case WARNING -> COLOR_WARNING;
            case CRITICAL -> COLOR_CRITICAL;
            case RECOVERED -> COLOR_RECOVERED;
            case ENDED -> COLOR_ENDED;
            case TEST -> COLOR_TEST;
        };
    }

    private static void appendEmbed(StringBuilder out, DiscordAlert alert, DiscordSettings settings) {
        out.append("{\"description\":").append(Json.quote(description(alert, settings)))
                .append(",\"color\":").append(color(alert.kind()))
                .append(",\"timestamp\":").append(Json.quote(alert.at().truncatedTo(ChronoUnit.MILLIS).toString()))
                .append('}');
    }
}
