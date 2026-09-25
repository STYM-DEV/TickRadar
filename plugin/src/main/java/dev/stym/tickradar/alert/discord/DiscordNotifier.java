package dev.stym.tickradar.alert.discord;

import dev.stym.tickradar.alert.AlertEvent;
import dev.stym.tickradar.alert.AlertSink;
import dev.stym.tickradar.alert.discord.DiscordStatus.State;
import dev.stym.tickradar.alert.discord.DiscordTestResult.Outcome;
import dev.stym.tickradar.engine.WarnOnce;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class DiscordNotifier implements AlertSink {

    public static final String THREAD_NAME = "TickRadar-Discord";
    public static final int QUEUE_CAPACITY = 50;
    static final long BATCH_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(2);
    static final long FIRST_RETRY_NANOS = TimeUnit.SECONDS.toNanos(2);
    static final int MAX_RETRIES = 2;
    static final int MAX_RATE_LIMITED_ATTEMPTS = 5;
    static final long GRACE_MILLIS = 1_500;
    static final long INTERRUPT_MILLIS = 450;

    private final Logger logger;
    private final Supplier<HttpTransport> transports;
    private final Ticker ticker;
    private final ArrayBlockingQueue<Pending> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final RateLimitState rateLimit = new RateLimitState();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final Object lifecycle = new Object();
    private volatile DiscordSettings settings = DiscordSettings.off();
    private volatile WarnOnce warnings = new WarnOnce();
    private volatile Rejection rejection;
    private volatile int lastHttpStatus = DiscordTestResult.NO_STATUS;
    private volatile boolean stopped;
    private boolean shutDown;
    private volatile Worker worker;
    private volatile Worker retiring;

    public DiscordNotifier(Logger logger) {
        this(logger, JdkHttpTransport::new, Ticker.SYSTEM);
    }

    DiscordNotifier(Logger logger, Supplier<HttpTransport> transports, Ticker ticker) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.transports = Objects.requireNonNull(transports, "transports");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    public void apply(DiscordSettings next) {
        Objects.requireNonNull(next, "settings");
        synchronized (lifecycle) {
            if (stopped) {
                return;
            }
            if (!Objects.equals(settings.webhook(), next.webhook())) {
                rateLimit.reset();
            }
            warnings = new WarnOnce();
            rejection = null;
            if (next.problem() != null) {
                logger.warning("Discord alerts are off: " + next.problem()
                        + ". Fix discord.webhook-url in config.yml, then run /tickradar reload.");
            }
            if (next.isUsable() && next.webhook().endpointNotice() != null) {
                logger.warning(next.webhook().endpointNotice());
            }
            if (next.isUsable()) {
                settings = next;
                if (worker == null) {
                    worker = startWorker();
                }
            } else {
                Worker previous = worker;
                settings = next;
                worker = null;
                retire(previous);
                abandonQueue(DiscordTestResult.of(Outcome.NOT_CONFIGURED));
            }
        }
    }

    @Override
    public void accept(AlertEvent event) {
        offer(DiscordAlert.from(event, settings.includeCoordinates()));
    }

    public boolean offer(DiscordAlert alert) {
        Objects.requireNonNull(alert, "alert");
        if (alert.kind() == DiscordAlert.Kind.TEST || !settings.accepts(alert.kind())) {
            return false;
        }
        return enqueue(new Pending(alert, null));
    }

    public CompletableFuture<DiscordTestResult> sendTest() {
        DiscordTestResult refusal = testRefusal();
        if (refusal != null) {
            return CompletableFuture.completedFuture(refusal);
        }
        Pending test = new Pending(DiscordAlert.test(Instant.now()), new CompletableFuture<>());
        if (!enqueue(test)) {
            test.finish(refusalOrStopped());
        }
        return test.result();
    }

    public DiscordStatus status() {
        DiscordSettings current = settings;
        return new DiscordStatus(state(current), queue.size(), dropped.get(), sent.get(), failed.get(), lastHttpStatus,
                current.maskedUrl());
    }

    public boolean isRunning() {
        Worker current = worker;
        return current != null && current.thread.isAlive();
    }

    public void stop() {
        stopped = true;
        interrupt(retiring);
        synchronized (lifecycle) {
            if (shutDown) {
                return;
            }
            shutDown = true;
            long before = dropped.get();
            Worker previous = worker;
            worker = null;
            stopWorker(previous);
            abandonQueue(DiscordTestResult.of(Outcome.STOPPED));
            long abandoned = dropped.get() - before;
            if (abandoned > 0) {
                logger.info("Discord: " + abandoned + " pending alert(s) dropped at shutdown.");
            }
        }
    }

    private State state(DiscordSettings current) {
        if (stopped) {
            return State.STOPPED;
        }
        if (!current.isConfigured()) {
            return State.OFF;
        }
        if (!current.isUsable()) {
            return State.INVALID_URL;
        }
        if (rejectedStatus(current) != 0) {
            return State.DISABLED;
        }
        return rateLimit.isLimited(ticker.nanoTime()) ? State.RATE_LIMITED : State.READY;
    }

    private DiscordTestResult testRefusal() {
        DiscordSettings current = settings;
        if (stopped) {
            return DiscordTestResult.of(Outcome.STOPPED);
        }
        if (!current.isUsable()) {
            return unusable(current);
        }
        int rejected = rejectedStatus(current);
        if (rejected != 0) {
            return DiscordTestResult.of(Outcome.DISABLED, rejected, "HTTP " + rejected);
        }
        return null;
    }

    private static DiscordTestResult unusable(DiscordSettings current) {
        if (!current.isConfigured()) {
            return DiscordTestResult.of(Outcome.NOT_CONFIGURED);
        }
        return DiscordTestResult.of(Outcome.INVALID_URL, DiscordTestResult.NO_STATUS, current.problem());
    }

    private DiscordTestResult refusalOrStopped() {
        DiscordTestResult refusal = testRefusal();
        return refusal != null ? refusal : DiscordTestResult.of(Outcome.STOPPED);
    }

    private boolean isAccepting() {
        DiscordSettings current = settings;
        return !stopped && worker != null && rejectedStatus(current) == 0 && current.isUsable();
    }

    private int rejectedStatus(DiscordSettings current) {
        Rejection refused = rejection;
        return refused != null && refused.concerns(current) ? refused.status() : 0;
    }

    private boolean enqueue(Pending pending) {
        if (!isAccepting()) {
            return false;
        }
        while (!queue.offer(pending)) {
            Pending oldest = queue.poll();
            if (oldest != null) {
                dropped.incrementAndGet();
                oldest.finish(DiscordTestResult.of(Outcome.DROPPED));
            }
        }
        if (!isAccepting()) {
            abandonQueue(refusalOrStopped());
        }
        return true;
    }

    private void abandonQueue(DiscordTestResult result) {
        Pending pending;
        while ((pending = queue.poll()) != null) {
            dropped.incrementAndGet();
            pending.finish(result);
        }
    }

    private Worker startWorker() {
        Worker started = new Worker();
        started.thread.start();
        return started;
    }

    private void retire(Worker target) {
        retiring = target;
        if (stopped) {
            interrupt(target);
        }
        try {
            stopWorker(target);
        } finally {
            retiring = null;
        }
    }

    private static void interrupt(Worker target) {
        if (target != null) {
            target.running = false;
            target.thread.interrupt();
        }
    }

    private void stopWorker(Worker target) {
        if (target == null) {
            return;
        }
        target.running = false;
        if (!target.sending) {
            target.thread.interrupt();
        }
        if (Thread.currentThread() == target.thread) {
            return;
        }
        join(target.thread, GRACE_MILLIS);
        if (target.thread.isAlive()) {
            target.thread.interrupt();
            join(target.thread, INTERRUPT_MILLIS);
        }
        if (target.thread.isAlive()) {
            target.closeTransport();
        }
    }

    private static void join(Thread thread, long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void warnOnce(String key, String message) {
        if (warnings.firstTime(key)) {
            logger.warning(message);
        }
    }

    private record Rejection(WebhookUrl webhook, int status) {

        boolean concerns(DiscordSettings current) {
            return webhook.equals(current.webhook());
        }
    }

    private record Pending(DiscordAlert alert, CompletableFuture<DiscordTestResult> result) {

        void finish(DiscordTestResult outcome) {
            if (result != null) {
                result.complete(outcome);
            }
        }
    }

    private record Attempt(HttpReply reply, String error) {

        int status() {
            return reply == null ? DiscordTestResult.NO_STATUS : reply.status();
        }

        String problem() {
            return reply == null ? error : "HTTP " + reply.status();
        }
    }

    private final class Worker implements Runnable {

        private final Thread thread;
        private final List<Pending> carry = new ArrayList<>();
        private volatile boolean running = true;
        private volatile boolean sending;
        private volatile HttpTransport transport;

        private Worker() {
            thread = new Thread(this, THREAD_NAME);
            thread.setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (running) {
                    runCycleSafely();
                }
            } finally {
                dropped.addAndGet(carry.size());
                finishAll(carry, refusalOrStopped());
                carry.clear();
                closeTransport();
            }
        }

        private void runCycleSafely() {
            try {
                cycle();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable error) {
                reportUnexpected(error);
                if (!carry.isEmpty()) {
                    failed.incrementAndGet();
                }
                finishAll(carry, DiscordTestResult.of(Outcome.FAILED, DiscordTestResult.NO_STATUS,
                        error.getClass().getSimpleName()));
                carry.clear();
            }
        }

        private void cycle() throws InterruptedException {
            if (carry.isEmpty()) {
                carry.add(queue.take());
                ticker.sleep(BATCH_WINDOW_NANOS);
            }
            if (carry.size() < DiscordPayload.MAX_EMBEDS) {
                queue.drainTo(carry, DiscordPayload.MAX_EMBEDS - carry.size());
            }
            DiscordSettings current = settings;
            int count = DiscordPayload.fittingCount(alerts(carry), current);
            List<Pending> batch = List.copyOf(carry.subList(0, count));
            deliver(batch, current);
            carry.subList(0, count).clear();
        }

        private void deliver(List<Pending> batch, DiscordSettings current) throws InterruptedException {
            if (!current.isUsable()) {
                dropped.addAndGet(batch.size());
                finishAll(batch, unusable(current));
                return;
            }
            int rejected = rejectedStatus(current);
            if (rejected != 0) {
                dropped.addAndGet(batch.size());
                finishAll(batch, DiscordTestResult.of(Outcome.DISABLED, rejected, ""));
                return;
            }
            String json = DiscordPayload.json(alerts(batch), current);
            long waited = 0;
            int failures = 0;
            int limited = 0;
            while (true) {
                waited += awaitRateLimit();
                Attempt attempt = post(current.webhook(), json);
                HttpReply reply = attempt.reply();
                long now = ticker.nanoTime();
                if (reply != null) {
                    lastHttpStatus = reply.status();
                    rateLimit.recordReply(reply, now);
                }
                if (reply != null && reply.isSuccess()) {
                    sent.incrementAndGet();
                    finishAll(batch, DiscordTestResult.sent(reply.status(), Duration.ofNanos(waited)));
                    return;
                }
                if (reply != null && reply.status() == 429) {
                    rateLimit.recordTooManyRequests(reply, now);
                    warnOnce("http-429", "Discord asked TickRadar to slow down (HTTP 429): alerts now wait as long as"
                            + " Discord requires. Shown once until /tickradar reload.");
                    if (++limited < MAX_RATE_LIMITED_ATTEMPTS) {
                        continue;
                    }
                    giveUp(batch, attempt);
                    return;
                }
                if (reply != null && isRejection(reply.status())) {
                    reject(batch, current.webhook(), reply.status());
                    return;
                }
                if (reply == null || reply.status() >= 500) {
                    warnOnce("retry-" + attempt.problem(), "Discord alert not delivered (" + attempt.problem()
                            + "): TickRadar retries twice, then drops it. Shown once per error type.");
                    if (failures < MAX_RETRIES) {
                        pause(FIRST_RETRY_NANOS << failures);
                        failures++;
                        continue;
                    }
                    giveUp(batch, attempt);
                    return;
                }
                warnOnce("http-" + reply.status(), "Discord refused an alert (HTTP " + reply.status()
                        + "): it was dropped. Shown once per status.");
                giveUp(batch, attempt);
                return;
            }
        }

        private long awaitRateLimit() throws InterruptedException {
            long total = 0;
            while (true) {
                long delay = rateLimit.delayBeforeSend(ticker.nanoTime());
                if (delay <= 0) {
                    return total;
                }
                pause(delay);
                total += delay;
            }
        }

        private void pause(long nanos) throws InterruptedException {
            if (!running) {
                throw new InterruptedException();
            }
            ticker.sleep(nanos);
        }

        private Attempt post(WebhookUrl url, String json) throws InterruptedException {
            sending = true;
            try {
                if (!running) {
                    throw new InterruptedException();
                }
                rateLimit.recordSend(ticker.nanoTime());
                return new Attempt(transport().postJson(url, json), null);
            } catch (IOException e) {
                return new Attempt(null, e.getClass().getSimpleName());
            } finally {
                sending = false;
            }
        }

        private void reject(List<Pending> batch, WebhookUrl refused, int status) {
            rejection = new Rejection(refused, status);
            failed.incrementAndGet();
            finishAll(batch, DiscordTestResult.of(Outcome.REJECTED, status, "HTTP " + status));
            if (rejectedStatus(settings) == 0) {
                return;
            }
            warnOnce("rejected", "Discord refused the webhook (HTTP " + status + "): Discord alerts are disabled until"
                    + " /tickradar reload. Check discord.webhook-url in config.yml (the webhook may have been deleted).");
            abandonQueue(DiscordTestResult.of(Outcome.DISABLED, status, "HTTP " + status));
        }

        private void giveUp(List<Pending> batch, Attempt attempt) {
            failed.incrementAndGet();
            finishAll(batch, DiscordTestResult.of(Outcome.FAILED, attempt.status(), attempt.problem()));
        }

        private void reportUnexpected(Throwable error) {
            try {
                WebhookUrl webhook = settings.webhook();
                String message = webhook == null ? "" : webhook.redact(error.getMessage());
                warnOnce("unexpected-" + error.getClass().getName(), "Unexpected error in the Discord thread: "
                        + error.getClass().getName() + (message.isEmpty() ? "" : ": " + message)
                        + " (reported once per type; the Discord thread keeps running)");
            } catch (Throwable ignored) {
            }
        }

        private HttpTransport transport() {
            HttpTransport current = transport;
            if (current == null) {
                current = transports.get();
                transport = current;
            }
            return current;
        }

        private void closeTransport() {
            HttpTransport current = transport;
            if (current == null) {
                return;
            }
            try {
                current.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isRejection(int status) {
        return status == 401 || status == 403 || status == 404;
    }

    private static List<DiscordAlert> alerts(List<Pending> pending) {
        List<DiscordAlert> alerts = new ArrayList<>(pending.size());
        for (Pending item : pending) {
            alerts.add(item.alert());
        }
        return alerts;
    }

    private static void finishAll(List<Pending> pending, DiscordTestResult result) {
        for (Pending item : pending) {
            item.finish(result);
        }
    }
}
