package dev.stym.tickradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.LangBundle;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionStats;
import dev.stym.tickradar.engine.TpsReading;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class RendererTest {

    private final Renderer renderer = new Renderer();
    private final Settings settings = new ConfigFiles(Path.of("unused"), RendererTest.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);

    private static RegionSnapshot snapshot(int players, double mspt) {
        return new RegionSnapshot("R12", "world", Optional.of(new BlockPosition(1204, 64, -3380)), players, 19.8, 19.9, mspt,
                new RegionStats(20, 41, 39, 60), 0, false);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void regionLineMatchesTheSpecification() {
        Component line = renderer.message(settings, "en", "command.region", renderer.region(settings, "en", snapshot(3, 23.4)));
        assertEquals("[TickRadar] Your region: 19.8 TPS · 23.4 ms (max 41.0 ms over 60 s) · 3 players here", plain(line));
    }

    @Test
    void anUnknownTpsIsShownAsADash() {
        RegionSnapshot unknown = new RegionSnapshot("R3", "world", Optional.empty(), 1, Double.NaN, Double.NaN, 12.5,
                new RegionStats(12.5, 12.5, 12.5, 1), 0, false);
        Component line = renderer.text(settings, "en", "command.region", renderer.region(settings, "en", unknown));
        assertEquals("Your region: \u2014 TPS · 12.5 ms (max 12.5 ms over 60 s) · 1 player here", plain(line));
        assertEquals("Region R3: \u2014 TPS · 12.5 ms",
                plain(renderer.text(settings, "en", "display.bossbar", renderer.region(settings, "en", unknown))));
        assertEquals("\u2014 TPS · 12.5 ms (max 12.5 ms)",
                plain(renderer.text(settings, "en", "display.actionbar", renderer.region(settings, "en", unknown))));
        assertEquals("Region R3: \u2014 TPS · 12.5 ms · 1 player here",
                plain(renderer.text(settings, "en", "display.tab-footer", renderer.region(settings, "en", unknown))));
    }

    @Test
    void aTpsReadingReplacesTheDash() {
        RegionSnapshot unknown = new RegionSnapshot("R3", "world", Optional.empty(), 1, Double.NaN, Double.NaN, 12.5,
                new RegionStats(12.5, 12.5, 12.5, 1), 0, false);
        RegionSnapshot known = unknown.withTps(new TpsReading(19.94, 19.6, 1));
        assertEquals("Region R3: 19.9 TPS · 12.5 ms",
                plain(renderer.text(settings, "en", "display.bossbar", renderer.region(settings, "en", known))));
    }

    @Test
    void oneOrManyPlayers() {
        assertEquals("1 player here", plain(renderer.players(settings, "en", 1)));
        assertEquals("0 players here", plain(renderer.players(settings, "en", 0)));
        assertEquals("1 joueur ici", plain(renderer.players(settings, "fr", 1)));
    }

    @Test
    void frenchRegionLine() {
        Component line = renderer.text(settings, "fr", "command.region", renderer.region(settings, "fr", snapshot(2, 23.4)));
        assertEquals("Votre région\u00A0: 19,8 TPS · 23,4 ms (max 41,0 ms sur 60 s) · 2 joueurs ici", plain(line));
    }

    @Test
    void statusColorFollowsTheThresholds() {
        assertEquals(NamedTextColor.GREEN, colorOfActionBar(snapshot(1, 10)));
        assertEquals(NamedTextColor.YELLOW, colorOfActionBar(snapshot(1, 45)));
        assertEquals(NamedTextColor.RED, colorOfActionBar(snapshot(1, 55)));
    }

    @Test
    void valuesAreInsertedAsTextNeverAsTags() {
        LangBundle lang = ((LoadResult.Loaded<LangBundle>) LangBundle.load(
                Map.of("en", "message: \"<error>\"\nprefix: \"\"\n"), Map.of())).value();
        Settings custom = new Settings(ConfigLoader.defaults(), lang, 1);
        Component component = renderer.message(custom, "en", "message",
                Placeholder.unparsed("error", "<red>boom</red>"));
        assertEquals("<red>boom</red>", plain(component));
    }

    private TextColor colorOfActionBar(RegionSnapshot snapshot) {
        return firstColor(renderer.text(settings, "en", "display.actionbar", renderer.region(settings, "en", snapshot)));
    }

    private static TextColor firstColor(Component component) {
        if (component.color() != null) {
            return component.color();
        }
        for (Component child : component.children()) {
            TextColor color = firstColor(child);
            if (color != null) {
                return color;
            }
        }
        return null;
    }
}
