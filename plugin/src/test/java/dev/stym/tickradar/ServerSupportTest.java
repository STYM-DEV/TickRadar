package dev.stym.tickradar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class ServerSupportTest {

    @Test
    void theNotFoliaMessageIsExactlyTheOneOfTheSpecification() {
        assertEquals(List.of(
                "TickRadar only runs on Folia (https://papermc.io/software/folia).",
                "This server runs Paper, which has a single main thread: use /tps or spark instead.",
                "TickRadar is now disabled. Nothing was changed on your server."), ServerSupport.notFoliaLines("Paper"));
    }

    @Test
    void aJvmWithoutServerIsNotFolia() {
        assertFalse(ServerSupport.isFolia());
    }
}
