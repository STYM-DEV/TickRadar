package dev.stym.tickradar.engine;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class Anchor {

    public static final String GLOBAL_ID = "G";
    private static final String REGION_PREFIX = "R";
    private static final long MEASURE_DUE_PERCENT = 90;

    private final long number;
    private final String id;
    private final String world;
    private final AtomicBoolean dead = new AtomicBoolean();
    private final ConcurrentHashMap<UUID, Observer> observers = new ConcurrentHashMap<>();
    private final AtomicReference<Published> published = new AtomicReference<>(Published.NOTHING);
    private final AtomicReference<AlertState> alert = new AtomicReference<>(AlertState.INITIAL);
    private volatile long chunk;
    private volatile long lastObservedNanos;
    private volatile long lastMeasureNanos;
    private volatile boolean measured;
    private SampleWindow window;
    private boolean tpsReadClaimed;
    private long tpsReadClaimNanos;
    private long countedTick = Long.MIN_VALUE;
    private int samplesOnCountedTick;

    private record Observer(boolean player, long lastSeenNanos) {
    }

    private record Published(RegionSnapshot snapshot, TpsReading tps) {

        static final Published NOTHING = new Published(null, null);

        Published withTps(TpsReading reading) {
            if (!reading.isNotOlderThan(tps)) {
                return this;
            }
            return new Published(snapshot == null ? null : snapshot.withTps(reading), reading);
        }
    }

    private Anchor(long number, String id, String world, int chunkX, int chunkZ, long nowNanos) {
        this.number = number;
        this.id = id;
        this.world = world;
        this.chunk = pack(chunkX, chunkZ);
        this.lastObservedNanos = nowNanos;
    }

    static Anchor region(long number, String world, int chunkX, int chunkZ, long nowNanos) {
        return new Anchor(number, REGION_PREFIX + number, world, chunkX, chunkZ, nowNanos);
    }

    public static Anchor global(long nowNanos) {
        return new Anchor(0, GLOBAL_ID, "", 0, 0, nowNanos);
    }

    public long number() {
        return number;
    }

    public String id() {
        return id;
    }

    public String world() {
        return world;
    }

    public int chunkX() {
        return (int) (chunk >> 32);
    }

    public int chunkZ() {
        return (int) chunk;
    }

    public ChunkPos chunk() {
        long packed = chunk;
        return new ChunkPos((int) (packed >> 32), (int) packed);
    }

    public boolean isDead() {
        return dead.get();
    }

    public RegionSnapshot snapshot() {
        return published.get().snapshot();
    }

    public Optional<TpsReading> tps() {
        return Optional.ofNullable(published.get().tps());
    }

    public synchronized boolean claimTpsRead(long nowNanos, long periodNanos) {
        if (tpsReadClaimed && nowNanos - tpsReadClaimNanos < periodNanos) {
            return false;
        }
        tpsReadClaimed = true;
        tpsReadClaimNanos = nowNanos;
        return true;
    }

    public synchronized int countSampleOnTick(long regionTick) {
        if (regionTick != countedTick) {
            countedTick = regionTick;
            samplesOnCountedTick = 0;
        }
        return ++samplesOnCountedTick;
    }

    public boolean publishTps(TpsReading reading) {
        if (!reading.isValid() || dead.get()) {
            return false;
        }
        return published.updateAndGet(current -> current.withTps(reading)).tps() == reading;
    }

    public int players(long nowNanos, long freshNanos) {
        int players = 0;
        for (Observer observer : observers.values()) {
            if (observer.player() && nowNanos - observer.lastSeenNanos() <= freshNanos) {
                players++;
            }
        }
        return players;
    }

    public boolean isMeasureDue(long nowNanos, long intervalNanos) {
        return !measured || nowNanos - lastMeasureNanos >= intervalNanos * MEASURE_DUE_PERCENT / 100;
    }

    public synchronized RegionSnapshot record(double mspt5s, BlockPosition position, int players, long nowNanos, int windowCapacity) {
        SampleWindow samples = window(windowCapacity);
        samples.add(mspt5s);
        return publish(position, players, mspt5s, samples.stats(), nowNanos, false);
    }

    public synchronized RegionSnapshot recordUnavailable(BlockPosition position, int players, long nowNanos, int windowCapacity) {
        return publish(position, players, Double.NaN, window(windowCapacity).stats(), nowNanos, true);
    }

    public AlertState alertState() {
        return alert.get();
    }

    public Optional<AlertKind> advanceAlert(double mspt5s, AlertRules rules, long nowNanos) {
        if (AlertMachine.isSteady(alert.get(), mspt5s, rules)) {
            return Optional.empty();
        }
        return advance(state -> AlertMachine.sample(state, mspt5s, rules, nowNanos));
    }

    public Optional<AlertKind> endAlert() {
        return advance(AlertMachine::end);
    }

    public void resetAlert() {
        if (!alert.get().equals(AlertState.INITIAL)) {
            advance(AlertMachine::reset);
        }
    }

    void absorbAlert(Anchor other) {
        AlertState absorbed = other.alert.get();
        alert.updateAndGet(state -> AlertMachine.merge(state, absorbed));
    }

    void moveTo(int chunkX, int chunkZ) {
        chunk = pack(chunkX, chunkZ);
    }

    boolean observe(UUID observer, boolean player, long nowNanos) {
        observers.put(observer, new Observer(player, nowNanos));
        lastObservedNanos = nowNanos;
        return !dead.get();
    }

    void forget(UUID observer) {
        observers.remove(observer);
    }

    boolean kill() {
        return dead.compareAndSet(false, true);
    }

    boolean expireIfAbandoned(long nowNanos, long emptyNanos, long staleNanos) {
        observers.entrySet().removeIf(entry -> nowNanos - entry.getValue().lastSeenNanos() > staleNanos);
        if (!observers.isEmpty() || nowNanos - lastObservedNanos <= emptyNanos) {
            return false;
        }
        return kill();
    }

    private Optional<AlertKind> advance(Function<AlertState, AlertMachine.Step> transition) {
        while (true) {
            AlertState current = alert.get();
            AlertMachine.Step step = transition.apply(current);
            if (alert.compareAndSet(current, step.state())) {
                return step.event();
            }
        }
    }

    private SampleWindow window(int capacity) {
        if (window == null) {
            window = new SampleWindow(capacity);
        } else {
            window.resize(capacity);
        }
        return window;
    }

    private RegionSnapshot publish(BlockPosition position, int players, double mspt5s, RegionStats history, long nowNanos,
                                   boolean unavailable) {
        Optional<BlockPosition> at = Optional.ofNullable(position);
        while (true) {
            Published current = published.get();
            TpsReading tps = current.tps();
            double tps5s = tps == null ? Double.NaN : tps.tps5s();
            double tps1m = tps == null ? Double.NaN : tps.tps1m();
            RegionSnapshot snapshot = new RegionSnapshot(id, world, at, players, tps5s, tps1m, mspt5s, history, nowNanos,
                    unavailable);
            if (published.compareAndSet(current, new Published(snapshot, tps))) {
                lastMeasureNanos = nowNanos;
                measured = true;
                return snapshot;
            }
        }
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
