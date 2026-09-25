package dev.stym.tickradar.display;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.engine.HealthStatus;
import dev.stym.tickradar.engine.RegionSnapshot;
import dev.stym.tickradar.engine.ValueFormat;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Renderer {

    public static final String TELEPORT_COMMAND = "/tickradar tp ";

    private static final String PREFIX = "prefix";
    private static final String TELEPORT_LINK = "teleport.link";
    private static final String TELEPORT_HOVER = "teleport.hover";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DisplayTemplates templates = new DisplayTemplates(this::parse);

    public Component message(Settings settings, String language, String key, TagResolver... resolvers) {
        return text(settings, language, PREFIX).append(text(settings, language, key, resolvers));
    }

    public Component text(Settings settings, String language, String key, TagResolver... resolvers) {
        return miniMessage.deserialize(settings.lang().text(language, key), TagResolver.resolver(resolvers));
    }

    public void prepareDisplays(Settings settings) {
        templates.prepare(settings);
    }

    public int displayFallbackTemplates() {
        return templates.fallbackCount();
    }

    Function<DisplayValues, Component> displayTemplate(Settings settings, String language, String key, HealthStatus status,
                                                       boolean onePlayer) {
        return templates.template(settings, language, key, status, onePlayer);
    }

    public TagResolver region(Settings settings, String language, RegionSnapshot snapshot) {
        DisplayValues values = DisplayValues.of(settings, snapshot);
        return region(settings, language, values.withDecimalSeparator(decimalSeparator(settings, language)));
    }

    public char decimalSeparator(Settings settings, String language) {
        return ValueFormat.decimalSeparator(settings.lang().resolve(language));
    }

    TagResolver region(Settings settings, String language, DisplayValues values) {
        return TagResolver.resolver(
                Placeholder.unparsed(DisplayTag.REGION.key(), values.region()),
                Placeholder.unparsed(DisplayTag.TPS.key(), values.tps()),
                Placeholder.unparsed(DisplayTag.TPS_1M.key(), values.tps1m()),
                Placeholder.unparsed(DisplayTag.MSPT.key(), values.mspt()),
                Placeholder.unparsed(DisplayTag.MSPT_AVG.key(), values.msptAverage()),
                Placeholder.unparsed(DisplayTag.MSPT_MAX.key(), values.msptMax()),
                Placeholder.unparsed(DisplayTag.MSPT_P95.key(), values.msptP95()),
                Placeholder.unparsed(DisplayTag.HISTORY.key(), values.history()),
                Placeholder.unparsed(DisplayTag.PLAYERS.key(), values.players()),
                Placeholder.component("players_text", players(settings, language, values.onePlayer(), values.players())),
                Placeholder.component("status", text(settings, language, "status." + values.status().key())),
                Placeholder.styling("status_color", color(values.status())));
    }

    public Component teleportLink(Settings settings, String language, String regionId) {
        TagResolver region = Placeholder.unparsed(DisplayTag.REGION.key(), regionId);
        return text(settings, language, TELEPORT_LINK, region)
                .clickEvent(ClickEvent.runCommand(TELEPORT_COMMAND + regionId))
                .hoverEvent(HoverEvent.showText(text(settings, language, TELEPORT_HOVER, region)));
    }

    public Component runCommandLink(Component label, String command) {
        return label.clickEvent(ClickEvent.runCommand(command));
    }

    public Component regionPlayers(Settings settings, String language, int count) {
        String key = count == 1 ? "regions.players.one" : "regions.players.other";
        return text(settings, language, key, Placeholder.unparsed("count", Integer.toString(count)));
    }

    public Component players(Settings settings, String language, int count) {
        return players(settings, language, count == 1, Integer.toString(count));
    }

    private Component players(Settings settings, String language, boolean one, String count) {
        String key = one ? "players.one" : "players.other";
        return text(settings, language, key, Placeholder.unparsed("count", count));
    }

    Component parse(Settings settings, String language, String key, DisplayValues values) {
        return text(settings, language, key, region(settings, language, values));
    }

    public static TextColor color(HealthStatus status) {
        return switch (status) {
            case OK -> NamedTextColor.GREEN;
            case WARNING -> NamedTextColor.YELLOW;
            case CRITICAL -> NamedTextColor.RED;
            case UNAVAILABLE -> NamedTextColor.GRAY;
        };
    }
}
