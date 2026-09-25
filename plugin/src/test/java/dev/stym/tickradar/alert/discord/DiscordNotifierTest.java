package dev.stym.tickradar.alert.discord;

import static dev.stym.tickradar.alert.discord.WebhookUrlTest.TOKEN;
import static dev.stym.tickradar.alert.discord.WebhookUrlTest.VALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.stym.tickradar.alert.AlertEvent;
import dev.stym.tickradar.alert.discord.DiscordAlert.Kind;
import dev.stym.tickradar.alert.discord.DiscordStatus.State;
import dev.stym.tickradar.alert.discord.DiscordTestResult.Outcome;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import dev.stym.tickradar.engine.AlertKind;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.BlockPosition;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscordNotifierTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long WAIT_MILLIS = 5_000;
    private static final Instant AT = Instant.parse("2026-09-24T10:00:00Z");
    private static final String OTHER_VALID = "https://discord.com/api/webhooks/876543210987654321/" + TOKEN;

    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final FakeTicker ticker = new FakeTicker();
    private final FakeTransport transport = new FakeTransport(ticker);
    private final DiscordNotifier notifier = new DiscordNotifier(capturingLogger(), () -> transport, ticker);
    private CountDownLatch windowEntered = new CountDownLatch(1);
    private volatile CountDownLatch warningGate = new CountDownLatch(0);

    @AfterEach
    void stopTheNotifier() {
        notifier.stop();
    }

    @Test
    void anAlertIsPostedFromTheDedicatedThread() {
        notifier.apply(settings());
        assertTrue(notifier.offer(alert(1)));
        awaitRequests(1);
        FakeTransport.Request request = transport.requests().getFirst();
        assertEquals(DiscordNotifier.THREAD_NAME, request.thread());
        assertTrue(request.json().contains("Region R1 in world \\\"world\\\" (3 players) is at 52.0 ms (critical)."));
        assertTrue(request.json().contains("\"allowed_mentions\":{\"parse\":[]}"));
        await(() -> notifier.status().sent() == 1);
        assertTrue(workerThread().isDaemon());
    }

    @Test
    void offeringNeverTouchesTheNetworkOnTheCallerThread() {
        notifier.apply(settings());
        transport.then(FakeTransport.blockForever());
        String caller = Thread.currentThread().getName();
        for (int i = 0; i < 100; i++) {
            assertTrue(notifier.offer(alert(i)));
        }
        awaitRequests(1);
        assertTrue(transport.requests().stream().noneMatch(request -> request.thread().equals(caller)));
        DiscordStatus status = notifier.status();
        assertTrue(status.queued() <= DiscordNotifier.QUEUE_CAPACITY);
        assertTrue(status.dropped() >= 100 - DiscordNotifier.QUEUE_CAPACITY - DiscordPayload.MAX_EMBEDS);
    }

    @Test
    void alertsWithinTwoSecondsAreGroupedInOneMessage() throws InterruptedException {
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(settings());
        notifier.offer(alert(1));
        awaitWindow();
        notifier.offer(alert(2));
        notifier.offer(alert(3));
        release.countDown();
        awaitRequests(1);
        await(() -> notifier.status().sent() == 1);
        String json = transport.requests().getFirst().json();
        assertEquals(3, occurrences(json, "\"description\""));
        assertTrue(ticker.sleeps().contains(DiscordNotifier.BATCH_WINDOW_NANOS));
        assertEquals(1, transport.requests().size());
    }

    @Test
    void anAlertAfterTheWindowGoesInANewMessage() {
        notifier.apply(settings());
        notifier.offer(alert(1));
        awaitRequests(1);
        notifier.offer(alert(2));
        awaitRequests(2);
        assertTrue(transport.requests().get(1).json().contains("Region R2 "));
        assertFalse(transport.requests().get(1).json().contains("Region R1 "));
    }

    @Test
    void aFullQueueDropsTheOldestAlertsAndCountsThem() throws InterruptedException {
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(settings());
        notifier.offer(alert(0));
        awaitWindow();
        for (int i = 1; i <= 60; i++) {
            assertTrue(notifier.offer(alert(i)));
        }
        assertEquals(DiscordNotifier.QUEUE_CAPACITY, notifier.status().queued());
        assertEquals(10, notifier.status().dropped());
        release.countDown();
        awaitRequests(6);
        await(() -> notifier.status().sent() == 6);
        String everything = String.join("\n", transport.requests().stream().map(FakeTransport.Request::json).toList());
        assertTrue(everything.contains("Region R0 "));
        for (int i = 1; i <= 10; i++) {
            assertFalse(everything.contains("Region R" + i + " "), "R" + i + " should have been dropped");
        }
        for (int i = 11; i <= 60; i++) {
            assertTrue(everything.contains("Region R" + i + " "), "R" + i + " should have been sent");
        }
        assertEquals(0, notifier.status().queued());
    }

    @Test
    void sendsAreAtLeastOneSecondApart() throws InterruptedException {
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(settings());
        notifier.offer(alert(0));
        awaitWindow();
        for (int i = 1; i < 25; i++) {
            notifier.offer(alert(i));
        }
        release.countDown();
        awaitRequests(3);
        List<FakeTransport.Request> requests = transport.requests();
        assertEquals(List.of(10, 10, 5), requests.stream().map(r -> occurrences(r.json(), "\"description\"")).toList());
        for (int i = 1; i < requests.size(); i++) {
            assertTrue(requests.get(i).at() - requests.get(i - 1).at() >= SECOND, "gap " + i);
        }
    }

    @Test
    void anExhaustedRateLimitBucketDelaysTheNextSend() {
        transport.then(FakeTransport.reply(204, Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset-After", "5.5"), ""));
        notifier.apply(settings());
        notifier.offer(alert(1));
        awaitRequests(1);
        await(() -> notifier.status().sent() == 1);
        assertEquals(State.RATE_LIMITED, notifier.status().state());
        notifier.offer(alert(2));
        awaitRequests(2);
        List<FakeTransport.Request> requests = transport.requests();
        assertTrue(requests.get(1).at() - requests.get(0).at() >= 5_500_000_000L);
    }

    @Test
    void tooManyRequestsWaitsForRetryAfterThenRetries() {
        transport.then(FakeTransport.reply(429, Map.of(), "{\"message\":\"You are being rate limited.\",\"retry_after\":3.2,"
                + "\"global\":false}"));
        notifier.apply(settings());
        notifier.offer(alert(1));
        awaitRequests(2);
        await(() -> notifier.status().sent() == 1);
        List<FakeTransport.Request> requests = transport.requests();
        assertTrue(requests.get(1).at() - requests.get(0).at() >= 3_200_000_000L);
        assertEquals(requests.get(0).json(), requests.get(1).json());
        assertEquals(0, notifier.status().failed());
        assertEquals(1, warnings("HTTP 429"));
    }

    @Test
    void tooManyRequestsCanUseTheRetryAfterHeader() {
        transport.then(FakeTransport.reply(429, Map.of("Retry-After", "7"), ""));
        notifier.apply(settings());
        notifier.offer(alert(1));
        awaitRequests(2);
        List<FakeTransport.Request> requests = transport.requests();
        assertTrue(requests.get(1).at() - requests.get(0).at() >= 7 * SECOND);
    }

    @Test
    void endlessTooManyRequestsEventuallyDropTheMessage() {
        for (int i = 0; i < DiscordNotifier.MAX_RATE_LIMITED_ATTEMPTS; i++) {
            transport.then(FakeTransport.reply(429, Map.of(), "{\"retry_after\": 3600}"));
        }
        notifier.apply(settings());
        CompletableFuture<DiscordTestResult> test = notifier.sendTest();
        DiscordTestResult result = test.join();
        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals(429, result.httpStatus());
        assertEquals(DiscordNotifier.MAX_RATE_LIMITED_ATTEMPTS, transport.requests().size());
        List<FakeTransport.Request> requests = transport.requests();
        long gap = requests.get(1).at() - requests.get(0).at();
        assertTrue(gap >= 60 * SECOND && gap < 61 * SECOND, "gap " + gap);
        assertEquals(1, notifier.status().failed());
        assertTrue(notifier.isRunning());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void aRejectedWebhookIsDisabledWithASingleWarning(int status) throws InterruptedException {
        transport.then(FakeTransport.status(status));
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(settings());
        notifier.offer(alert(0));
        awaitWindow();
        for (int i = 1; i < 15; i++) {
            notifier.offer(alert(i));
        }
        release.countDown();
        await(() -> notifier.status().state() == State.DISABLED);
        await(() -> notifier.status().dropped() == 5);
        assertEquals(1, notifier.status().failed());
        assertFalse(notifier.offer(alert(99)));
        assertFalse(notifier.offer(alert(100)));
        DiscordTestResult refused = notifier.sendTest().join();
        assertEquals(Outcome.DISABLED, refused.outcome());
        assertEquals(status, refused.httpStatus());
        assertEquals(1, transport.requests().size());
        assertEquals(1, warnings("HTTP " + status));
        assertTrue(notifier.isRunning());
    }

    @Test
    void aTestQueuedWhileTheWebhookIsRejectedReportsTheRefusal() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        transport.then(() -> {
            release.await();
            return HttpReply.of(401);
        });
        notifier.apply(settings());
        notifier.offer(alert(0));
        assertTrue(transport.entered().await(WAIT_MILLIS, TimeUnit.MILLISECONDS));
        CompletableFuture<DiscordTestResult> oldest = notifier.sendTest();
        oldest.thenRun(() -> {
            release.countDown();
            await(() -> notifier.status().state() == State.DISABLED);
        });
        for (int i = 1; i < DiscordNotifier.QUEUE_CAPACITY; i++) {
            notifier.offer(alert(i));
        }
        CountDownLatch gate = new CountDownLatch(1);
        warningGate = gate;
        CompletableFuture<DiscordTestResult> late = notifier.sendTest();
        assertTrue(late.isDone());
        gate.countDown();
        DiscordTestResult result = late.join();
        assertEquals(Outcome.DROPPED, oldest.join().outcome());
        assertEquals(Outcome.DISABLED, result.outcome());
        assertEquals(401, result.httpStatus());
    }

    @Test
    void aReloadEnablesARejectedWebhookAgain() {
        transport.then(FakeTransport.status(404));
        notifier.apply(settings());
        notifier.offer(alert(1));
        await(() -> notifier.status().state() == State.DISABLED);
        notifier.apply(settings());
        assertEquals(State.READY, notifier.status().state());
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.SENT, result.outcome());
        assertEquals(204, result.httpStatus());
    }

    @Test
    void aRefusalOfTheOldUrlDoesNotDisableTheNewOne() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        transport.then(() -> {
            release.await();
            return HttpReply.of(404);
        });
        notifier.apply(settings());
        CompletableFuture<DiscordTestResult> oldTest = notifier.sendTest();
        assertTrue(transport.entered().await(WAIT_MILLIS, TimeUnit.MILLISECONDS));
        notifier.apply(settings(OTHER_VALID));
        release.countDown();
        assertEquals(Outcome.REJECTED, oldTest.join().outcome());
        assertEquals(State.READY, notifier.status().state());
        assertEquals(0, warnings("HTTP 404"));
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.SENT, result.outcome());
        assertEquals(State.READY, notifier.status().state());
    }

    @Test
    void aRefusalOfTheCurrentUrlStillDisablesIt() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        transport.then(() -> {
            release.await();
            return HttpReply.of(401);
        });
        notifier.apply(settings());
        CompletableFuture<DiscordTestResult> test = notifier.sendTest();
        assertTrue(transport.entered().await(WAIT_MILLIS, TimeUnit.MILLISECONDS));
        notifier.apply(settings());
        release.countDown();
        assertEquals(Outcome.REJECTED, test.join().outcome());
        assertEquals(State.DISABLED, notifier.status().state());
    }

    @Test
    void anActiveTestEndpointIsReportedAtEachApplyWithoutTheToken() throws InvalidWebhookUrlException {
        DiscordSettings redirected = new DiscordSettings(WebhookUrl.parse(VALID, "http://127.0.0.1:8099"), null,
                EnumSet.allOf(AlertLevel.class), false, "TickRadar", DiscordTexts.ENGLISH);
        notifier.apply(redirected);
        notifier.apply(redirected);
        assertEquals(2, warnings("instead of Discord"));
        assertEquals(2, warnings("127.0.0.1"));
        notifier.apply(settings());
        assertEquals(2, warnings(WebhookUrl.TEST_ENDPOINT_PROPERTY));
        for (LogRecord record : logged) {
            assertFalse(record.getMessage().contains(TOKEN));
        }
    }

    @Test
    void serverErrorsAreRetriedTwiceThenDroppedWithoutKillingTheThread() {
        transport.then(FakeTransport.status(503)).then(FakeTransport.status(503)).then(FakeTransport.status(503));
        notifier.apply(settings());
        notifier.offer(alert(1));
        await(() -> notifier.status().failed() == 1);
        assertEquals(3, transport.requests().size());
        List<Long> sleeps = ticker.sleeps();
        assertTrue(sleeps.contains(2 * SECOND));
        assertTrue(sleeps.contains(4 * SECOND));
        assertEquals(1, warnings("HTTP 503"));
        assertTrue(notifier.isRunning());
        notifier.offer(alert(2));
        await(() -> notifier.status().sent() == 1);
        assertEquals(204, notifier.status().lastHttpStatus());
    }

    @Test
    void aNetworkErrorIsRetriedAndTheTestSucceeds() {
        transport.then(FakeTransport.failure(new IOException("connection reset")));
        notifier.apply(settings());
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.SENT, result.outcome());
        assertEquals(2, transport.requests().size());
        assertTrue(ticker.sleeps().contains(2 * SECOND));
        assertEquals(1, warnings("IOException"));
    }

    @Test
    void repeatedNetworkErrorsFailTheTest() {
        for (int i = 0; i < 3; i++) {
            transport.then(FakeTransport.failure(new IOException("timeout")));
        }
        notifier.apply(settings());
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals(DiscordTestResult.NO_STATUS, result.httpStatus());
        assertEquals("IOException", result.detail());
        assertTrue(notifier.isRunning());
    }

    @Test
    void timeoutsAreRetriedThenCountedOnceAsFailed() {
        for (int i = 0; i < 3; i++) {
            transport.then(FakeTransport.failure(new HttpTimeoutException("no reply")));
        }
        notifier.apply(settings());
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals("HttpTimeoutException", result.detail());
        assertEquals(3, transport.requests().size());
        assertEquals(1, notifier.status().failed());
        assertEquals(0, notifier.status().dropped());
        assertEquals(1, warnings("HttpTimeoutException"));
        assertEquals(Outcome.SENT, notifier.sendTest().join().outcome());
        assertEquals(1, notifier.status().failed());
    }

    @Test
    void aFailedMessageOfFiveAlertsCountsOnceLikeASentMessage() throws InterruptedException {
        transport.then(FakeTransport.status(503)).then(FakeTransport.status(503)).then(FakeTransport.status(503));
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(settings());
        notifier.offer(alert(0));
        awaitWindow();
        for (int i = 1; i < 5; i++) {
            notifier.offer(alert(i));
        }
        release.countDown();
        await(() -> notifier.status().failed() == 1);
        assertEquals(3, transport.requests().size());
        assertEquals(5, occurrences(transport.requests().getFirst().json(), "\"description\""));
        assertEquals(0, notifier.status().dropped());
        assertTrue(notifier.status().summary().contains(" discord_failed=1 "));
        for (int i = 5; i < 10; i++) {
            notifier.offer(alert(i));
        }
        await(() -> notifier.status().sent() == 1);
        assertEquals(1, notifier.status().failed());
    }

    @Test
    void anUnexpectedErrorCountsOneFailedMessage() {
        transport.then(FakeTransport.crash(new IllegalStateException("boom")));
        notifier.apply(settings());
        assertEquals(Outcome.FAILED, notifier.sendTest().join().outcome());
        assertEquals(1, notifier.status().failed());
    }

    @Test
    void aTimeoutFollowedByASuccessIsNotCountedAsFailed() {
        transport.then(FakeTransport.failure(new HttpTimeoutException("no reply")));
        notifier.apply(settings());
        assertEquals(Outcome.SENT, notifier.sendTest().join().outcome());
        assertEquals(0, notifier.status().failed());
        assertEquals(1, notifier.status().sent());
    }

    @Test
    void aWebhookThatNeverFinishesItsReplyIsCountedAsFailed() throws IOException {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService handlers = Executors.newCachedThreadPool();
        server.setExecutor(handlers);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().flush();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        DiscordNotifier real = new DiscordNotifier(capturingLogger(),
                () -> new JdkHttpTransport(Duration.ofMillis(300)), ticker);
        try {
            real.apply(new DiscordSettings(redirectedUrl(server), null, EnumSet.allOf(AlertLevel.class), false, "TickRadar",
                    DiscordTexts.ENGLISH));
            DiscordTestResult result = real.sendTest().orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS).join();
            assertEquals(Outcome.FAILED, result.outcome());
            assertEquals("HttpTimeoutException", result.detail());
            assertEquals(1, real.status().failed());
            assertTrue(real.isRunning());
        } finally {
            real.stop();
            release.countDown();
            server.stop(0);
            handlers.shutdownNow();
        }
    }

    @Test
    void anotherClientErrorDropsTheMessageOnce() {
        transport.then(FakeTransport.status(400));
        notifier.apply(settings());
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.FAILED, result.outcome());
        assertEquals(400, result.httpStatus());
        assertEquals(1, transport.requests().size());
        assertEquals(State.READY, notifier.status().state());
    }

    @Test
    void anUnexpectedErrorNeverKillsTheThread() {
        transport.then(FakeTransport.crash(new IllegalStateException("boom")))
                .then(FakeTransport.crash(new IllegalStateException("boom again")));
        notifier.apply(settings());
        assertEquals(Outcome.FAILED, notifier.sendTest().join().outcome());
        assertEquals(Outcome.FAILED, notifier.sendTest().join().outcome());
        assertEquals(Outcome.SENT, notifier.sendTest().join().outcome());
        assertTrue(notifier.isRunning());
        assertEquals(1, warnings("IllegalStateException"));
    }

    @Test
    void stoppingWithASlowWebhookTakesAtMostTwoSeconds() throws InterruptedException {
        transport.then(FakeTransport.blockForever());
        notifier.apply(settings());
        notifier.offer(alert(0));
        assertTrue(transport.entered().await(WAIT_MILLIS, TimeUnit.MILLISECONDS));
        for (int i = 1; i <= 20; i++) {
            notifier.offer(alert(i));
        }
        CompletableFuture<DiscordTestResult> test = notifier.sendTest();
        Thread worker = workerThread();
        long start = System.nanoTime();
        notifier.stop();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis < 2_000, "stop took " + elapsedMillis + " ms");
        assertFalse(worker.isAlive());
        assertTrue(liveWorkers().isEmpty());
        assertEquals(22, notifier.status().dropped());
        assertEquals(Outcome.STOPPED, test.join().outcome());
        assertEquals(State.STOPPED, notifier.status().state());
        assertTrue(transport.isClosed());
        assertTrue(logged.stream().anyMatch(record -> record.getMessage().contains("22 pending alert(s) dropped")));
    }

    @Test
    void stoppingWhileAReloadRetiresABlockedWorkerIsQuickAndLeavesNoThread() throws Exception {
        transport.then(FakeTransport.blockForever());
        notifier.apply(settings());
        notifier.offer(alert(0));
        assertTrue(transport.entered().await(WAIT_MILLIS, TimeUnit.MILLISECONDS));
        Thread worker = workerThread();
        Thread applier = new Thread(() -> notifier.apply(DiscordSettings.off()), "reload");
        applier.start();
        await(() -> !notifier.isRunning() && applier.getState() == Thread.State.TIMED_WAITING);
        long start = System.nanoTime();
        notifier.stop();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis < 1_000, "stop took " + elapsedMillis + " ms");
        applier.join(WAIT_MILLIS);
        assertFalse(applier.isAlive());
        assertFalse(worker.isAlive());
        assertTrue(liveWorkers().isEmpty());
        assertEquals(State.STOPPED, notifier.status().state());
        assertEquals(1, notifier.status().dropped());
        notifier.apply(settings());
        assertFalse(notifier.isRunning());
    }

    @Test
    void stoppingAnIdleNotifierIsQuickAndIdempotent() {
        notifier.apply(settings());
        Thread worker = workerThread();
        long start = System.nanoTime();
        notifier.stop();
        notifier.stop();
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1_000);
        assertFalse(worker.isAlive());
        assertFalse(notifier.offer(alert(1)));
        assertEquals(Outcome.STOPPED, notifier.sendTest().join().outcome());
        notifier.apply(settings());
        assertFalse(notifier.isRunning());
        assertTrue(liveWorkers().isEmpty());
    }

    @Test
    void stoppingBeforeAnyConfigurationStartsNothing() {
        notifier.stop();
        notifier.apply(settings());
        assertFalse(notifier.isRunning());
        assertTrue(liveWorkers().isEmpty());
        assertEquals(State.STOPPED, notifier.status().state());
    }

    @Test
    void removingTheWebhookStopsTheThreadAndAddingItStartsItAgain() {
        notifier.apply(settings());
        Thread first = workerThread();
        notifier.apply(DiscordSettings.off());
        assertFalse(first.isAlive());
        assertFalse(notifier.isRunning());
        assertEquals(State.OFF, notifier.status().state());
        assertFalse(notifier.offer(alert(1)));
        assertEquals(Outcome.NOT_CONFIGURED, notifier.sendTest().join().outcome());
        notifier.apply(settings());
        assertTrue(notifier.isRunning());
        assertEquals(Outcome.SENT, notifier.sendTest().join().outcome());
    }

    @Test
    void aTestInTheBatchWindowWhenAReloadTurnsDiscordOffIsNotConfigured() throws InterruptedException {
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(settings());
        CompletableFuture<DiscordTestResult> test = notifier.sendTest();
        awaitWindow();
        notifier.apply(DiscordSettings.off());
        release.countDown();
        assertEquals(Outcome.NOT_CONFIGURED, test.orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS).join().outcome());
        assertEquals(1, notifier.status().dropped());
        assertEquals(0, transport.requests().size());
    }

    @Test
    void aTestRacingAReloadThatTurnsDiscordOffIsNeverReportedAsStopped() throws Exception {
        ExecutorService tester = Executors.newSingleThreadExecutor();
        try {
            for (int round = 0; round < 200; round++) {
                notifier.apply(settings());
                CountDownLatch start = new CountDownLatch(1);
                CompletableFuture<Outcome> outcome = CompletableFuture.supplyAsync(() -> {
                    awaitGate(start);
                    return notifier.sendTest().orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS).join().outcome();
                }, tester);
                start.countDown();
                notifier.apply(DiscordSettings.off());
                Outcome answered = outcome.join();
                assertTrue(Set.of(Outcome.SENT, Outcome.NOT_CONFIGURED).contains(answered), "round " + round + ": " + answered);
            }
        } finally {
            tester.shutdownNow();
        }
    }

    @Test
    void anInvalidUrlIsReportedOnceWithoutTheUrl() {
        DiscordSettings invalid = DiscordSettings.from(new ConfigSnapshot.Discord("http://discord.com/api/webhooks/"
                + "123456789012345678/" + TOKEN, Set.of(AlertLevel.CRITICAL), false, "TickRadar"), "en");
        notifier.apply(invalid);
        assertFalse(notifier.isRunning());
        assertEquals(State.INVALID_URL, notifier.status().state());
        DiscordTestResult result = notifier.sendTest().join();
        assertEquals(Outcome.INVALID_URL, result.outcome());
        assertFalse(result.detail().contains(TOKEN));
        assertEquals(1, warnings("must start with https://"));
    }

    @Test
    void levelsNotConfiguredAreIgnored() {
        notifier.apply(new DiscordSettings(url(VALID), null, EnumSet.of(AlertLevel.CRITICAL, AlertLevel.RECOVERED), false,
                "TickRadar", null));
        assertFalse(notifier.offer(DiscordAlert.region(Kind.WARNING, "R1", "world", 2, 45, null, AT)));
        assertTrue(notifier.offer(DiscordAlert.region(Kind.ENDED, "R1", "world", 0, 45, null, AT)));
        assertFalse(notifier.offer(DiscordAlert.test(AT)));
    }

    @Test
    void anAlertEventIsPostedWithoutCoordinatesByDefault() {
        notifier.apply(settings());
        notifier.accept(regionEvent(AlertKind.CRITICAL));
        awaitRequests(1);
        String json = transport.requests().getFirst().json();
        assertTrue(json.contains("Region R12 in world \\\"world\\\" (5 players) is at 52.1 ms (critical)."), json);
        assertFalse(json.contains("1204"), json);
    }

    @Test
    void anAlertEventCarriesItsCoordinatesWhenTheyAreAllowed() {
        notifier.apply(new DiscordSettings(url(VALID), null, EnumSet.allOf(AlertLevel.class), true, "TickRadar", DiscordTexts.ENGLISH));
        notifier.accept(regionEvent(AlertKind.WARNING));
        awaitRequests(1);
        String json = transport.requests().getFirst().json();
        assertTrue(json.contains("Region R12 in world \\\"world\\\" around X 1204, Z -3380 (5 players) is at 52.1 ms (warning)."), json);
    }

    @Test
    void alertEventsFollowTheConfiguredLevels() {
        CountDownLatch release = holdFirstBatchWindow();
        notifier.apply(new DiscordSettings(url(VALID), null, EnumSet.of(AlertLevel.CRITICAL, AlertLevel.RECOVERED), false,
                "TickRadar", DiscordTexts.ENGLISH));
        notifier.accept(regionEvent(AlertKind.WARNING));
        notifier.accept(regionEvent(AlertKind.ENDED));
        notifier.accept(new AlertEvent(Anchor.GLOBAL_ID, AlertKind.CRITICAL, 61.5, 0, "", Optional.empty(), AT));
        release.countDown();
        awaitRequests(1);
        String json = transport.requests().getFirst().json();
        assertFalse(json.contains("(warning)"), json);
        assertTrue(json.contains("Region R12 in world \\\"world\\\" is no longer observed: no players are left there."), json);
        assertTrue(json.contains("The global region G is at 61.5 ms (critical)."), json);
    }

    @Test
    void theTestAnswersWithoutBlockingTheCaller() {
        transport.then(FakeTransport.blockForever());
        notifier.apply(settings());
        long start = System.nanoTime();
        CompletableFuture<DiscordTestResult> test = notifier.sendTest();
        assertTrue((System.nanoTime() - start) / 1_000_000 < 500);
        assertFalse(test.isDone());
        notifier.stop();
        assertEquals(Outcome.STOPPED, test.join().outcome());
    }

    @Test
    void theTestReportsTheRateLimitWait() {
        transport.then(FakeTransport.reply(204, Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset-After", "4"), ""));
        notifier.apply(settings());
        assertEquals(Outcome.SENT, notifier.sendTest().join().outcome());
        DiscordTestResult second = notifier.sendTest().join();
        assertEquals(Outcome.SENT, second.outcome());
        assertEquals(4 * SECOND - DiscordNotifier.BATCH_WINDOW_NANOS, second.rateLimitWait().toNanos());
        List<FakeTransport.Request> requests = transport.requests();
        assertTrue(requests.get(1).at() - requests.get(0).at() >= 4 * SECOND);
    }

    @Test
    void theUrlNeverLeaksInLogsOrStatus() {
        transport.then(FakeTransport.failure(new IOException("cannot reach " + VALID)))
                .then(FakeTransport.failure(new IOException("cannot reach " + VALID)))
                .then(FakeTransport.failure(new IOException("cannot reach " + VALID)))
                .then(FakeTransport.crash(new IllegalArgumentException("bad " + VALID)))
                .then(FakeTransport.status(500))
                .then(FakeTransport.status(500))
                .then(FakeTransport.status(500))
                .then(FakeTransport.status(404));
        notifier.apply(DiscordSettings.from(new ConfigSnapshot.Discord("http://discord.com/api/webhooks/123456789012345678/"
                + TOKEN, Set.of(AlertLevel.CRITICAL), false, "TickRadar"), "en"));
        notifier.apply(settings());
        notifier.sendTest().join();
        notifier.sendTest().join();
        notifier.sendTest().join();
        notifier.sendTest().join();
        assertEquals(State.DISABLED, notifier.status().state());
        notifier.stop();
        assertTrue(logged.size() >= 4, logged.stream().map(LogRecord::getMessage).toList().toString());
        for (LogRecord record : logged) {
            assertFalse(record.getMessage().contains(TOKEN), record.getMessage());
            assertTrue(record.getThrown() == null || !String.valueOf(record.getThrown()).contains(TOKEN));
        }
        assertFalse(notifier.status().toString().contains(TOKEN));
        assertFalse(notifier.status().summary().contains(TOKEN));
        assertFalse(settings().toString().contains(TOKEN));
    }

    @Test
    void theStatusSummaryHasStableKeys() {
        notifier.apply(settings());
        assertEquals("ready discord_queue=0 discord_dropped=0 discord_sent=0 discord_failed=0 discord_last_http=none",
                notifier.status().summary());
        assertEquals("https://discord.com/api/webhooks/1234.../****", notifier.status().maskedUrl());
        assertEquals("off discord_queue=0 discord_dropped=0 discord_sent=0 discord_failed=0 discord_last_http=none",
                new DiscordNotifier(capturingLogger(), () -> transport, ticker).status().summary());
    }

    private CountDownLatch holdFirstBatchWindow() {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ticker.onSleep(nanos -> {
            if (nanos == DiscordNotifier.BATCH_WINDOW_NANOS && entered.getCount() > 0) {
                entered.countDown();
                release.await();
            }
        });
        windowEntered = entered;
        return release;
    }

    private static void awaitGate(CountDownLatch gate) {
        try {
            gate.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitWindow() throws InterruptedException {
        assertTrue(windowEntered.await(WAIT_MILLIS, TimeUnit.MILLISECONDS));
    }

    private void awaitRequests(int count) {
        await(() -> transport.requests().size() >= count);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("condition not met in " + WAIT_MILLIS + " ms");
            }
            Thread.onSpinWait();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
    }

    private long warnings(String part) {
        return logged.stream()
                .filter(record -> record.getLevel() == Level.WARNING && record.getMessage().contains(part))
                .count();
    }

    private static List<Thread> liveWorkers() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(DiscordNotifier.THREAD_NAME) && thread.isAlive())
                .toList();
    }

    private static Thread workerThread() {
        await(() -> liveWorkers().size() == 1);
        return liveWorkers().getFirst();
    }

    private static int occurrences(String text, String part) {
        int count = 0;
        int index = text.indexOf(part);
        while (index >= 0) {
            count++;
            index = text.indexOf(part, index + part.length());
        }
        return count;
    }

    private static DiscordAlert alert(int index) {
        return DiscordAlert.region(Kind.CRITICAL, "R" + index, "world", 3, 52, new DiscordAlert.Coordinates(10, 20), AT);
    }

    private static AlertEvent regionEvent(AlertKind kind) {
        return new AlertEvent("R12", kind, 52.1, 5, "world", Optional.of(new BlockPosition(1204, 64, -3380)), AT);
    }

    private static WebhookUrl url(String raw) {
        try {
            return WebhookUrl.parse(raw, null);
        } catch (InvalidWebhookUrlException e) {
            throw new AssertionError(e);
        }
    }

    private static WebhookUrl redirectedUrl(HttpServer server) {
        try {
            return WebhookUrl.parse(VALID, "http://127.0.0.1:" + server.getAddress().getPort());
        } catch (InvalidWebhookUrlException e) {
            throw new AssertionError(e);
        }
    }

    private static DiscordSettings settings() {
        return settings(VALID);
    }

    private static DiscordSettings settings(String raw) {
        return new DiscordSettings(url(raw), null, EnumSet.allOf(AlertLevel.class), false, "TickRadar", DiscordTexts.ENGLISH);
    }

    private Logger capturingLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
                if (record.getLevel() == Level.WARNING) {
                    awaitGate(warningGate);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }
}
