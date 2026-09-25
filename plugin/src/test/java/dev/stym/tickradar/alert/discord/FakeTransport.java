package dev.stym.tickradar.alert.discord;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

final class FakeTransport implements HttpTransport {

    interface Step {
        HttpReply run() throws IOException, InterruptedException;
    }

    record Request(String json, String thread, long at) {
    }

    static final Step NO_CONTENT = () -> HttpReply.of(204);

    private final FakeTicker ticker;
    private final ConcurrentLinkedQueue<Step> script = new ConcurrentLinkedQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();

    FakeTransport(FakeTicker ticker) {
        this.ticker = ticker;
    }

    static Step reply(int status, Map<String, String> headers, String body) {
        return () -> new HttpReply(status, headers, body);
    }

    static Step status(int status) {
        return () -> HttpReply.of(status);
    }

    static Step failure(IOException error) {
        return () -> {
            throw error;
        };
    }

    static Step crash(RuntimeException error) {
        return () -> {
            throw error;
        };
    }

    static Step blockForever() {
        return () -> {
            Thread.sleep(60_000);
            return HttpReply.of(204);
        };
    }

    FakeTransport then(Step step) {
        script.add(step);
        return this;
    }

    @Override
    public HttpReply postJson(WebhookUrl url, String json) throws IOException, InterruptedException {
        requests.add(new Request(json, Thread.currentThread().getName(), ticker.nanoTime()));
        entered.countDown();
        Step step = script.poll();
        return (step == null ? NO_CONTENT : step).run();
    }

    @Override
    public void close() {
        closed.set(true);
    }

    List<Request> requests() {
        return List.copyOf(requests);
    }

    CountDownLatch entered() {
        return entered;
    }

    boolean isClosed() {
        return closed.get();
    }
}
