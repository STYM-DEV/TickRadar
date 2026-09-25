package dev.stym.tickradar.sample;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final PlayerSamplers samplers;

    public PlayerConnectionListener(PlayerSamplers samplers) {
        this.samplers = samplers;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        samplers.start(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        samplers.stop(event.getPlayer().getUniqueId());
    }
}
