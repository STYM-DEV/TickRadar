package dev.stym.tickradar.alert.discord;

import java.util.concurrent.TimeUnit;

interface Ticker {

    Ticker SYSTEM = new Ticker() {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public void sleep(long nanos) throws InterruptedException {
            TimeUnit.NANOSECONDS.sleep(nanos);
        }
    };

    long nanoTime();

    void sleep(long nanos) throws InterruptedException;
}
