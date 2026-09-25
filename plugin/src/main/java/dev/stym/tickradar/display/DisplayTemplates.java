package dev.stym.tickradar.display;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.ValueFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

final class DisplayTemplates {

    static final String BOSSBAR = "display.bossbar";
    static final String ACTIONBAR = "display.actionbar";
    static final String TAB_FOOTER = "display.tab-footer";
    static final String UNAVAILABLE = "display.unavailable";
    static final List<String> ALL = List.of(BOSSBAR, ACTIONBAR, TAB_FOOTER, UNAVAILABLE);
    private static final String MISSING = ValueFormat.MISSING;

    @FunctionalInterface
    interface Parser {

        Component parse(Settings settings, String language, String template, DisplayValues values);
    }

    private record Key(String language, String template, HealthStatus status, boolean onePlayer) {
    }

    private record Generation(long number, ConcurrentHashMap<Key, Function<DisplayValues, Component>> templates,
                              AtomicInteger fallbacks) {

        Generation(long number) {
            this(number, new ConcurrentHashMap<>(), new AtomicInteger());
        }
    }

    private record Compiled(Function<DisplayValues, Component> render, boolean fallback) {
    }

    private record ParserFallback(Parser parser, Settings settings, Key key) implements Function<DisplayValues, Component> {

        @Override
        public Component apply(DisplayValues values) {
            return parser.parse(settings, key.language(), key.template(), values);
        }
    }

    private final Parser parser;
    private final AtomicReference<Generation> cache = new AtomicReference<>(new Generation(Long.MIN_VALUE));

    DisplayTemplates(Parser parser) {
        this.parser = parser;
    }

    Component render(Settings settings, String language, String template, DisplayValues values) {
        return template(settings, language, template, values.status(), values.onePlayer()).apply(values);
    }

    Function<DisplayValues, Component> template(Settings settings, String language, String template, HealthStatus status,
                                                boolean onePlayer) {
        Key key = new Key(language, template, status, onePlayer);
        Generation generation = generationOf(settings.generation());
        if (generation == null) {
            return compile(settings, key).render();
        }
        Function<DisplayValues, Component> known = generation.templates().get(key);
        if (known != null) {
            return known;
        }
        Compiled compiled = compile(settings, key);
        Function<DisplayValues, Component> raced = generation.templates().putIfAbsent(key, compiled.render());
        if (raced != null) {
            return raced;
        }
        if (compiled.fallback()) {
            generation.fallbacks().incrementAndGet();
        }
        return compiled.render();
    }

    int fallbackCount() {
        return cache.get().fallbacks().get();
    }

    void prepare(Settings settings) {
        for (String language : settings.lang().languages()) {
            for (String template : ALL) {
                for (HealthStatus status : HealthStatus.values()) {
                    template(settings, language, template, status, true);
                    template(settings, language, template, status, false);
                }
            }
        }
    }

    private Generation generationOf(long number) {
        Generation current = cache.get();
        while (current.number() < number) {
            Generation next = new Generation(number);
            if (cache.compareAndSet(current, next)) {
                return next;
            }
            current = cache.get();
        }
        return current.number() == number ? current : null;
    }

    private Compiled compile(Settings settings, Key key) {
        Function<DisplayValues, Component> render = compileShape(settings, key);
        boolean fallback = render instanceof ParserFallback;
        char separator = ValueFormat.decimalSeparator(settings.lang().resolve(key.language()));
        if (separator == ValueFormat.DECIMAL_POINT) {
            return new Compiled(render, fallback);
        }
        return new Compiled(values -> render.apply(values.withDecimalSeparator(separator)), fallback);
    }

    private Function<DisplayValues, Component> compileShape(Settings settings, Key key) {
        Component shape = parser.parse(settings, key.language(), key.template(), DisplayValues.markers(key.status(), key.onePlayer()));
        MarkerTemplate template = new MarkerTemplate(shape);
        if (rendersLikeTheParser(template, settings, key)) {
            return template::render;
        }
        return new ParserFallback(parser, settings, key);
    }

    private boolean rendersLikeTheParser(MarkerTemplate template, Settings settings, Key key) {
        for (DisplayValues sample : samples(key.status(), key.onePlayer())) {
            if (!template.render(sample).equals(parser.parse(settings, key.language(), key.template(), sample))) {
                return false;
            }
        }
        return true;
    }

    private static List<DisplayValues> samples(HealthStatus status, boolean onePlayer) {
        String players = onePlayer ? "1" : "12";
        return List.of(
                new DisplayValues("R12", "19.8", "19.9", "23.4", "20.0", "41.0", "39.0", "60", players, onePlayer, status),
                new DisplayValues("G", MISSING, MISSING, MISSING, MISSING, MISSING, MISSING, "300", players, onePlayer, status));
    }
}
