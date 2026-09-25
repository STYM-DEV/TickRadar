package dev.stym.tickradar.command;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

final class RegionList {

    static final String REGIONS_COMMAND = "/tickradar regions ";

    private final Renderer renderer;

    RegionList(Renderer renderer) {
        this.renderer = renderer;
    }

    List<Component> lines(Settings settings, String language, RegionsPage page, boolean canTeleport) {
        List<Component> lines = new ArrayList<>();
        if (page.isEmpty()) {
            lines.add(renderer.message(settings, language, "regions.none"));
        } else {
            lines.add(renderer.message(settings, language, "regions.header",
                    Placeholder.unparsed("page", Integer.toString(page.page())),
                    Placeholder.unparsed("pages", Integer.toString(page.pages()))));
            for (RegionSnapshot region : page.entries()) {
                lines.add(entry(settings, language, region, canTeleport));
            }
        }
        if (page.hasNext()) {
            lines.add(nextPage(settings, language, page.page() + 1));
        }
        lines.add(renderer.text(settings, language, "regions.footer"));
        return lines;
    }

    private Component entry(Settings settings, String language, RegionSnapshot region, boolean canTeleport) {
        TagResolver values = renderer.region(settings, language, region);
        if (region.isGlobal()) {
            return renderer.text(settings, language, "regions.global", values);
        }
        String key = region.unavailable() ? "regions.unavailable" : "regions.region";
        Optional<BlockPosition> position = region.position();
        Component teleport = canTeleport && position.isPresent() ? renderer.teleportLink(settings, language, region.id())
                : Component.empty();
        return renderer.text(settings, language, key, values,
                Placeholder.unparsed("world", region.world()),
                Placeholder.unparsed("x", position.map(at -> Integer.toString(at.x())).orElse(ValueFormat.MISSING)),
                Placeholder.unparsed("z", position.map(at -> Integer.toString(at.z())).orElse(ValueFormat.MISSING)),
                Placeholder.component("region_players", renderer.regionPlayers(settings, language, region.players())),
                Placeholder.component("teleport", teleport));
    }

    private Component nextPage(Settings settings, String language, int next) {
        TagResolver page = Placeholder.unparsed("page", Integer.toString(next));
        return renderer.runCommandLink(renderer.text(settings, language, "regions.next", page), REGIONS_COMMAND + next);
    }
}
