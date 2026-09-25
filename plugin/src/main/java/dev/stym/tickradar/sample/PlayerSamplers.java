package dev.stym.tickradar.sample;

import dev.stym.tickradar.player.DisplayPrefs;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class PlayerSamplers {

    private final SamplingContext context;
    private final ConcurrentHashMap<UUID, PlayerSampler> samplers = new ConcurrentHashMap<>();

    public PlayerSamplers(SamplingContext context) {
        this.context = context;
    }

    public void start(Player player) {
        context.scheduler().runForPlayerNextTick(player, () -> startOnPlayerThread(player));
    }

    public void stop(UUID id) {
        PlayerSampler sampler = samplers.remove(id);
        if (sampler != null) {
            sampler.cancel();
        }
        forget(id);
    }

    public Optional<PlayerSampler> get(UUID id) {
        return Optional.ofNullable(samplers.get(id));
    }

    public void cancelAll() {
        samplers.values().forEach(PlayerSampler::cancel);
        samplers.clear();
    }

    private void startOnPlayerThread(Player player) {
        if (context.isStopping()) {
            return;
        }
        UUID id = player.getUniqueId();
        DisplayPrefs prefs = DisplayPrefs.read(player, context.prefsKey());
        PlayerSampler sampler = new PlayerSampler(context, player, prefs, retired -> retire(id, retired));
        PlayerSampler previous = samplers.put(id, sampler);
        if (previous != null) {
            previous.cancel();
        }
        int interval = context.settings().get().config().sampling().intervalTicks();
        if (!sampler.start(interval)) {
            samplers.remove(id, sampler);
            forget(id);
        }
    }

    private void retire(UUID id, PlayerSampler sampler) {
        if (samplers.remove(id, sampler)) {
            forget(id);
        }
    }

    private void forget(UUID id) {
        context.online().remove(id);
        context.regions().detach(id);
    }
}
