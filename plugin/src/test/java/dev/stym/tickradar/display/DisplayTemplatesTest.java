package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.LangBundle;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class DisplayTemplatesTest {

    private final Renderer renderer = new Renderer();
    private final Settings settings = new ConfigFiles(Path.of("unused"), DisplayTemplatesTest.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);
    private final AtomicInteger parses = new AtomicInteger();
    private final DisplayTemplates templates = new DisplayTemplates((settings, language, template, values) -> {
        parses.incrementAndGet();
        return renderer.parse(settings, language, template, values);
    });

    @Test
    void everyBuiltInTemplateRendersExactlyLikeMiniMessage() {
        for (String language : settings.lang().languages()) {
            for (String template : DisplayTemplates.ALL) {
                for (HealthStatus status : HealthStatus.values()) {
                    for (DisplayValues values : List.of(values("3", false, status), values("1", true, status))) {
                        DisplayValues localized = values.withDecimalSeparator(renderer.decimalSeparator(settings, language));
                        assertEquals(renderer.parse(settings, language, template, localized),
                                templates.render(settings, language, template, values), language + " " + template + " " + status);
                    }
                }
            }
        }
    }

    @Test
    void aTemplateIsParsedOnlyWhenItIsPrepared() {
        templates.render(settings, "en", "display.actionbar", values("3", false, HealthStatus.OK));
        int preparation = parses.get();
        templates.render(settings, "en", "display.actionbar", withMspt(values("3", false, HealthStatus.OK), "31.2"));
        templates.render(settings, "en", "display.actionbar", withMspt(values("4", false, HealthStatus.OK), "12.0"));
        assertEquals(preparation, parses.get());
    }

    @Test
    void preparedTemplatesAreNeverParsedWhileSampling() {
        templates.prepare(settings);
        int prepared = parses.get();
        for (String language : settings.lang().languages()) {
            for (String template : DisplayTemplates.ALL) {
                for (HealthStatus status : HealthStatus.values()) {
                    templates.render(settings, language, template, values("1", true, status));
                    templates.render(settings, language, template, values("7", false, status));
                }
            }
        }
        assertEquals(prepared, parses.get());
    }

    @Test
    void anotherStatusOrAReloadPreparesTheTemplateAgain() {
        templates.render(settings, "en", "display.actionbar", values("3", false, HealthStatus.OK));
        int preparation = parses.get();
        templates.render(settings, "en", "display.actionbar", values("3", false, HealthStatus.WARNING));
        templates.render(withGeneration(settings, 2), "en", "display.actionbar", values("3", false, HealthStatus.WARNING));
        assertEquals(3 * preparation, parses.get());
    }

    @Test
    void aTemplateTheMarkersCannotRenderFallsBackToMiniMessage() {
        Settings custom = custom("display:\n  bossbar: \"<hover:show_text:'<mspt> ms'>Region <region></hover>\"\n");
        DisplayValues values = values("3", false, HealthStatus.OK);
        Component rendered = templates.render(custom, "en", "display.bossbar", values);
        assertEquals(renderer.parse(custom, "en", "display.bossbar", values), rendered);
        int afterFirst = parses.get();
        templates.render(custom, "en", "display.bossbar", withMspt(values, "31.2"));
        assertEquals(afterFirst + 1, parses.get());
    }

    @Test
    void aTemplateContainingAMarkerCharacterFallsBackToMiniMessage() {
        String marker = DisplayTag.MSPT.marker();
        Settings custom = custom("display:\n  bossbar: \"" + marker + " <mspt> ms\"\n");
        DisplayValues values = values("3", false, HealthStatus.OK);
        assertEquals(marker + " 23.4 ms", plain(templates.render(custom, "en", "display.bossbar", values)));
    }

    @Test
    void aResourcePackGlyphIsRenderedByTheMarkers() {
        String glyph = "\uE000\uE008\uF8FF";
        Settings custom = custom("display:\n  bossbar: \"" + glyph + " <mspt> ms <region>\"\n");
        DisplayValues values = values("3", false, HealthStatus.OK);
        assertEquals(glyph + " 23.4 ms R12", plain(templates.render(custom, "en", "display.bossbar", values)));
        int afterFirst = parses.get();
        assertEquals(glyph + " 31.2 ms R12", plain(templates.render(custom, "en", "display.bossbar", withMspt(values, "31.2"))));
        assertEquals(afterFirst, parses.get());
        assertEquals(0, templates.fallbackCount());
    }

    @Test
    void markersAreNoncharacters() {
        for (DisplayTag tag : DisplayTag.values()) {
            char marker = tag.marker().charAt(0);
            assertTrue(marker >= '\uFDD0' && marker <= '\uFDEF', tag.name());
            assertEquals(tag, DisplayTag.ofMarker(marker));
        }
        assertNull(DisplayTag.ofMarker('\uE000'));
    }

    @Test
    void builtInTemplatesNeverFallBack() {
        templates.prepare(settings);
        assertEquals(0, templates.fallbackCount());
    }

    @Test
    void eachFallbackTemplateIsCountedOnceForTheCurrentConfiguration() {
        Settings custom = custom("display:\n  bossbar: \"<hover:show_text:'<mspt> ms'>Region <region></hover>\"\n");
        templates.render(custom, "en", "display.bossbar", values("3", false, HealthStatus.OK));
        templates.render(custom, "en", "display.bossbar", values("4", false, HealthStatus.OK));
        templates.render(custom, "en", "display.bossbar", values("1", true, HealthStatus.OK));
        templates.render(custom, "en", "display.actionbar", values("3", false, HealthStatus.OK));
        assertEquals(2, templates.fallbackCount());
        templates.render(withGeneration(custom, 8), "en", "display.actionbar", values("3", false, HealthStatus.OK));
        assertEquals(0, templates.fallbackCount());
    }

    @Test
    void aFrenchFallbackTemplateIsCountedAndKeepsTheDecimalComma() {
        String hover = "display:\n  bossbar: \"<hover:show_text:'<mspt> ms'>Region <region> <tps> TPS</hover>\"\n";
        Settings custom = custom(hover, hover);
        DisplayValues values = values("3", false, HealthStatus.OK);
        Component rendered = templates.render(custom, "fr", "display.bossbar", values);
        assertEquals(1, templates.fallbackCount());
        assertEquals("Region R12 19,8 TPS", plain(rendered));
        assertEquals(renderer.parse(custom, "fr", "display.bossbar", values.withDecimalSeparator(',')), rendered);
    }

    @Test
    void frenchBuiltInTemplatesNeverFallBack() {
        for (String template : DisplayTemplates.ALL) {
            for (HealthStatus status : HealthStatus.values()) {
                templates.render(settings, "fr", template, values("1", true, status));
                templates.render(settings, "fr", template, values("3", false, status));
            }
        }
        assertEquals(0, templates.fallbackCount());
    }

    @Test
    void valuesStayPlainText() {
        DisplayValues values = new DisplayValues("<red>R1</red>", "19.8", "19.9", "23.4", "20.0", "41.0", "39.0", "60", "3",
                false, HealthStatus.OK);
        assertEquals("Region <red>R1</red>: 19.8 TPS · 23.4 ms", plain(templates.render(settings, "en", "display.bossbar", values)));
    }

    @Test
    void frenchDisplaysUseADecimalComma() {
        DisplayValues values = values("3", false, HealthStatus.OK);
        assertTrue(plain(templates.render(settings, "fr", "display.bossbar", values)).endsWith("19,8 TPS · 23,4 ms"));
        assertEquals("19,8 TPS · 23,4 ms (max 41,0 ms)", plain(templates.render(settings, "fr", "display.actionbar", values)));
        assertEquals("19.8 TPS · 23.4 ms (max 41.0 ms)", plain(templates.render(settings, "en", "display.actionbar", values)));
    }

    @Test
    void frenchBuiltInDisplaysUseADecimalCommaThroughTheMarkerTemplate() {
        DisplayValues values = values("3", false, HealthStatus.OK);
        assertEquals("Région R12\u00A0: 19,8 TPS · 23,4 ms", plain(templates.render(settings, "fr", "display.bossbar", values)));
        assertEquals("19,8 TPS · 23,4 ms (max 41,0 ms)", plain(templates.render(settings, "fr", "display.actionbar", values)));
        assertEquals("Région R12\u00A0: 19,8 TPS · 23,4 ms · 3 joueurs ici",
                plain(templates.render(settings, "fr", "display.tab-footer", values)));
        assertEquals(0, templates.fallbackCount());
    }

    @Test
    void aResourcePackGlyphIsRenderedByTheMarkersInFrenchWithADecimalComma() {
        String glyph = "\uE000\uE008\uF8FF";
        Settings custom = custom("display:\n  bossbar: \"" + glyph + " <mspt> ms <region>\"\n",
                "display:\n  bossbar: \"" + glyph + " <mspt> ms <region>\"\n");
        DisplayValues values = values("3", false, HealthStatus.OK);
        assertEquals(glyph + " 23,4 ms R12", plain(templates.render(custom, "fr", "display.bossbar", values)));
        int afterFirst = parses.get();
        assertEquals(glyph + " 31,2 ms R12", plain(templates.render(custom, "fr", "display.bossbar", withMspt(values, "31.2"))));
        assertEquals(afterFirst, parses.get());
        assertEquals(0, templates.fallbackCount());
    }

    @Test
    void theDecimalCommaLeavesTheMissingValueAndTheOtherValuesAlone() {
        DisplayValues missing = new DisplayValues("R1", "\u2014", "\u2014", "\u2014", "\u2014", "\u2014", "\u2014", "60", "12",
                false, HealthStatus.UNAVAILABLE);
        assertEquals(missing, missing.withDecimalSeparator(','));
        DisplayValues values = values("3", false, HealthStatus.OK);
        assertSame(values, values.withDecimalSeparator('.'));
    }

    private static DisplayValues values(String players, boolean onePlayer, HealthStatus status) {
        return new DisplayValues("R12", "19.8", "19.9", "23.4", "20.0", "41.0", "39.0", "60", players, onePlayer, status);
    }

    private static DisplayValues withMspt(DisplayValues values, String mspt) {
        return new DisplayValues(values.region(), values.tps(), values.tps1m(), mspt, values.msptAverage(), values.msptMax(),
                values.msptP95(), values.history(), values.players(), values.onePlayer(), values.status());
    }

    private static Settings custom(String english) {
        LangBundle lang = ((LoadResult.Loaded<LangBundle>) LangBundle.load(Map.of("en", english), Map.of())).value();
        return new Settings(ConfigLoader.defaults(), lang, 7);
    }

    private static Settings custom(String english, String french) {
        LangBundle lang = ((LoadResult.Loaded<LangBundle>) LangBundle.load(Map.of("en", english, "fr", french), Map.of())).value();
        return new Settings(ConfigLoader.defaults(), lang, 7);
    }

    private static Settings withGeneration(Settings settings, long generation) {
        return new Settings(settings.config(), settings.lang(), generation);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
