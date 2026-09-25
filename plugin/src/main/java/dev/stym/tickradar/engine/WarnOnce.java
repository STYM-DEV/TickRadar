package dev.stym.tickradar.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WarnOnce {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public boolean firstTime(String key) {
        return seen.add(key);
    }
}
