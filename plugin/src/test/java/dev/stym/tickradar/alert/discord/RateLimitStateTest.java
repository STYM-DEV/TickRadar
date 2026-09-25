package dev.stym.tickradar.alert.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitStateTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long NOW = 50 * SECOND;

    private final RateLimitState state = new RateLimitState();

    @Test
    void theFirstSendNeverWaits() {
        assertEquals(0, state.delayBeforeSend(NOW));
    }

    @Test
    void twoSendsAreAtLeastOneSecondApart() {
        state.recordSend(NOW);
        assertEquals(SECOND, state.delayBeforeSend(NOW));
        assertEquals(SECOND / 4, state.delayBeforeSend(NOW + 3 * SECOND / 4));
        assertEquals(0, state.delayBeforeSend(NOW + SECOND));
    }

    @Test
    void anExhaustedBucketWaitsForItsReset() {
        state.recordSend(NOW);
        state.recordReply(new HttpReply(204, Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset-After", "5.5"), ""), NOW);
        assertEquals(5 * SECOND + SECOND / 2, state.delayBeforeSend(NOW));
        assertTrue(state.isLimited(NOW));
        assertFalse(state.isLimited(NOW + 6 * SECOND));
    }

    @Test
    void aBucketWithRoomOnlyKeepsTheFloor() {
        state.recordSend(NOW);
        state.recordReply(new HttpReply(204, Map.of("x-ratelimit-remaining", "4", "x-ratelimit-reset-after", "30"), ""), NOW);
        assertEquals(SECOND, state.delayBeforeSend(NOW));
        assertFalse(state.isLimited(NOW));
    }

    @Test
    void unreadableHeadersAreIgnored() {
        state.recordReply(new HttpReply(204, Map.of("X-RateLimit-Remaining", "zero", "X-RateLimit-Reset-After", "soon"), ""), NOW);
        assertEquals(0, state.delayBeforeSend(NOW));
    }

    @Test
    void tooManyRequestsReadsRetryAfterFromTheBody() {
        long wait = state.recordTooManyRequests(new HttpReply(429, Map.of("Retry-After", "9"),
                "{\"message\":\"You are being rate limited.\",\"retry_after\":3.2,\"global\":false}"), NOW);
        assertEquals(3_200_000_000L, wait);
        assertEquals(3_200_000_000L, state.delayBeforeSend(NOW));
    }

    @Test
    void tooManyRequestsFallsBackOnTheHeaders() {
        assertEquals(7 * SECOND, state.recordTooManyRequests(new HttpReply(429, Map.of("Retry-After", "7"), ""), NOW));
        RateLimitState other = new RateLimitState();
        assertEquals(2 * SECOND, other.recordTooManyRequests(
                new HttpReply(429, Map.of("X-RateLimit-Reset-After", "2"), "not json"), NOW));
        RateLimitState bare = new RateLimitState();
        assertEquals(RateLimitState.DEFAULT_RETRY_NANOS, bare.recordTooManyRequests(HttpReply.of(429), NOW));
    }

    @Test
    void waitsAreCappedAtOneMinute() {
        assertEquals(RateLimitState.MAX_WAIT_NANOS,
                state.recordTooManyRequests(new HttpReply(429, Map.of(), "{\"retry_after\": 3600}"), NOW));
    }

    @Test
    void aLaterLimitNeverShortensAnEarlierOne() {
        state.recordTooManyRequests(new HttpReply(429, Map.of(), "{\"retry_after\": 10}"), NOW);
        state.recordReply(new HttpReply(204, Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset-After", "1"), ""), NOW);
        assertEquals(10 * SECOND, state.delayBeforeSend(NOW));
    }

    @Test
    void resetForgetsEverything() {
        state.recordSend(NOW);
        state.recordTooManyRequests(new HttpReply(429, Map.of(), "{\"retry_after\": 10}"), NOW);
        state.reset();
        assertEquals(0, state.delayBeforeSend(NOW));
        assertFalse(state.isLimited(NOW));
    }
}
