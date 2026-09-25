package dev.stym.tickradar.alert.discord;

final class DiscordMarkdown {

    private static final String SPECIAL = "\\*_~`|>[]()<#";

    private DiscordMarkdown() {
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SPECIAL.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
