package dev.stym.tickradar.command;

import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

record TeleportTarget(Outcome outcome, String id, String world, BlockPosition position) {

    enum Outcome {
        FOUND,
        UNKNOWN,
        GLOBAL
    }

    static TeleportTarget resolve(String input, Function<String, Optional<RegionSnapshot>> regions) {
        String id = input.trim().toUpperCase(Locale.ROOT);
        if (Anchor.GLOBAL_ID.equals(id)) {
            return new TeleportTarget(Outcome.GLOBAL, id, null, null);
        }
        Optional<RegionSnapshot> snapshot = regions.apply(id);
        if (snapshot.isEmpty() || snapshot.get().position().isEmpty() || snapshot.get().world().isEmpty()) {
            return new TeleportTarget(Outcome.UNKNOWN, id, null, null);
        }
        return new TeleportTarget(Outcome.FOUND, id, snapshot.get().world(), snapshot.get().position().get());
    }
}
