package dev.stym.tickradar.player;

import java.util.UUID;
import org.bukkit.entity.Player;

public record PlayerState(
        UUID id,
        Player player,
        String anchorId,
        String language,
        boolean canDisplay,
        boolean canReceiveAlerts,
        boolean canTeleport,
        DisplayPrefs prefs) {
}
