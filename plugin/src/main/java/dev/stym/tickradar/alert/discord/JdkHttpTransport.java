package dev.stym.tickradar.alert.discord;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class JdkHttpTransport implements HttpTransport {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String USER_AGENT = "TickRadar (Folia plugin)";

    private final HttpClient client;
    private final Duration requestTimeout;

    public JdkHttpTransport() {
        this(REQUEST_TIMEOUT);
    }

    JdkHttpTransport(Duration requestTimeout) {
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public HttpReply postJson(WebhookUrl url, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url.target())
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(request, CappedBodySubscriber.handler(MAX_BODY_BYTES));
        HttpResponse<byte[]> response = await(exchange);
        return new HttpReply(response.statusCode(), firstValues(response.headers()), new String(response.body(), StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        client.shutdownNow();
    }

    private HttpResponse<byte[]> await(CompletableFuture<HttpResponse<byte[]>> exchange)
            throws IOException, InterruptedException {
        try {
            return exchange.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw new HttpTimeoutException("no complete reply within " + requestTimeout.toMillis() + " ms");
        } catch (InterruptedException e) {
            exchange.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw asIoException(e.getCause());
        }
    }

    private static IOException asIoException(Throwable cause) {
        if (cause instanceof IOException io) {
            return io;
        }
        String name = cause == null ? "unknown" : cause.getClass().getSimpleName();
        return new IOException("request failed: " + name);
    }

    private static Map<String, String> firstValues(HttpHeaders headers) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, List<String>> header : headers.map().entrySet()) {
            if (!header.getValue().isEmpty()) {
                values.put(header.getKey(), header.getValue().getFirst());
            }
        }
        return values;
    }
}
