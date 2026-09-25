package dev.stym.tickradar.sample;

import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class MetricsCache {

    static final long VIEW_MEMO_NANOS = 500_000_000L;

    private final AnchorTracker anchors;
    private final Anchor global;
    private final AtomicReference<RegionView> view = new AtomicReference<>();

    public MetricsCache(AnchorTracker anchors, Anchor global) {
        this.anchors = anchors;
        this.global = global;
    }

    public Optional<RegionSnapshot> region(String anchorId) {
        if (Anchor.GLOBAL_ID.equals(anchorId)) {
            return global();
        }
        return anchors.find(anchorId).map(Anchor::snapshot);
    }

    public Optional<RegionSnapshot> regionOf(UUID observer) {
        return anchors.anchorOf(observer).map(Anchor::snapshot);
    }

    public Optional<RegionSnapshot> global() {
        return Optional.ofNullable(global.snapshot());
    }

    public int regionCount() {
        return anchors.size();
    }

    public int activeAlerts() {
        int active = global.alertState().isActive() ? 1 : 0;
        for (Anchor anchor : anchors.anchors()) {
            if (anchor.alertState().isActive()) {
                active++;
            }
        }
        return active;
    }

    public List<String> regionIds() {
        List<String> ids = new ArrayList<>();
        for (Anchor anchor : anchors.anchors()) {
            ids.add(anchor.id());
        }
        return ids;
    }

    public RegionView view(long nowNanos) {
        RegionView memo = view.get();
        if (memo != null && memo.isYoungerThan(VIEW_MEMO_NANOS, nowNanos)) {
            return memo;
        }
        RegionView fresh = compute(nowNanos);
        view.set(fresh);
        return fresh;
    }

    private RegionView compute(long nowNanos) {
        List<RegionSnapshot> regions = new ArrayList<>();
        for (Anchor anchor : anchors.anchors()) {
            RegionSnapshot snapshot = anchor.snapshot();
            if (snapshot != null) {
                regions.add(snapshot);
            }
        }
        return RegionView.of(nowNanos, global.snapshot(), regions);
    }
}
