package dev.stym.tickradar.alert.discord;

import dev.stym.tickradar.engine.ValueFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record DiscordTexts(
        char decimalSeparator,
        Sentence warning,
        Sentence critical,
        Sentence recovered,
        Sentence ended,
        String globalWarning,
        String globalCritical,
        String globalRecovered,
        String onePlayer,
        String manyPlayers,
        String test) {

    public record Sentence(String withCoordinates, String withoutCoordinates) {

        String pick(boolean coordinates) {
            return coordinates ? withCoordinates : withoutCoordinates;
        }
    }

    public static final DiscordTexts ENGLISH = new DiscordTexts(
            ValueFormat.DECIMAL_POINT,
            new Sentence(
                    "Region {region} in world \"{world}\" around X {x}, Z {z} ({players}) is at {mspt} ms (warning).",
                    "Region {region} in world \"{world}\" ({players}) is at {mspt} ms (warning)."),
            new Sentence(
                    "Region {region} in world \"{world}\" around X {x}, Z {z} ({players}) is at {mspt} ms (critical).",
                    "Region {region} in world \"{world}\" ({players}) is at {mspt} ms (critical)."),
            new Sentence(
                    "Region {region} in world \"{world}\" around X {x}, Z {z} has recovered: {mspt} ms.",
                    "Region {region} in world \"{world}\" has recovered: {mspt} ms."),
            new Sentence(
                    "Region {region} in world \"{world}\" around X {x}, Z {z} is no longer observed: no players are left there.",
                    "Region {region} in world \"{world}\" is no longer observed: no players are left there."),
            "The global region {region} is at {mspt} ms (warning).",
            "The global region {region} is at {mspt} ms (critical).",
            "The global region {region} has recovered: {mspt} ms.",
            "1 player",
            "{count} players",
            "Test message from TickRadar: Discord alerts reach this channel.");

    public static final DiscordTexts FRENCH = new DiscordTexts(
            ValueFormat.DECIMAL_COMMA,
            new Sentence(
                    "La région {region} du monde «\u00A0{world}\u00A0», vers X {x}, Z {z} ({players}), est à {mspt} ms (ralentie).",
                    "La région {region} du monde «\u00A0{world}\u00A0» ({players}) est à {mspt} ms (ralentie)."),
            new Sentence(
                    "La région {region} du monde «\u00A0{world}\u00A0», vers X {x}, Z {z} ({players}), est à {mspt} ms (critique).",
                    "La région {region} du monde «\u00A0{world}\u00A0» ({players}) est à {mspt} ms (critique)."),
            new Sentence(
                    "La région {region} du monde «\u00A0{world}\u00A0», vers X {x}, Z {z}, est revenue à la normale\u00A0: {mspt} ms.",
                    "La région {region} du monde «\u00A0{world}\u00A0» est revenue à la normale\u00A0: {mspt} ms."),
            new Sentence(
                    "La région {region} du monde «\u00A0{world}\u00A0», vers X {x}, Z {z}, n'est plus observée\u00A0: il n'y reste aucun joueur.",
                    "La région {region} du monde «\u00A0{world}\u00A0» n'est plus observée\u00A0: il n'y reste aucun joueur."),
            "La région globale {region} est à {mspt} ms (ralentie).",
            "La région globale {region} est à {mspt} ms (critique).",
            "La région globale {region} est revenue à la normale\u00A0: {mspt} ms.",
            "1 joueur",
            "{count} joueurs",
            "Message de test de TickRadar\u00A0: les alertes Discord arrivent dans ce salon.");

    public static DiscordTexts forLanguage(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).equals("fr") ? FRENCH : ENGLISH;
    }

    public String render(DiscordAlert alert, boolean includeCoordinates) {
        if (alert.kind() == DiscordAlert.Kind.TEST) {
            return test;
        }
        boolean located = includeCoordinates && alert.hasCoordinates();
        String template = alert.isGlobal() ? globalTemplate(alert.kind()) : regionSentence(alert.kind()).pick(located);
        Map<String, String> values = new HashMap<>();
        values.put("region", DiscordMarkdown.escape(alert.regionId()));
        values.put("world", DiscordMarkdown.escape(alert.world()));
        values.put("players", players(alert.players()));
        values.put("mspt", ValueFormat.mspt(alert.mspt(), decimalSeparator));
        if (located) {
            values.put("x", Integer.toString(alert.coordinates().x()));
            values.put("z", Integer.toString(alert.coordinates().z()));
        }
        return Templates.fill(template, values);
    }

    private Sentence regionSentence(DiscordAlert.Kind kind) {
        return switch (kind) {
            case WARNING -> warning;
            case CRITICAL -> critical;
            case RECOVERED -> recovered;
            case ENDED, TEST -> ended;
        };
    }

    private String globalTemplate(DiscordAlert.Kind kind) {
        return switch (kind) {
            case WARNING -> globalWarning;
            case CRITICAL -> globalCritical;
            case RECOVERED, ENDED, TEST -> globalRecovered;
        };
    }

    private String players(int count) {
        return count == 1 ? onePlayer : Templates.fill(manyPlayers, Map.of("count", Integer.toString(count)));
    }
}
