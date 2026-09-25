package dev.stym.tickradar.player;

import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class DisplayPrefs {

    public static final DisplayPrefs NONE = new DisplayPrefs(new EnumMap<>(DisplayKind.class), null);
    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String ALERTS = "alerts";

    private final Map<DisplayKind, Boolean> choices;
    private final Boolean alerts;

    private DisplayPrefs(Map<DisplayKind, Boolean> choices, Boolean alerts) {
        this.choices = choices;
        this.alerts = alerts;
    }

    public boolean isShown(DisplayKind kind, boolean defaultOn) {
        return choices.getOrDefault(kind, defaultOn);
    }

    public DisplayPrefs with(DisplayKind kind, boolean shown) {
        EnumMap<DisplayKind, Boolean> changed = copy();
        changed.put(kind, shown);
        return new DisplayPrefs(changed, alerts);
    }

    public boolean alertsOn() {
        return alerts == null || alerts;
    }

    public DisplayPrefs withAlerts(boolean on) {
        return new DisplayPrefs(choices, on);
    }

    public String encode() {
        StringJoiner joiner = new StringJoiner(";");
        choices.forEach((kind, shown) -> joiner.add(kind.key() + "=" + onOff(shown)));
        if (alerts != null) {
            joiner.add(ALERTS + "=" + onOff(alerts));
        }
        return joiner.toString();
    }

    public static DisplayPrefs decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return NONE;
        }
        EnumMap<DisplayKind, Boolean> choices = new EnumMap<>(DisplayKind.class);
        Boolean alerts = null;
        for (String entry : encoded.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String key = parts[0].trim();
            String value = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!value.equals(ON) && !value.equals(OFF)) {
                continue;
            }
            DisplayKind kind = kindOf(key);
            if (kind != null) {
                choices.put(kind, value.equals(ON));
            } else if (key.equals(ALERTS)) {
                alerts = value.equals(ON);
            }
        }
        return new DisplayPrefs(choices, alerts);
    }

    public static DisplayPrefs read(Player player, NamespacedKey key) {
        return decode(player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    public void write(Player player, NamespacedKey key) {
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, encode());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DisplayPrefs prefs && prefs.choices.equals(choices) && Objects.equals(prefs.alerts, alerts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(choices, alerts);
    }

    private static String onOff(boolean on) {
        return on ? ON : OFF;
    }

    private EnumMap<DisplayKind, Boolean> copy() {
        return choices.isEmpty() ? new EnumMap<>(DisplayKind.class) : new EnumMap<>(choices);
    }

    private static DisplayKind kindOf(String key) {
        for (DisplayKind kind : DisplayKind.values()) {
            if (kind.key().equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
