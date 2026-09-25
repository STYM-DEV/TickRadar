package dev.stym.tickradar.alert.discord;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class CappedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    private final int capacity;
    private final ByteArrayOutputStream kept;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    CappedBodySubscriber(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative: " + capacity);
        }
        this.capacity = capacity;
        this.kept = new ByteArrayOutputStream(Math.min(capacity, 1024));
    }

    static HttpResponse.BodyHandler<byte[]> handler(int capacity) {
        return responseInfo -> new CappedBodySubscriber(capacity);
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) {
            return;
        }
        for (ByteBuffer buffer : buffers) {
            keep(buffer);
        }
        if (kept.size() >= capacity) {
            finish();
            subscription.cancel();
            return;
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        body.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
        finish();
    }

    private void keep(ByteBuffer buffer) {
        int room = capacity - kept.size();
        int length = Math.min(room, buffer.remaining());
        if (length <= 0) {
            return;
        }
        byte[] chunk = new byte[length];
        buffer.get(chunk);
        kept.write(chunk, 0, length);
    }

    private void finish() {
        body.complete(kept.toByteArray());
    }
}
