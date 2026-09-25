package dev.stym.tickradar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.RegionView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class RegionListTest {

    private static final String FOOTER = "Only regions with players are listed. Use /tps (Folia) or spark for a full view.";

    private final Settings settings = new ConfigFiles(Path.of("unused"), RegionListTest.class.getClassLoader()::getResourceAsStream)
            .builtInDefaults(1);
    private final RegionList list = new RegionList(new Renderer());

    @Test
    void theListShowsGlobalThenRegionsThenTheFixedFooter() {
        RegionView view = RegionView.of(0, RegionsPageTest.snapshot("G", 3.2),
                List.of(RegionsPageTest.snapshot("R12", 52.1), RegionsPageTest.snapshot("R3", 12)));
        List<String> lines = plain(list.lines(settings, "en", RegionsPage.of(view, 1, 8), true));
        assertEquals(List.of(
                "[TickRadar] Regions with players, slowest first (page 1/1):",
                "G global region · 19.8 TPS · 3.2 ms (max 4.2 ms)",
                "R12 in world \"world\" around X 1204, Z -3380 · 5 players · 19.8 TPS · 52.1 ms (max 53.1 ms) [Teleport]",
                "R3 in world \"world\" around X 1204, Z -3380 · 5 players · 19.8 TPS · 12.0 ms (max 13.0 ms) [Teleport]",
                FOOTER), lines);
    }

    @Test
    void theFrenchListUsesFrenchTypographyAndADecimalComma() {
        RegionView view = RegionView.of(0, RegionsPageTest.snapshot("G", 3.2), List.of(RegionsPageTest.snapshot("R12", 52.1)));
        List<String> lines = plain(list.lines(settings, "fr", RegionsPage.of(view, 1, 8), true));
        assertEquals("G région globale · 19,8 TPS · 3,2 ms (max 4,2 ms)", lines.get(1));
        assertEquals("R12 du monde «\u00A0world\u00A0», vers X 1204, Z -3380 · 5 joueurs · 19,8 TPS · 52,1 ms (max 53,1 ms) [Téléporter]",
                lines.get(2));
    }

    @Test
    void theFooterIsAlsoShownWithoutAnyRegion() {
        List<String> lines = plain(list.lines(settings, "en", RegionsPage.of(RegionView.EMPTY, 1, 8), true));
        assertEquals(List.of("[TickRadar] No region has been measured yet; try again in a few seconds.", FOOTER), lines);
    }

    @Test
    void aFullPageLinksToTheNextOne() {
        RegionView view = RegionView.of(0, RegionsPageTest.snapshot("G", 3.2), List.of(RegionsPageTest.snapshot("R1", 5)));
        List<Component> lines = list.lines(settings, "en", RegionsPage.of(view, 1, 1), true);
        Component next = lines.get(lines.size() - 2);
        assertEquals("[Next page: /tickradar regions 2]", PlainTextComponentSerializer.plainText().serialize(next));
        assertEquals(ClickEvent.runCommand("/tickradar regions 2"), next.clickEvent());
    }

    @Test
    void theTeleportLinkRunsTheTpCommand() {
        RegionView view = RegionView.of(0, null, List.of(RegionsPageTest.snapshot("R12", 52.1)));
        Component line = list.lines(settings, "en", RegionsPage.of(view, 1, 8), true).get(1);
        List<ClickEvent> clicks = clickEvents(line);
        assertEquals(List.of(ClickEvent.runCommand("/tickradar tp R12")), clicks);
    }

    @Test
    void theGlobalRegionHasNoTeleportLink() {
        RegionView view = RegionView.of(0, RegionsPageTest.snapshot("G", 3.2), List.of());
        Component line = list.lines(settings, "en", RegionsPage.of(view, 1, 8), true).get(1);
        assertEquals(List.of(), clickEvents(line));
        assertNull(line.clickEvent());
    }

    @Test
    void aSenderWithoutTheTeleportPermissionGetsNoLink() {
        RegionView view = RegionView.of(0, null, List.of(RegionsPageTest.snapshot("R12", 52.1)));
        Component line = list.lines(settings, "en", RegionsPage.of(view, 1, 8), false).get(1);
        assertEquals(List.of(), clickEvents(line));
    }

    @Test
    void theFrenchFooterIsTranslated() {
        List<String> lines = plain(list.lines(settings, "fr", RegionsPage.of(RegionView.EMPTY, 1, 8), true));
        assertEquals("Seules les régions avec des joueurs sont listées. Utilisez /tps (Folia) ou spark pour une vue complète.",
                lines.getLast());
    }

    static List<ClickEvent> clickEvents(Component component) {
        List<ClickEvent> events = new ArrayList<>();
        collect(component, events);
        return events;
    }

    private static void collect(Component component, List<ClickEvent> events) {
        if (component.clickEvent() != null) {
            events.add(component.clickEvent());
        }
        component.children().forEach(child -> collect(child, events));
    }

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }
}
