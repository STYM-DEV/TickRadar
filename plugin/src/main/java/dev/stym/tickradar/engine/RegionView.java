package dev.stym.tickradar.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RegionView(long computedAtNanos, Optional<RegionSnapshot> global, List<RegionSnapshot> regions) {

    public static final RegionView EMPTY = new RegionView(0, Optional.empty(), List.of());

    private static final Comparator<RegionSnapshot> SLOWEST_FIRST = Comparator
            .comparing((RegionSnapshot snapshot) -> !isMeasured(snapshot))
            .thenComparing(Comparator.comparingDouble(RegionSnapshot::mspt5s).reversed())
            .thenComparingInt(snapshot -> snapshot.id().length())
            .thenComparing(RegionSnapshot::id);

    public RegionView {
        regions = List.copyOf(regions);
    }

    public static RegionView of(long computedAtNanos, RegionSnapshot global, Collection<RegionSnapshot> regions) {
        List<RegionSnapshot> sorted = new ArrayList<>(regions.size());
        for (RegionSnapshot region : regions) {
            if (region != null) {
                sorted.add(region);
            }
        }
        sorted.sort(SLOWEST_FIRST);
        return new RegionView(computedAtNanos, Optional.ofNullable(global), sorted);
    }

    public List<RegionSnapshot> globalFirst() {
        List<RegionSnapshot> all = new ArrayList<>(regions.size() + 1);
        global.ifPresent(all::add);
        all.addAll(regions);
        return all;
    }

    public Optional<RegionSnapshot> worst(long nowNanos, long maxAgeNanos) {
        for (RegionSnapshot region : regions) {
            if (isMeasured(region) && region.isFresh(nowNanos, maxAgeNanos)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    public boolean isYoungerThan(long ageNanos, long nowNanos) {
        long age = nowNanos - computedAtNanos;
        return age >= 0 && age < ageNanos;
    }

    public int count() {
        return regions.size();
    }

    private static boolean isMeasured(RegionSnapshot snapshot) {
        return !snapshot.unavailable() && ValueFormat.isUsable(snapshot.mspt5s());
    }
}
