package dev.stym.tickradar.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class AnchorTracker {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Anchor>> byWorld = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Anchor> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Anchor> current = new ConcurrentHashMap<>();
    private final AtomicLong nextNumber = new AtomicLong(1);

    public Attachment attach(UUID observer, boolean player, String world, int chunkX, int chunkZ, long nowNanos,
                             long intervalNanos, OwnershipProbe probe) {
        Attachment kept = keepKnownAnchor(observer, player, world, chunkX, chunkZ, nowNanos, intervalNanos, probe);
        if (kept != null) {
            return kept;
        }
        CopyOnWriteArrayList<Anchor> anchors = byWorld.computeIfAbsent(world, key -> new CopyOnWriteArrayList<>());
        List<Anchor> merged = new ArrayList<>();
        while (true) {
            Anchor chosen = ownedAnchor(anchors, probe, merged);
            if (chosen == null) {
                chosen = create(anchors, world, chunkX, chunkZ, nowNanos);
            }
            chosen.moveTo(chunkX, chunkZ);
            if (chosen.observe(observer, player, nowNanos)) {
                if (current.get(observer) != chosen) {
                    Anchor previous = current.put(observer, chosen);
                    if (previous != null && previous != chosen) {
                        previous.forget(observer);
                    }
                }
                return new Attachment(chosen, chosen.isMeasureDue(nowNanos, intervalNanos), merged);
            }
            chosen.forget(observer);
        }
    }

    public void detach(UUID observer) {
        Anchor anchor = current.remove(observer);
        if (anchor != null) {
            anchor.forget(observer);
        }
    }

    public List<Anchor> sweep(long nowNanos, long emptyNanos, long staleNanos) {
        List<Anchor> expired = new ArrayList<>();
        for (CopyOnWriteArrayList<Anchor> anchors : byWorld.values()) {
            for (Anchor anchor : anchors) {
                if (anchor.expireIfAbandoned(nowNanos, emptyNanos, staleNanos)) {
                    remove(anchors, anchor);
                    expired.add(anchor);
                }
            }
        }
        return expired;
    }

    public List<Anchor> anchors() {
        List<Anchor> live = new ArrayList<>();
        for (CopyOnWriteArrayList<Anchor> anchors : byWorld.values()) {
            for (Anchor anchor : anchors) {
                if (!anchor.isDead()) {
                    live.add(anchor);
                }
            }
        }
        return live;
    }

    public Optional<Anchor> find(String id) {
        return Optional.ofNullable(byId.get(id)).filter(anchor -> !anchor.isDead());
    }

    public Optional<Anchor> anchorOf(UUID observer) {
        return Optional.ofNullable(current.get(observer)).filter(anchor -> !anchor.isDead());
    }

    public int size() {
        return byId.size();
    }

    private Attachment keepKnownAnchor(UUID observer, boolean player, String world, int chunkX, int chunkZ, long nowNanos,
                                      long intervalNanos, OwnershipProbe probe) {
        Anchor known = current.get(observer);
        if (known == null || known.isDead() || !known.world().equals(world) || known.isMeasureDue(nowNanos, intervalNanos)
                || !probe.ownsChunk(known.chunkX(), known.chunkZ())) {
            return null;
        }
        known.moveTo(chunkX, chunkZ);
        if (known.observe(observer, player, nowNanos)) {
            return new Attachment(known, false, List.of());
        }
        known.forget(observer);
        return null;
    }

    private Anchor ownedAnchor(List<Anchor> anchors, OwnershipProbe probe, List<Anchor> merged) {
        Anchor oldest = null;
        List<Anchor> others = null;
        for (Anchor anchor : anchors) {
            if (anchor.isDead() || !probe.ownsChunk(anchor.chunkX(), anchor.chunkZ())) {
                continue;
            }
            if (oldest == null) {
                oldest = anchor;
                continue;
            }
            if (others == null) {
                others = new ArrayList<>(1);
            }
            if (anchor.number() < oldest.number()) {
                others.add(oldest);
                oldest = anchor;
            } else {
                others.add(anchor);
            }
        }
        if (others != null) {
            absorb(anchors, oldest, others, merged);
        }
        return oldest;
    }

    private void absorb(List<Anchor> anchors, Anchor oldest, List<Anchor> others, List<Anchor> merged) {
        for (Anchor other : others) {
            if (other.kill()) {
                oldest.absorbAlert(other);
                remove(anchors, other);
                merged.add(other);
            }
        }
    }

    private Anchor create(List<Anchor> anchors, String world, int chunkX, int chunkZ, long nowNanos) {
        Anchor anchor = Anchor.region(nextNumber.getAndIncrement(), world, chunkX, chunkZ, nowNanos);
        byId.put(anchor.id(), anchor);
        anchors.add(anchor);
        return anchor;
    }

    private void remove(List<Anchor> anchors, Anchor anchor) {
        anchors.remove(anchor);
        byId.remove(anchor.id(), anchor);
    }
}
