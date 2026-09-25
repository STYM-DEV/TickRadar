package dev.stym.tickradar.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.stym.tickradar.config.ConfigSnapshot.DisplayKind;
import org.junit.jupiter.api.Test;

class DisplayPrefsTest {

    @Test
    void unsetChoicesFollowTheServerDefault() {
        assertTrue(DisplayPrefs.NONE.isShown(DisplayKind.BOSSBAR, true));
        assertFalse(DisplayPrefs.NONE.isShown(DisplayKind.BOSSBAR, false));
    }

    @Test
    void explicitChoicesWinOverTheDefault() {
        DisplayPrefs prefs = DisplayPrefs.NONE.with(DisplayKind.BOSSBAR, true).with(DisplayKind.TAB, false);
        assertTrue(prefs.isShown(DisplayKind.BOSSBAR, false));
        assertFalse(prefs.isShown(DisplayKind.TAB, true));
        assertTrue(prefs.isShown(DisplayKind.ACTIONBAR, true));
        assertTrue(DisplayPrefs.NONE.encode().isEmpty());
    }

    @Test
    void encodingRoundTrips() {
        DisplayPrefs prefs = DisplayPrefs.NONE.with(DisplayKind.ACTIONBAR, true).with(DisplayKind.TAB, false);
        assertEquals("actionbar=on;tab=off", prefs.encode());
        assertEquals(prefs, DisplayPrefs.decode(prefs.encode()));
    }

    @Test
    void damagedValuesAreIgnored() {
        assertSame(DisplayPrefs.NONE, DisplayPrefs.decode(null));
        assertSame(DisplayPrefs.NONE, DisplayPrefs.decode(" "));
        DisplayPrefs prefs = DisplayPrefs.decode("bossbar=ON;radar=on;tab;actionbar=perhaps");
        assertEquals(DisplayPrefs.NONE.with(DisplayKind.BOSSBAR, true), prefs);
    }

    @Test
    void alertsAreOnUntilTurnedOff() {
        assertTrue(DisplayPrefs.NONE.alertsOn());
        DisplayPrefs off = DisplayPrefs.NONE.with(DisplayKind.TAB, true).withAlerts(false);
        assertFalse(off.alertsOn());
        assertEquals("tab=on;alerts=off", off.encode());
        assertEquals(off, DisplayPrefs.decode(off.encode()));
        assertTrue(off.withAlerts(true).alertsOn());
        assertTrue(off.isShown(DisplayKind.TAB, false));
    }

    @Test
    void aDamagedAlertsChoiceKeepsTheAlertsOn() {
        assertTrue(DisplayPrefs.decode("alerts=maybe").alertsOn());
        assertFalse(DisplayPrefs.decode("alerts=OFF").alertsOn());
    }
}
