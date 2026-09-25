package dev.stym.tickradar.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.stym.tickradar.alert.discord.DiscordTestResult;
import dev.stym.tickradar.alert.discord.DiscordTestResult.Outcome;
import dev.stym.tickradar.config.ConfigFiles;
import dev.stym.tickradar.config.LangBundle;
import dev.stym.tickradar.config.LoadResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiscordTestKeyTest {

    @Test
    void aDeliveredTestShowsTheRateLimitWaitOnlyWhenThereWasOne() {
        assertEquals("command.discord-test-sent", key(Outcome.SENT, 204, Duration.ofMillis(20)));
        assertEquals("command.discord-test-sent-waited", key(Outcome.SENT, 204, Duration.ofSeconds(4)));
    }

    @Test
    void aRejectedWebhookShowsItsStatus() {
        assertEquals("command.discord-test-rejected", key(Outcome.REJECTED, 404, Duration.ZERO));
        assertEquals("command.discord-test-rejected", key(Outcome.DISABLED, 401, Duration.ZERO));
    }

    @Test
    void aWebhookRemovedBeforeTheSendIsReportedAsNotConfigured() {
        assertEquals("command.discord-test-not-configured", key(Outcome.DISABLED, DiscordTestResult.NO_STATUS, Duration.ZERO));
    }

    @Test
    void otherOutcomesHaveTheirOwnText() {
        assertEquals("command.discord-test-not-configured", key(Outcome.NOT_CONFIGURED, DiscordTestResult.NO_STATUS, Duration.ZERO));
        assertEquals("command.discord-test-invalid-url", key(Outcome.INVALID_URL, DiscordTestResult.NO_STATUS, Duration.ZERO));
        assertEquals("command.discord-test-failed", key(Outcome.FAILED, 500, Duration.ZERO));
        assertEquals("command.discord-test-dropped", key(Outcome.DROPPED, DiscordTestResult.NO_STATUS, Duration.ZERO));
        assertEquals("command.discord-test-stopped", key(Outcome.STOPPED, DiscordTestResult.NO_STATUS, Duration.ZERO));
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void everyTextExistsInEveryBuiltInLanguage(Outcome outcome) throws IOException {
        LangBundle bundle = builtInBundle();
        for (int status : List.of(DiscordTestResult.NO_STATUS, 204)) {
            for (Duration wait : List.of(Duration.ZERO, Duration.ofSeconds(3))) {
                String key = key(outcome, status, wait);
                for (String language : ConfigFiles.BUILT_IN_LANGUAGES) {
                    assertTrue(bundle.keys(language).contains(key), language + " misses " + key);
                }
            }
        }
        for (String language : ConfigFiles.BUILT_IN_LANGUAGES) {
            assertTrue(bundle.keys(language).containsAll(List.of("command.discord-test-rcon", "command.discord-test-sending",
                    "command.help.discord")), language);
        }
    }

    private static String key(Outcome outcome, int status, Duration wait) {
        return TickRadarCommand.discordTestKey(new DiscordTestResult(outcome, status, wait, ""));
    }

    private static LangBundle builtInBundle() throws IOException {
        Map<String, String> files = new HashMap<>();
        for (String language : ConfigFiles.BUILT_IN_LANGUAGES) {
            try (InputStream in = DiscordTestKeyTest.class.getClassLoader().getResourceAsStream("lang/" + language + ".yml")) {
                files.put(language, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        LoadResult<LangBundle> result = LangBundle.load(files, Map.of());
        if (result instanceof LoadResult.Loaded<LangBundle> loaded) {
            return loaded.value();
        }
        return fail("the built-in messages cannot be read: " + result);
    }
}
