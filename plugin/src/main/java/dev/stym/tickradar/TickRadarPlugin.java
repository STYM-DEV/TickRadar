package dev.stym.tickradar;

import org.bukkit.plugin.java.JavaPlugin;

public final class TickRadarPlugin extends JavaPlugin {

    private TickRadarRuntime runtime;

    @Override
    public void onEnable() {
        if (!ServerSupport.isFolia()) {
            ServerSupport.notFoliaLines(getServer().getName()).forEach(getLogger()::severe);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        runtime = TickRadarRuntime.start(this);
    }

    @Override
    public void onDisable() {
        TickRadarRuntime running = runtime;
        runtime = null;
        if (running != null) {
            running.stop();
        }
    }
}
