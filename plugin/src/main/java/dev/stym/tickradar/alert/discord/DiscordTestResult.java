package dev.stym.tickradar.alert.discord;

import java.time.Duration;

public record DiscordTestResult(Outcome outcome, int httpStatus, Duration rateLimitWait, String detail) {

    public static final int NO_STATUS = -1;

    public enum Outcome {
        SENT,
        NOT_CONFIGURED,
        INVALID_URL,
        DISABLED,
        REJECTED,
        FAILED,
        DROPPED,
        STOPPED
    }

    public DiscordTestResult {
        rateLimitWait = rateLimitWait == null ? Duration.ZERO : rateLimitWait;
        detail = detail == null ? "" : detail;
    }

    static DiscordTestResult sent(int status, Duration waited) {
        return new DiscordTestResult(Outcome.SENT, status, waited, "");
    }

    static DiscordTestResult of(Outcome outcome, int status, String detail) {
        return new DiscordTestResult(outcome, status, Duration.ZERO, detail);
    }

    static DiscordTestResult of(Outcome outcome) {
        return of(outcome, NO_STATUS, "");
    }

    public boolean isSuccess() {
        return outcome == Outcome.SENT;
    }
}
