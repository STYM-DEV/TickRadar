package dev.stym.tickradar.alert.discord;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

final class FakeTicker implements Ticker {

    interface SleepHook {
        void onSleep(long nanos) throws InterruptedException;
    }

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final List<Long> sleeps = new CopyOnWriteArrayList<>();
    private volatile SleepHook hook = nanos -> {
    };

    @Override
    public long nanoTime() {
        return now.get();
    }

    @Override
    public void sleep(long nanos) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        sleeps.add(nanos);
        hook.onSleep(nanos);
        now.addAndGet(nanos);
    }

    void advance(long nanos) {
        now.addAndGet(nanos);
    }

    void onSleep(SleepHook next) {
        hook = next;
    }

    List<Long> sleeps() {
        return List.copyOf(sleeps);
    }
}
