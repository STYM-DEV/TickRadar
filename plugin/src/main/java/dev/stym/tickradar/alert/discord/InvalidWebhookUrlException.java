package dev.stym.tickradar.alert.discord;

public final class InvalidWebhookUrlException extends Exception {

    public InvalidWebhookUrlException(String reason) {
        super(reason, null, false, false);
    }
}
