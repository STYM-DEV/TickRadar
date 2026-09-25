package dev.stym.tickradar.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class SettingsService {

    private final ConfigFiles files;
    private final Logger logger;
    private final AtomicLong generations = new AtomicLong();
    private final AtomicReference<Settings> current = new AtomicReference<>();

    public SettingsService(ConfigFiles files, Logger logger) {
        this.files = files;
        this.logger = logger;
    }

    public Settings current() {
        return current.get();
    }

    public Settings loadAtStartup() {
        try {
            files.installDefaults();
        } catch (IOException e) {
            logger.severe("Cannot copy the default files into the plugin folder: " + e.getMessage());
        }
        long generation = generations.incrementAndGet();
        Settings settings = switch (files.read(generation)) {
            case LoadResult.Loaded<Settings>(Settings loaded, List<String> warnings) -> {
                warnings.forEach(logger::warning);
                yield loaded;
            }
            case LoadResult.Unreadable<Settings>(String error) -> {
                logger.severe(error);
                logger.severe("TickRadar runs with its default settings until this file is fixed and /tickradar reload succeeds.");
                yield files.builtInDefaults(generation);
            }
        };
        current.set(settings);
        return settings;
    }

    public LoadResult<Settings> reload() {
        LoadResult<Settings> result = files.read(generations.incrementAndGet());
        switch (result) {
            case LoadResult.Loaded<Settings>(Settings loaded, List<String> warnings) -> {
                warnings.forEach(logger::warning);
                publishIfNewer(loaded);
                logger.info("Reloaded" + (warnings.isEmpty() ? "." : " with " + warnings.size() + " warning(s)."));
            }
            case LoadResult.Unreadable<Settings>(String error) ->
                    logger.severe("Reload failed, the previous settings are kept: " + error);
        }
        return result;
    }

    private void publishIfNewer(Settings loaded) {
        current.accumulateAndGet(loaded, (previous, candidate) ->
                previous == null || previous.generation() < candidate.generation() ? candidate : previous);
    }
}
