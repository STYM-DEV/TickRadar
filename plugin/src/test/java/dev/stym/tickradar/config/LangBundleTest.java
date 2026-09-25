package dev.stym.tickradar.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class LangBundleTest {

    private static final List<String> TEXT_TAGS = List.of("region", "tps", "tps_1m", "mspt", "mspt_avg", "mspt_max", "mspt_p95",
            "history", "players", "count", "version", "warnings", "error", "world", "x", "z", "page", "pages", "http", "wait");
    private static final List<String> COMPONENT_TAGS = List.of("players_text", "status", "display", "region_players", "teleport");

    private static Map<String, String> builtIn() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String language : ConfigFiles.BUILT_IN_LANGUAGES) {
            files.put(language, ConfigLoaderTest.resource("lang/" + language + ".yml"));
        }
        return files;
    }

    private static LoadResult.Loaded<LangBundle> load(Map<String, String> admin) throws IOException {
        return Loads.loaded(LangBundle.load(builtIn(), admin));
    }

    @Test
    void englishAndFrenchHaveExactlyTheSameKeys() throws IOException {
        LoadResult.Loaded<LangBundle> result = load(Map.of());
        assertEquals(List.of(), result.warnings());
        Set<String> english = result.value().keys("en");
        LoadResult.Loaded<LangBundle> frenchAlone = Loads.loaded(
                LangBundle.load(Map.of("en", ConfigLoaderTest.resource("lang/fr.yml")), Map.of()));
        assertEquals(english, frenchAlone.value().keys("en"));
    }

    @Test
    void everyTextIsValidMiniMessageWithOnlyKnownTags() throws IOException {
        LangBundle bundle = load(Map.of()).value();
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        TagResolver known = knownTags();
        for (String language : List.of("en", "fr")) {
            for (String key : bundle.keys(language)) {
                String text = bundle.text(language, key);
                Component component = strict.deserialize(text, known);
                String plain = PlainTextComponentSerializer.plainText().serialize(component);
                assertFalse(plain.contains("<"), language + " " + key + " has an unknown tag: " + plain);
            }
        }
    }

    @Test
    void theFrenchFileIsReallyTranslated() throws IOException {
        LangBundle bundle = load(Map.of()).value();
        assertEquals("Your region has not been measured yet; try again in a few seconds.",
                plain(bundle.text("en", "command.not-measured")));
        assertTrue(bundle.text("fr", "command.not-measured").contains("Votre région"));
    }

    @Test
    void frenchTextsKeepANonBreakingSpaceBeforeHighPunctuationAndInsideQuotes() throws IOException {
        LangBundle bundle = load(Map.of()).value();
        for (String key : bundle.keys("fr")) {
            String text = bundle.text("fr", key);
            for (String breakable : List.of(" :", " ;", " !", " ?", "« ", " »")) {
                assertFalse(text.contains(breakable), "fr " + key + " contains '" + breakable + "': " + text);
            }
        }
    }

    @Test
    void noTextShowsInternalJargon() throws IOException {
        LangBundle bundle = load(Map.of()).value();
        for (String language : List.of("en", "fr")) {
            for (String key : bundle.keys(language)) {
                String text = bundle.text(language, key).toLowerCase(Locale.ROOT);
                for (String jargon : List.of("anchor", "ancre", "snapshot", "instantané", "cas ", "marker", "marqueur")) {
                    assertFalse(text.contains(jargon), language + " " + key + " contains '" + jargon + "': " + text);
                }
            }
        }
    }

    @Test
    void unknownLanguagesFallBackToEnglish() throws IOException {
        LangBundle bundle = load(Map.of()).value();
        assertEquals("en", bundle.resolve("de"));
        assertEquals("en", bundle.resolve(null));
        assertEquals("fr", bundle.resolve("fr"));
        assertEquals(bundle.text("en", "status.ok"), bundle.text("de", "status.ok"));
    }

    @Test
    void adminTextsOverrideAndMissingKeysComeFromTheBuiltInFile() throws IOException {
        LoadResult.Loaded<LangBundle> result = load(Map.of(
                "fr", "status:\n  ok: \"tout va bien\"\n",
                "de", "status:\n  ok: \"alles gut\"\nnonsense: \"x\"\n"));
        LangBundle bundle = result.value();
        assertEquals("tout va bien", bundle.text("fr", "status.ok"));
        assertEquals("critique", bundle.text("fr", "status.critical"));
        assertEquals("alles gut", bundle.text("de", "status.ok"));
        assertEquals("critical", bundle.text("de", "status.critical"));
        assertEquals(Set.of("de", "en", "fr"), bundle.languages());
        assertEquals(List.of("lang/de.yml: unknown key 'nonsense' ignored"), result.warnings());
    }

    @Test
    void anUnreadableAdminFileRejectsTheWholeBundle() throws IOException {
        LoadResult<LangBundle> result = LangBundle.load(builtIn(), Map.of("fr", "status: [broken\n"));
        String error = assertInstanceOf(LoadResult.Unreadable.class, result).error();
        assertTrue(error.startsWith("lang/fr.yml:"), error);
    }

    @Test
    void anUnknownKeyReturnsTheKeyItself() throws IOException {
        assertEquals("no.such.key", load(Map.of()).value().text("en", "no.such.key"));
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static TagResolver knownTags() {
        TagResolver.Builder builder = TagResolver.builder();
        TEXT_TAGS.forEach(tag -> builder.resolver(Placeholder.unparsed(tag, "1")));
        COMPONENT_TAGS.forEach(tag -> builder.resolver(Placeholder.component(tag, Component.text("x"))));
        builder.resolver(Placeholder.styling("status_color", NamedTextColor.GREEN));
        return builder.build();
    }
}
