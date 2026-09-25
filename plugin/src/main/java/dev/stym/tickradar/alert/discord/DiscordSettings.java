package dev.stym.tickradar.alert.discord;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import java.util.Set;

public record DiscordSettings(WebhookUrl webhook, String problem, Set<AlertLevel> levels, boolean includeCoordinates,
                              String username, DiscordTexts texts) {

    public static final String DEFAULT_USERNAME = "TickRadar";

    public DiscordSettings {
        levels = Set.copyOf(levels);
        username = username == null ? "" : username;
        texts = texts == null ? DiscordTexts.ENGLISH : texts;
    }

    public static DiscordSettings off() {
        return new DiscordSettings(null, null, Set.of(), false, DEFAULT_USERNAME, DiscordTexts.ENGLISH);
    }

    public static DiscordSettings from(ConfigSnapshot.Discord config, String language) {
        DiscordTexts texts = DiscordTexts.forLanguage(language);
        if (!config.isConfigured()) {
            return new DiscordSettings(null, null, config.levels(), config.includeCoordinates(), config.username(), texts);
        }
        try {
            WebhookUrl webhook = WebhookUrl.parse(config.webhookUrl());
            return new DiscordSettings(webhook, null, config.levels(), config.includeCoordinates(), config.username(), texts);
        } catch (InvalidWebhookUrlException e) {
            return new DiscordSettings(null, e.getMessage(), config.levels(), config.includeCoordinates(), config.username(), texts);
        }
    }

    public boolean isConfigured() {
        return webhook != null || problem != null;
    }

    public boolean isUsable() {
        return webhook != null;
    }

    public boolean accepts(DiscordAlert.Kind kind) {
        return kind.level() == null || levels.contains(kind.level());
    }

    public String maskedUrl() {
        return webhook == null ? "" : webhook.masked();
    }

    @Override
    public String toString() {
        return "DiscordSettings[webhook=" + maskedUrl() + ", problem=" + problem + ", levels=" + levels
                + ", includeCoordinates=" + includeCoordinates + ", username=" + username + "]";
    }
}
