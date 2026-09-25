package dev.stym.tickradar.player;

import java.util.function.Predicate;

public final class PermissionCache {

    public static final int SAMPLES_PER_ROUND = Permissions.ALL.size();

    private final Predicate<String> hasPermission;
    private Permissions current;
    private int next;

    public PermissionCache(Predicate<String> hasPermission) {
        this.hasPermission = hasPermission;
    }

    public Permissions next() {
        if (current == null) {
            return refresh();
        }
        current = current.with(next, hasPermission.test(Permissions.ALL.get(next)));
        next = (next + 1) % SAMPLES_PER_ROUND;
        return current;
    }

    public Permissions refresh() {
        Permissions read = Permissions.read(hasPermission);
        if (!read.equals(current)) {
            current = read;
        }
        next = 0;
        return current;
    }
}
