package dev.stym.tickradar.alert.discord;

import static dev.stym.tickradar.alert.discord.WebhookUrlTest.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdkHttpTransportTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(500);

    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final CountDownLatch releaseBody = new CountDownLatch(1);
    private HttpServer server;
    private HttpServer extraServer;
    private JdkHttpTransport transport;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            path.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"retry_after\": 1.5}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
            exchange.getResponseHeaders().add("X-RateLimit-Reset-After", "2.25");
            exchange.sendResponseHeaders(429, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        transport = new JdkHttpTransport();
    }

    @AfterEach
    void stopServer() {
        releaseBody.countDown();
        transport.close();
        server.stop(0);
        if (extraServer != null) {
            extraServer.stop(0);
        }
    }

    @Test
    void postsJsonAndReadsTheReply() throws Exception {
        WebhookUrl url = WebhookUrl.parse(VALID, "http://127.0.0.1:" + server.getAddress().getPort());
        HttpReply reply = transport.postJson(url, "{\"content\":\"é\"}");
        assertEquals(429, reply.status());
        assertEquals("0", reply.header("x-ratelimit-remaining").orElseThrow());
        assertEquals("2.25", reply.header("X-RateLimit-Reset-After").orElseThrow());
        assertEquals("{\"retry_after\": 1.5}", reply.body());
        assertEquals("{\"content\":\"é\"}", received.get());
        assertEquals("application/json", contentType.get());
        assertEquals(url.target().getPath(), path.get());
    }

    @Test
    void aClosedTransportRefusesToSend() throws Exception {
        WebhookUrl url = WebhookUrl.parse(VALID, "http://127.0.0.1:" + server.getAddress().getPort());
        transport.close();
        assertThrows(IOException.class, () -> transport.postJson(url, "{}"));
    }

    @Test
    void aBodyThatNeverEndsTimesOutAfterTheRequestTimeout() throws Exception {
        WebhookUrl url = extraServer(exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().flush();
            awaitRelease();
            exchange.close();
        });
        JdkHttpTransport shortTransport = new JdkHttpTransport(SHORT_TIMEOUT);
        try {
            long start = System.nanoTime();
            assertThrows(HttpTimeoutException.class, () -> shortTransport.postJson(url, "{}"));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsedMillis >= SHORT_TIMEOUT.toMillis() - 50 && elapsedMillis < 1_500, "took " + elapsedMillis + " ms");
        } finally {
            shortTransport.close();
        }
    }

    @Test
    void aLongBodyIsTruncated() throws Exception {
        byte[] body = new byte[JdkHttpTransport.MAX_BODY_BYTES * 2];
        Arrays.fill(body, (byte) 'x');
        WebhookUrl url = extraServer(exchange -> {
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        HttpReply reply = transport.postJson(url, "{}");
        assertEquals(400, reply.status());
        assertEquals(JdkHttpTransport.MAX_BODY_BYTES, reply.body().length());
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 429, 400})
    void theStatusAndHeadersOfAReplyWithALongBodyAreKept(int status) throws Exception {
        byte[] body = filled(JdkHttpTransport.MAX_BODY_BYTES * 4);
        WebhookUrl url = extraServer(exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "3");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        HttpReply reply = transport.postJson(url, "{}");
        assertEquals(status, reply.status());
        assertEquals("3", reply.header("retry-after").orElseThrow());
        assertEquals(JdkHttpTransport.MAX_BODY_BYTES, reply.body().length());
    }

    @Test
    void aLongBodyThatNeverEndsStillGivesItsStatusRightAway() throws Exception {
        byte[] head = filled(JdkHttpTransport.MAX_BODY_BYTES + 1);
        WebhookUrl url = extraServer(exchange -> {
            exchange.sendResponseHeaders(200, head.length * 10L);
            exchange.getResponseBody().write(head);
            exchange.getResponseBody().flush();
            awaitRelease();
            exchange.close();
        });
        JdkHttpTransport shortTransport = new JdkHttpTransport(SHORT_TIMEOUT);
        try {
            HttpReply reply = shortTransport.postJson(url, "{}");
            assertEquals(200, reply.status());
            assertEquals(JdkHttpTransport.MAX_BODY_BYTES, reply.body().length());
        } finally {
            shortTransport.close();
        }
    }

    @Test
    void aBodyOfExactlyTheLimitIsKeptWhole() throws Exception {
        byte[] body = filled(JdkHttpTransport.MAX_BODY_BYTES);
        WebhookUrl url = extraServer(exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        assertEquals(new String(body, StandardCharsets.UTF_8), transport.postJson(url, "{}").body());
    }

    @Test
    void aReplyWithoutBodyIsRead() throws Exception {
        WebhookUrl url = extraServer(exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        HttpReply reply = transport.postJson(url, "{}");
        assertEquals(204, reply.status());
        assertEquals("", reply.body());
    }

    private static byte[] filled(int length) {
        byte[] body = new byte[length];
        Arrays.fill(body, (byte) 'x');
        return body;
    }

    private WebhookUrl extraServer(HttpHandler handler) throws Exception {
        extraServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        extraServer.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            handler.handle(exchange);
        });
        extraServer.start();
        return WebhookUrl.parse(VALID, "http://127.0.0.1:" + extraServer.getAddress().getPort());
    }

    private void awaitRelease() {
        try {
            releaseBody.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
