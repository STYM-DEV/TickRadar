package dev.stym.tickradar.alert.discord;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record HttpReply(int status, Map<String, String> headers, String body) {

    public HttpReply {
        Map<String, String> normalized = new HashMap<>();
        headers.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
        headers = Map.copyOf(normalized);
        body = body == null ? "" : body;
    }

    public static HttpReply of(int status) {
        return new HttpReply(status, Map.of(), "");
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
