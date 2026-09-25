package dev.stym.tickradar.alert.discord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class CappedBodySubscriberTest {

    private final RecordingSubscription subscription = new RecordingSubscription();

    @Test
    void aShortBodyIsKeptWhole() throws Exception {
        CappedBodySubscriber subscriber = subscribed(8);
        subscriber.onNext(List.of(bytes("abc"), bytes("de")));
        subscriber.onComplete();
        assertEquals("abcde", body(subscriber));
        assertEquals(2, subscription.requested);
        assertEquals(false, subscription.cancelled);
    }

    @Test
    void aLongBodyIsCutAtTheCapacityAndTheRestIsCancelled() throws Exception {
        CappedBodySubscriber subscriber = subscribed(4);
        subscriber.onNext(List.of(bytes("ab"), bytes("cdef")));
        assertTrue(subscription.cancelled);
        assertEquals(1, subscription.requested);
        subscriber.onNext(List.of(bytes("gh")));
        subscriber.onComplete();
        assertEquals("abcd", body(subscriber));
    }

    @Test
    void anErrorBeforeTheCapacityFailsTheBody() {
        CappedBodySubscriber subscriber = subscribed(4);
        subscriber.onNext(List.of(bytes("ab")));
        subscriber.onError(new IOException("reset"));
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> subscriber.getBody().toCompletableFuture().get());
        assertEquals(IOException.class, failure.getCause().getClass());
    }

    @Test
    void anErrorAfterTheCapacityKeepsTheBody() throws Exception {
        CappedBodySubscriber subscriber = subscribed(2);
        subscriber.onNext(List.of(bytes("abc")));
        subscriber.onError(new IOException("cancelled"));
        assertArrayEquals("ab".getBytes(StandardCharsets.UTF_8), subscriber.getBody().toCompletableFuture().get());
    }

    @Test
    void aNegativeCapacityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CappedBodySubscriber(-1));
    }

    private CappedBodySubscriber subscribed(int capacity) {
        CappedBodySubscriber subscriber = new CappedBodySubscriber(capacity);
        subscriber.onSubscribe(subscription);
        return subscriber;
    }

    private static String body(CappedBodySubscriber subscriber) throws Exception {
        CompletableFuture<byte[]> body = subscriber.getBody().toCompletableFuture();
        return new String(body.get(), StandardCharsets.UTF_8);
    }

    private static ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingSubscription implements Flow.Subscription {

        private long requested;
        private boolean cancelled;

        @Override
        public void request(long n) {
            requested += n;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
