package dev.stym.tickradar.display;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

@FunctionalInterface
interface DisplayRenderer {

    Function<DisplayValues, Component> template(Settings settings, String language, String template, HealthStatus status,
                                                boolean onePlayer);
}
