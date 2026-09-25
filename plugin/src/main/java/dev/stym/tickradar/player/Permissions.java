package dev.stym.tickradar.player;

import java.util.List;
import java.util.function.Predicate;

public record Permissions(boolean display, boolean alerts, boolean teleport) {

    public static final String DISPLAY = "tickradar.display";
    public static final String ALERTS = "tickradar.alerts";
    public static final String TELEPORT = "tickradar.admin.teleport";
    public static final List<String> ALL = List.of(DISPLAY, ALERTS, TELEPORT);

    public static Permissions read(Predicate<String> hasPermission) {
        return new Permissions(hasPermission.test(DISPLAY), hasPermission.test(ALERTS), hasPermission.test(TELEPORT));
    }

    public boolean has(int index) {
        return switch (index) {
            case 0 -> display;
            case 1 -> alerts;
            case 2 -> teleport;
            default -> throw new IllegalArgumentException("No permission at " + index);
        };
    }

    public Permissions with(int index, boolean granted) {
        if (has(index) == granted) {
            return this;
        }
        return switch (index) {
            case 0 -> new Permissions(granted, alerts, teleport);
            case 1 -> new Permissions(display, granted, teleport);
            case 2 -> new Permissions(display, alerts, granted);
            default -> throw new IllegalArgumentException("No permission at " + index);
        };
    }
}
