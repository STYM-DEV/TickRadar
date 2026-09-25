package dev.stym.tickradar.alert.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.ConfigSnapshot.AlertLevel;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookUrlTest {

    static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcdefghijklmnopqrstuvwxyzABCD";
    static final String VALID = "https://discord.com/api/webhooks/123456789012345678/" + TOKEN;

    @ParameterizedTest
    @ValueSource(strings = {
            VALID,
            "https://discordapp.com/api/webhooks/123456789012345678/" + TOKEN,
            "https://ptb.discord.com/api/webhooks/123456789012345678/" + TOKEN,
            "https://canary.discord.com/api/webhooks/123456789012345678/" + TOKEN,
            "https://DISCORD.com/api/v10/webhooks/123456789012345678/" + TOKEN,
            "  https://discord.com/api/webhooks/123456789012345678/" + TOKEN + "  ",
            "https://discord.com/api/webhooks/123456789012345678/" + TOKEN + "?thread_id=987654321098765432",
            "https://discord.com:443/api/webhooks/123456789012345678/" + TOKEN})
    void discordWebhooksAreAccepted(String raw) throws InvalidWebhookUrlException {
        WebhookUrl url = WebhookUrl.parse(raw, null);
        assertFalse(url.toString().contains(TOKEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "http://discord.com/api/webhooks/123456789012345678/" + TOKEN,
            "https://evil.example.com/api/webhooks/123456789012345678/" + TOKEN,
            "https://discord.com.evil.example/api/webhooks/123456789012345678/" + TOKEN,
            "https://user:" + TOKEN + "@discord.com/api/webhooks/123456789012345678/" + TOKEN,
            "https://discord.com:8443/api/webhooks/123456789012345678/" + TOKEN,
            "https://discord.com/api/channels/123456789012345678/" + TOKEN,
            "https://discord.com/api/webhooks/notanumber/" + TOKEN,
            "https://discord.com/api/webhooks/123456789012345678/short",
            "https://discord.com/api/webhooks/123456789012345678/" + TOKEN + "?wait=true&x=" + TOKEN,
            "https://discord.com/api/webhooks/123456789012345678/" + TOKEN + "#" + TOKEN,
            "not a url at all " + TOKEN,
            "discord.com/api/webhooks/123456789012345678/" + TOKEN})
    void otherUrlsAreRefusedWithoutEchoingThem(String raw) {
        InvalidWebhookUrlException error = assertThrows(InvalidWebhookUrlException.class, () -> WebhookUrl.parse(raw, null));
        assertFalse(error.getMessage().contains(TOKEN));
        assertFalse(error.getMessage().contains("123456789012345678"));
        assertFalse(error.getMessage().contains("evil"));
        assertNull(error.getCause());
    }

    @Test
    void theMaskedFormHidesTheToken() throws InvalidWebhookUrlException {
        WebhookUrl url = WebhookUrl.parse(VALID, null);
        assertEquals("https://discord.com/api/webhooks/1234.../****", url.masked());
        assertEquals(url.masked(), url.toString());
    }

    @Test
    void redactionRemovesTheUrlAndTheToken() throws InvalidWebhookUrlException {
        WebhookUrl url = WebhookUrl.parse(VALID, null);
        String redacted = url.redact("failed to reach " + VALID + " with token " + TOKEN);
        assertFalse(redacted.contains(TOKEN));
        assertTrue(redacted.contains(url.masked()));
        assertEquals("", url.redact(null));
    }

    @Test
    void theTargetIsTheWebhookItself() throws InvalidWebhookUrlException {
        assertEquals(URI.create(VALID), WebhookUrl.parse(VALID, null).target());
        assertEquals(URI.create(VALID), WebhookUrl.parse(VALID, "  ").target());
    }

    @Test
    void theTestEndpointReplacesOnlyTheHost() throws InvalidWebhookUrlException {
        WebhookUrl url = WebhookUrl.parse(VALID + "?thread_id=12345678", "http://127.0.0.1:8099/");
        assertEquals(URI.create("http://127.0.0.1:8099/api/webhooks/123456789012345678/" + TOKEN + "?thread_id=12345678"),
                url.target());
        assertEquals("https://discord.com/api/webhooks/1234.../****", url.masked());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8099", "http://LOCALHOST", "http://[::1]:8099", "https://127.0.0.1",
            "http://127.255.0.9:1", "http://10.1.2.3:8099", "http://172.16.0.1", "http://172.31.255.255:80",
            "http://192.168.1.20:8099/mock"})
    void theTestEndpointAcceptsLocalAndPrivateHosts(String endpoint) throws InvalidWebhookUrlException {
        WebhookUrl url = WebhookUrl.parse(VALID, endpoint);
        assertNotEquals(URI.create(VALID), url.target());
        assertTrue(url.endpointNotice().contains("instead of Discord"), url.endpointNotice());
        assertFalse(url.endpointNotice().contains(TOKEN));
        assertFalse(url.endpointNotice().contains("123456789012345678"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://evil.example.com:8099", "https://8.8.8.8", "http://172.32.0.1", "http://172.15.0.1",
            "http://192.169.0.1", "http://11.0.0.1", "http://256.0.0.1", "http://[2001:db8::1]", "http://localhost.evil.com",
            "http://127.0.0.1.nip.io", "ftp://127.0.0.1", "http://user@127.0.0.1", "http://127.0.0.1/?x=1", "127.0.0.1:8099",
            "not a url"})
    void aForeignTestEndpointIsIgnoredWithANotice(String endpoint) throws InvalidWebhookUrlException {
        WebhookUrl url = WebhookUrl.parse(VALID, endpoint);
        assertEquals(URI.create(VALID), url.target());
        assertTrue(url.endpointNotice().contains("is ignored"), url.endpointNotice());
        assertFalse(url.endpointNotice().contains("evil"));
    }

    @Test
    void withoutTestEndpointThereIsNoNotice() throws InvalidWebhookUrlException {
        assertNull(WebhookUrl.parse(VALID, null).endpointNotice());
        assertNull(WebhookUrl.parse(VALID, " ").endpointNotice());
    }

    @Test
    void theTestEndpointNeverValidatesAForeignUrl() {
        assertThrows(InvalidWebhookUrlException.class,
                () -> WebhookUrl.parse("https://evil.example.com/api/webhooks/1234567/" + TOKEN, "http://127.0.0.1:8099"));
    }

    @Test
    void settingsFromConfigKeepTheProblemWithoutTheUrl() {
        ConfigSnapshot.Discord config = new ConfigSnapshot.Discord("http://discord.com/api/webhooks/123456789012345678/"
                + TOKEN, Set.of(AlertLevel.CRITICAL), true, "TickRadar");
        DiscordSettings settings = DiscordSettings.from(config, "fr");
        assertTrue(settings.isConfigured());
        assertFalse(settings.isUsable());
        assertEquals("the webhook URL must start with https://", settings.problem());
        assertEquals(DiscordTexts.FRENCH, settings.texts());
        assertFalse(settings.toString().contains(TOKEN));
    }

    @Test
    void settingsFromAValidConfigAreUsable() {
        DiscordSettings settings = DiscordSettings.from(new ConfigSnapshot.Discord(VALID, Set.of(AlertLevel.CRITICAL),
                false, "TickRadar"), "auto");
        assertTrue(settings.isUsable());
        assertNull(settings.problem());
        assertEquals(DiscordTexts.ENGLISH, settings.texts());
        assertFalse(settings.toString().contains(TOKEN));
        assertTrue(settings.toString().contains(settings.maskedUrl()));
    }

    @Test
    void anEmptyConfigIsNotConfigured() {
        DiscordSettings settings = DiscordSettings.from(new ConfigSnapshot.Discord("", Set.of(), false, "TickRadar"), "en");
        assertFalse(settings.isConfigured());
        assertFalse(settings.isUsable());
        assertEquals("", settings.maskedUrl());
    }
}
