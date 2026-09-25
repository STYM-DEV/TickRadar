package dev.stym.tickradar.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlinePlayers {

    private final ConcurrentHashMap<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public void publish(PlayerState state) {
        states.put(state.id(), state);
    }

    public boolean isCurrent(PlayerState state) {
        return states.get(state.id()) == state;
    }

    public void remove(UUID id) {
        states.remove(id);
    }

    public Optional<PlayerState> get(UUID id) {
        return Optional.ofNullable(states.get(id));
    }

    public Collection<PlayerState> all() {
        return List.copyOf(states.values());
    }

    public int size() {
        return states.size();
    }
}
