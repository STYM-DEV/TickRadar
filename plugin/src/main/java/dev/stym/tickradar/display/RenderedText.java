package dev.stym.tickradar.display;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

final class RenderedText {

    private final DisplayRenderer renderer;
    private Function<DisplayValues, Component> compiled;
    private long generation;
    private String language;
    private String template;
    private HealthStatus status;
    private boolean onePlayer;
    private DisplayValues values;
    private Component component;

    RenderedText(DisplayRenderer renderer) {
        this.renderer = renderer;
    }

    boolean refresh(Settings settings, String language, String template, DisplayValues values) {
        if (!sameTemplate(settings, language, template, values)) {
            compiled = renderer.template(settings, language, template, values.status(), values.onePlayer());
            this.generation = settings.generation();
            this.language = language;
            this.template = template;
            this.status = values.status();
            this.onePlayer = values.onePlayer();
            this.values = null;
        }
        if (values == this.values) {
            return false;
        }
        Component rendered = compiled.apply(values);
        boolean changed = !rendered.equals(component);
        this.values = values;
        this.component = rendered;
        return changed;
    }

    Component component() {
        return component;
    }

    private boolean sameTemplate(Settings settings, String language, String template, DisplayValues values) {
        return compiled != null && generation == settings.generation() && status == values.status()
                && onePlayer == values.onePlayer() && language.equals(this.language) && template.equals(this.template);
    }
}
