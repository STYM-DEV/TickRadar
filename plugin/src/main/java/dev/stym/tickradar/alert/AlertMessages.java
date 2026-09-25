package dev.stym.tickradar.alert;

import dev.stym.tickradar.config.Settings;
import dev.stym.tickradar.display.Renderer;
import dev.stym.tickradar.engine.BlockPosition;
import dev.stym.tickradar.engine.ValueFormat;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class AlertMessages {

    private final Renderer renderer;

    AlertMessages(Renderer renderer) {
        this.renderer = renderer;
    }

    Component forPlayer(Settings settings, String language, AlertEvent event, boolean canTeleport) {
        return renderer.message(settings, language, key(event), tags(settings, language, event, canTeleport));
    }

    String forConsole(Settings settings, String language, AlertEvent event) {
        Component text = renderer.text(settings, language, key(event), tags(settings, language, event, false));
        return PlainTextComponentSerializer.plainText().serialize(text).strip();
    }

    private static String key(AlertEvent event) {
        String prefix = event.isGlobal() ? "alert.global-" : "alert.";
        return switch (event.kind()) {
            case WARNING, CRITICAL -> prefix + "slow";
            case RECOVERED -> prefix + "recovered";
            case ENDED -> event.isGlobal() ? prefix + "recovered" : prefix + "ended";
        };
    }

    private TagResolver tags(Settings settings, String language, AlertEvent event, boolean withTeleport) {
        Optional<BlockPosition> position = event.position();
        boolean teleport = withTeleport && event.kind().isSlow() && position.isPresent() && !event.isGlobal();
        return TagResolver.resolver(
                Placeholder.unparsed("region", event.regionId()),
                Placeholder.unparsed("mspt", ValueFormat.mspt(event.mspt(), renderer.decimalSeparator(settings, language))),
                Placeholder.unparsed("world", event.world()),
                Placeholder.unparsed("x", position.map(at -> Integer.toString(at.x())).orElse(ValueFormat.MISSING)),
                Placeholder.unparsed("z", position.map(at -> Integer.toString(at.z())).orElse(ValueFormat.MISSING)),
                Placeholder.unparsed("players", Integer.toString(event.players())),
                Placeholder.component("region_players", renderer.regionPlayers(settings, language, event.players())),
                Placeholder.component("status", renderer.text(settings, language, "status." + event.status().key())),
                Placeholder.styling("status_color", Renderer.color(event.status())),
                Placeholder.component("teleport", teleport ? renderer.teleportLink(settings, language, event.regionId())
                        : Component.empty()));
    }
}
