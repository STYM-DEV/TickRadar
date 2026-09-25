package dev.stym.tickradar.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.alert.AlertEvent;
import dev.stym.tickradar.config.ConfigLoader;
import dev.stym.tickradar.config.ConfigSnapshot;
import dev.stym.tickradar.config.LoadResult;
import dev.stym.tickradar.engine.AlertKind;
import dev.stym.tickradar.engine.Anchor;
import dev.stym.tickradar.engine.AnchorTracker;
import dev.stym.tickradar.engine.BlockPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegionAlertsTest {

    private static final long SECOND = 1_000_000_000L;

    private final List<AlertEvent> events = new ArrayList<>();

    @Test
    void aRegionAlertsAfterTheTriggerSamplesThenEndsWhenExpired() {
        ConfigSnapshot config = config("alerts:\n  trigger-samples: 2\n");
        Anchor anchor = new AnchorTracker().attach(UUID.randomUUID(), true, "world", 0, 0, 0, SECOND, (x, z) -> true).anchor();
        measure(anchor, config, 60, 1);
        measure(anchor, config, 60, 2);
        RegionTracker.endAlert(anchor, events::add);
        assertEquals(List.of(AlertKind.CRITICAL, AlertKind.ENDED), events.stream().map(AlertEvent::kind).toList());
        assertEquals("R1", events.getFirst().regionId());
        assertEquals(60.0, events.getFirst().mspt());
    }

    @Test
    void disabledAlertsDoNotAdvanceTheMachine() {
        ConfigSnapshot config = config("alerts:\n  enabled: false\n  trigger-samples: 1\n");
        Anchor anchor = new AnchorTracker().attach(UUID.randomUUID(), true, "world", 0, 0, 0, SECOND, (x, z) -> true).anchor();
        measure(anchor, config, 60, 1);
        assertEquals(List.of(), events);
        assertEquals(false, anchor.alertState().isActive());
    }

    @Test
    void disablingAlertsClearsAnActiveAlertSilently() {
        ConfigSnapshot enabled = config("alerts:\n  trigger-samples: 1\n  recovery-samples: 1\n");
        ConfigSnapshot disabled = config("alerts:\n  enabled: false\n  trigger-samples: 1\n  recovery-samples: 1\n");
        Anchor anchor = new AnchorTracker().attach(UUID.randomUUID(), true, "world", 0, 0, 0, SECOND, (x, z) -> true).anchor();
        measure(anchor, enabled, 60, 1);
        assertTrue(anchor.alertState().isActive());
        measure(anchor, disabled, 60, 2);
        assertFalse(anchor.alertState().isActive());
        measure(anchor, enabled, 10, 3);
        RegionTracker.endAlert(anchor, events::add);
        assertEquals(List.of(AlertKind.CRITICAL), kinds());
    }

    @Test
    void disablingAlertsClearsAHeldBackAlertSilently() {
        ConfigSnapshot enabled = config("alerts:\n  trigger-samples: 1\n  recovery-samples: 1\n  cooldown-seconds: 300\n");
        ConfigSnapshot disabled =
                config("alerts:\n  enabled: false\n  trigger-samples: 1\n  recovery-samples: 1\n  cooldown-seconds: 300\n");
        Anchor anchor = new AnchorTracker().attach(UUID.randomUUID(), true, "world", 0, 0, 0, SECOND, (x, z) -> true).anchor();
        measure(anchor, enabled, 45, 1);
        assertTrue(anchor.alertState().isActive());
        assertFalse(anchor.alertState().pending());
        measure(anchor, enabled, 10, 2);
        assertFalse(anchor.alertState().isActive());
        measure(anchor, enabled, 45, 3);
        assertTrue(anchor.alertState().isActive());
        assertTrue(anchor.alertState().pending());
        measure(anchor, disabled, 45, 4);
        assertFalse(anchor.alertState().isActive());
        assertFalse(anchor.alertState().pending());
        measure(anchor, enabled, 10, 5);
        RegionTracker.endAlert(anchor, events::add);
        assertEquals(List.of(AlertKind.WARNING, AlertKind.RECOVERED), kinds());
    }

    @Test
    void reEnabledAlertsStartFromScratch() {
        ConfigSnapshot enabled = config("alerts:\n  trigger-samples: 1\n");
        ConfigSnapshot disabled = config("alerts:\n  enabled: false\n  trigger-samples: 1\n");
        Anchor anchor = new AnchorTracker().attach(UUID.randomUUID(), true, "world", 0, 0, 0, SECOND, (x, z) -> true).anchor();
        measure(anchor, enabled, 60, 1);
        measure(anchor, disabled, 60, 2);
        measure(anchor, enabled, 60, 3);
        assertEquals(List.of(AlertKind.CRITICAL, AlertKind.CRITICAL), kinds());
    }

    @Test
    void turningOffTheGlobalRegionAlertClearsItSilently() {
        Anchor global = Anchor.global(0);
        measure(global, config("alerts:\n  trigger-samples: 1\n  recovery-samples: 1\n"), 60, 1);
        measure(global, config("alerts:\n  trigger-samples: 1\n  recovery-samples: 1\n  global-region: false\n"), 60, 2);
        assertFalse(global.alertState().isActive());
        measure(global, config("alerts:\n  trigger-samples: 1\n  recovery-samples: 1\n"), 10, 3);
        assertEquals(List.of(AlertKind.CRITICAL), kinds());
    }

    @Test
    void aRegionAlertIsKeptWhenOnlyTheGlobalOneIsOff() {
        Anchor anchor = new AnchorTracker().attach(UUID.randomUUID(), true, "world", 0, 0, 0, SECOND, (x, z) -> true).anchor();
        measure(anchor, config("alerts:\n  trigger-samples: 1\n"), 60, 1);
        measure(anchor, config("alerts:\n  trigger-samples: 1\n  global-region: false\n"), 60, 2);
        assertTrue(anchor.alertState().isActive());
    }

    @Test
    void theGlobalRegionAlertsOnlyWhenAskedTo() {
        Anchor global = Anchor.global(0);
        measure(global, config("alerts:\n  trigger-samples: 1\n  global-region: false\n"), 60, 1);
        assertEquals(List.of(), events);
        measure(global, config("alerts:\n  trigger-samples: 1\n"), 60, 2);
        assertEquals(List.of("G"), events.stream().map(AlertEvent::regionId).toList());
    }

    private List<AlertKind> kinds() {
        return events.stream().map(AlertEvent::kind).toList();
    }

    private void measure(Anchor anchor, ConfigSnapshot config, double mspt, int second) {
        long now = second * SECOND;
        BlockPosition position = anchor.id().equals(Anchor.GLOBAL_ID) ? null : new BlockPosition(0, 64, 0);
        RegionTracker.advanceAlert(anchor, anchor.record(mspt, position, 1, now, 60), config, events::add, now);
    }

    private static ConfigSnapshot config(String yaml) {
        return ((LoadResult.Loaded<ConfigSnapshot>) ConfigLoader.load(ConfigLoader.FILE_NAME, yaml)).value();
    }
}
