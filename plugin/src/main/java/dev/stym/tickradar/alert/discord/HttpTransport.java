package dev.stym.tickradar.alert.discord;

import java.io.IOException;

public interface HttpTransport extends AutoCloseable {

    HttpReply postJson(WebhookUrl url, String json) throws IOException, InterruptedException;

    @Override
    default void close() {
    }
}
