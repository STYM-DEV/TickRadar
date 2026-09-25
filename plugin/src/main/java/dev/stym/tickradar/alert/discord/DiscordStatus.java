package dev.stym.tickradar.alert.discord;

import java.util.Locale;

public record DiscordStatus(State state, int queued, long dropped, long sent, long failed, int lastHttpStatus,
                           String maskedUrl) {

    public enum State {
        OFF,
        INVALID_URL,
        READY,
        RATE_LIMITED,
        DISABLED,
        STOPPED;

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public String summary() {
        return state.key()
                + " discord_queue=" + queued
                + " discord_dropped=" + dropped
                + " discord_sent=" + sent
                + " discord_failed=" + failed
                + " discord_last_http=" + (lastHttpStatus < 0 ? "none" : Integer.toString(lastHttpStatus));
    }
}
