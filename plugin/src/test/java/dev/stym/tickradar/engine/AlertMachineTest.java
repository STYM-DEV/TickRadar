package dev.stym.tickradar.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AlertMachineTest {

    private static final long SECOND = 1_000_000_000L;
    private static final AlertRules RULES = AlertRules.of(new Thresholds(40, 50), 3, 4, 10);
    private static final String SILENT_CRITICAL = "45 45 55 55 55 30 30 30 30 55 55 55";

    static Stream<Arguments> scenarios() {
        return Stream.of(
                row("rise to warning", "41 41 41", ". . W", HealthStatus.WARNING),
                row("rise straight to critical", "55 55 55", ". . C", HealthStatus.CRITICAL),
                row("oscillation around the warning threshold", "41 39 41 39 41 39 41 39", ". . . . . . . .", HealthStatus.OK),
                row("trigger not reached", "41 41 30 41 41", ". . . . .", HealthStatus.OK),
                row("exactly on the threshold counts", "40 40 40", ". . W", HealthStatus.WARNING),
                row("escalation from warning to critical", "45 45 45 55 55 55", ". . W . . C", HealthStatus.CRITICAL),
                row("escalation counts only critical samples in a row", "45 45 45 55 55 45 55 55", ". . W . . . . .",
                        HealthStatus.WARNING),
                row("no de-escalation from critical to warning", "55 55 55 45 45 45 45 45", ". . C . . . . .",
                        HealthStatus.CRITICAL),
                row("recovery after enough samples", "45 45 45 30 30 30 30", ". . W . . . R", HealthStatus.OK),
                row("recovery from critical", "55 55 55 30 30 30 30", ". . C . . . R", HealthStatus.OK),
                row("hysteresis: one slow sample restarts the recovery", "45 45 45 30 30 30 41 30 30 30 30",
                        ". . W . . . . . . . R", HealthStatus.OK),
                row("cooldown: no second warning", "45 45 45 30 30 30 30 45 45 45", ". . W . . . R . . .",
                        HealthStatus.WARNING),
                row("a silent alert does not recover aloud", "45 45 45 30 30 30 30 45 45 45 30 30 30 30",
                        ". . W . . . R . . . . . . .", HealthStatus.OK),
                row("warning again after the cooldown", "45 45 45 30 30 30 30 +10 45 45 45", ". . W . . . R . . . W",
                        HealthStatus.WARNING),
                row("critical is sent inside the cooldown of a warning", "45 45 45 30 30 30 30 55 55 55",
                        ". . W . . . R . . C", HealthStatus.CRITICAL),
                row("critical has its own cooldown, the free warning is sent", "55 55 55 30 30 30 30 55 55 55",
                        ". . C . . . R . . W", HealthStatus.CRITICAL),
                row("a critical and a warning both held back say nothing", "45 45 55 55 55 30 30 30 30 55 55 55",
                        ". . W . C . . . R . . .", HealthStatus.CRITICAL),
                row("end when no player is left", "55 55 55 END", ". . C E", HealthStatus.OK),
                row("end without alert says nothing", "30 41 END", ". . .", HealthStatus.OK),
                row("end of a silent alert says nothing", "45 45 45 30 30 30 30 45 45 45 END", ". . W . . . R . . . .",
                        HealthStatus.OK),
                row("unavailable samples are neither counted nor reset", "41 NaN 41 NaN 41", ". . . . W",
                        HealthStatus.WARNING),
                row("reload lowers the thresholds", "30 30 @20/45 30 30 30", ". . . . . W", HealthStatus.WARNING),
                row("reload raises the thresholds", "45 45 45 @60/70 45 45 45 45", ". . W . . . . R", HealthStatus.OK),
                row("a warning held back by the cooldown is sent when it ends", "45 45 45 30 30 30 30 45 45 45 45 45 45",
                        ". . W . . . R . . . . . W", HealthStatus.WARNING),
                row("a held back warning needs the trigger again after a fast sample",
                        "45 45 45 30 30 30 30 45 45 45 30 45 45 45", ". . W . . . R . . . . . . W", HealthStatus.WARNING),
                row("a critical held back by its cooldown is sent when it ends", "55 55 55 30 30 30 30 55 55 55 55 55 55",
                        ". . C . . . R . . W . . C", HealthStatus.CRITICAL),
                row("a held back critical sends the free warning, then itself when its cooldown ends",
                        "55 55 55 30 30 30 30 55 55 55 55 55 55 55 30 30 30 30", ". . C . . . R . . W . . C . . . . R",
                        HealthStatus.OK),
                row("a held back critical that recovers after its free warning recovers aloud",
                        "55 55 55 30 30 30 30 55 55 55 30 30 30 30", ". . C . . . R . . W . . . R", HealthStatus.OK),
                row("an escalation held back by the critical cooldown is sent when it ends",
                        "55 55 55 30 30 30 30 45 45 55 55 55 55 55", ". . C . . . R . . W . . C .", HealthStatus.CRITICAL),
                row("a held back critical sends the free warning, not itself, while only above the warning",
                        "55 55 55 30 30 30 30 55 55 55 45 45 45 45", ". . C . . . R . . W . . . .", HealthStatus.CRITICAL),
                row("a held back critical sends the warning when the warning cooldown ends first",
                        "45 45 55 55 55 30 30 30 30 55 55 55 45 45 45", ". . W . C . . . R . . . W . .",
                        HealthStatus.CRITICAL),
                rowWithCooldown("a warning held back at the escalation is sent when its cooldown ends", 60,
                        "45 45 55 55 55 30 30 30 30 +20 45 45 45 55 55 55 +30 45 45",
                        ". . W . C . . . R . . . . . . . . W .", HealthStatus.CRITICAL),
                rowWithCooldown("a critical and a warning both held back stay silent at the warning level", 60,
                        "45 45 55 55 55 30 30 30 30 55 55 55 45 45 45 45", ". . W . C . . . R . . . . . . .",
                        HealthStatus.CRITICAL),
                row("raising the thresholds while a warning is held back cancels it silently",
                        "45 45 45 30 30 30 30 45 45 45 @60/70 45 45 45 30", ". . W . . . R . . . . . . . .", HealthStatus.OK));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name, int cooldownSeconds, String samples, String expected, HealthStatus finalLevel) {
        AlertState state = AlertState.INITIAL;
        AlertRules rules = AlertRules.of(RULES.thresholds(), 3, 4, cooldownSeconds);
        long now = 0;
        List<String> events = new ArrayList<>();
        for (String token : samples.split(" ")) {
            Optional<AlertKind> event = Optional.empty();
            if (token.equals("END")) {
                AlertMachine.Step step = AlertMachine.end(state);
                state = step.state();
                event = step.event();
            } else if (token.startsWith("+")) {
                now += Long.parseLong(token.substring(1)) * SECOND;
            } else if (token.startsWith("@")) {
                String[] limits = token.substring(1).split("/");
                rules = AlertRules.of(new Thresholds(Double.parseDouble(limits[0]), Double.parseDouble(limits[1])), 3, 4,
                        cooldownSeconds);
            } else {
                now += SECOND;
                AlertMachine.Step step = AlertMachine.sample(state, Double.parseDouble(token), rules, now);
                state = step.state();
                event = step.event();
            }
            events.add(event.map(kind -> kind.name().substring(0, 1)).orElse("."));
        }
        assertEquals(expected, String.join(" ", events), name);
        assertEquals(finalLevel, state.level(), name);
    }

    @Test
    void aMergeKeepsTheWorstStateAndTheLatestCooldowns() {
        AlertState warning = run("45 45 45");
        AlertState critical = run("55 55 55");
        AlertState merged = AlertMachine.merge(warning, critical);
        assertEquals(HealthStatus.CRITICAL, merged.level());
        assertTrue(merged.emitted());
        assertEquals(3 * SECOND, merged.lastWarningNanos());
        assertEquals(3 * SECOND, merged.lastCriticalNanos());
        assertEquals(HealthStatus.CRITICAL, AlertMachine.merge(critical, AlertState.INITIAL).level());
    }

    @Test
    void theMergedStateRecoversLikeTheWorstOne() {
        AlertState merged = AlertMachine.merge(AlertState.INITIAL, run("55 55 55"));
        long now = 10 * SECOND;
        Optional<AlertKind> last = Optional.empty();
        for (int i = 0; i < 4; i++) {
            AlertMachine.Step step = AlertMachine.sample(merged, 30, RULES, now += SECOND);
            merged = step.state();
            last = step.event();
        }
        assertEquals(Optional.of(AlertKind.RECOVERED), last);
    }

    @Test
    void aSilentCriticalThatAbsorbsAShownWarningRecoversAloud() {
        AlertState silentCritical = run(SILENT_CRITICAL);
        AlertState shownWarning = run("45 45 45");
        assertEquals(HealthStatus.CRITICAL, silentCritical.level());
        assertFalse(silentCritical.emitted());
        assertTrue(shownWarning.emitted());
        AlertState merged = AlertMachine.merge(silentCritical, shownWarning);
        assertEquals(HealthStatus.CRITICAL, merged.level());
        assertEquals(Optional.of(AlertKind.RECOVERED), recoverFully(merged));
    }

    @Test
    void twoSilentAlertsMergeIntoASilentOne() {
        AlertState silentCritical = run(SILENT_CRITICAL);
        AlertState merged = AlertMachine.merge(AlertState.INITIAL, silentCritical);
        assertFalse(merged.emitted());
        assertEquals(Optional.empty(), recoverFully(merged));
    }

    @Test
    void aHeldBackWarningMergedWithAnAnnouncedOneIsNotSentTwice() {
        AlertState heldBack = run("45 45 45 30 30 30 30 45 45 45");
        AlertState announced = run("45 45 45");
        assertTrue(heldBack.pending());
        assertFalse(announced.pending());
        AlertState merged = AlertMachine.merge(announced, heldBack);
        assertFalse(merged.pending());
        assertEquals(Optional.empty(), stayWarning(merged));
    }

    @Test
    void aHeldBackWarningMergedIntoAQuietAnchorIsStillSent() {
        AlertState merged = AlertMachine.merge(AlertState.INITIAL, run("45 45 45 30 30 30 30 45 45 45"));
        assertTrue(merged.pending());
        assertEquals(Optional.of(AlertKind.WARNING), stayWarning(merged));
    }

    @Test
    void aHeldBackCriticalThatSentItsWarningDoesNotSendItAgainAfterAMerge() {
        AlertState warned = run("55 55 55 30 30 30 30 55 55 55");
        assertTrue(warned.pending());
        assertTrue(warned.emitted());
        AlertState merged = AlertMachine.merge(AlertState.INITIAL, warned);
        AlertMachine.Step step = AlertMachine.sample(merged, 45, RULES, 100 * SECOND);
        assertEquals(HealthStatus.CRITICAL, step.state().level());
        assertEquals(Optional.empty(), step.event());
    }

    @Test
    void aResetForgetsTheAlertSilently() {
        AlertMachine.Step step = AlertMachine.reset(run("55 55 55"));
        assertEquals(AlertState.INITIAL, step.state());
        assertEquals(Optional.empty(), step.event());
    }

    @Test
    void aResetForgetsAHeldBackAlertSilently() {
        AlertState heldBack = run("45 45 45 30 30 30 30 45 45 45");
        assertTrue(heldBack.pending());
        assertTrue(heldBack.isActive());
        AlertMachine.Step step = AlertMachine.reset(heldBack);
        assertEquals(AlertState.INITIAL, step.state());
        assertEquals(Optional.empty(), step.event());
    }

    @Test
    void anAnchorResetsItsAlertWithoutEvent() {
        Anchor anchor = Anchor.global(0);
        for (int i = 1; i <= 3; i++) {
            anchor.advanceAlert(60, RULES, i * SECOND);
        }
        anchor.resetAlert();
        assertEquals(AlertState.INITIAL, anchor.alertState());
        assertEquals(Optional.empty(), anchor.endAlert());
        anchor.resetAlert();
        assertEquals(AlertState.INITIAL, anchor.alertState());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void anAnchorSendsTheSameEventsAsTheMachine(String name, int cooldownSeconds, String samples, String expected,
                                                HealthStatus finalLevel) {
        Anchor anchor = Anchor.global(0);
        AlertRules rules = AlertRules.of(RULES.thresholds(), 3, 4, cooldownSeconds);
        long now = 0;
        List<String> events = new ArrayList<>();
        for (String token : samples.split(" ")) {
            Optional<AlertKind> event = Optional.empty();
            if (token.equals("END")) {
                event = anchor.endAlert();
            } else if (token.startsWith("+")) {
                now += Long.parseLong(token.substring(1)) * SECOND;
            } else if (token.startsWith("@")) {
                String[] limits = token.substring(1).split("/");
                rules = AlertRules.of(new Thresholds(Double.parseDouble(limits[0]), Double.parseDouble(limits[1])), 3, 4,
                        cooldownSeconds);
            } else {
                now += SECOND;
                event = anchor.advanceAlert(Double.parseDouble(token), rules, now);
            }
            events.add(event.map(kind -> kind.name().substring(0, 1)).orElse("."));
        }
        assertEquals(expected, String.join(" ", events), name);
        assertEquals(finalLevel, anchor.alertState().level(), name);
    }

    @Test
    void aQuietRegionKeepsItsAlertStateWithoutRebuildingIt() {
        Anchor anchor = Anchor.global(0);
        for (int i = 1; i <= RULES.recoverySamples(); i++) {
            anchor.advanceAlert(10, RULES, i * SECOND);
        }
        AlertState settled = anchor.alertState();
        assertTrue(AlertMachine.isSteady(settled, 10, RULES));
        for (int i = 0; i < 100; i++) {
            assertEquals(Optional.empty(), anchor.advanceAlert(10, RULES, (10 + i) * SECOND));
        }
        assertSame(settled, anchor.alertState());
    }

    @Test
    void aSlowOrUnavailableSampleIsNeverSteady() {
        AlertState settled = new AlertState(HealthStatus.OK, 0, 0, RULES.recoverySamples(), false, false, AlertState.NEVER,
                AlertState.NEVER);
        assertTrue(AlertMachine.isSteady(settled, 39.9, RULES));
        assertFalse(AlertMachine.isSteady(settled, 40, RULES));
        assertFalse(AlertMachine.isSteady(settled, Double.NaN, RULES));
        assertFalse(AlertMachine.isSteady(AlertState.INITIAL, 10, RULES));
        AlertState pending = new AlertState(HealthStatus.OK, 0, 0, RULES.recoverySamples(), false, true, AlertState.NEVER,
                AlertState.NEVER);
        assertFalse(AlertMachine.isSteady(pending, 10, RULES));
    }

    @Test
    void countersStopAtTheirLimit() {
        AlertState state = new AlertState(HealthStatus.OK, 0, 0, AlertMachine.COUNTER_LIMIT, false, false, AlertState.NEVER,
                AlertState.NEVER);
        assertEquals(AlertMachine.COUNTER_LIMIT, AlertMachine.sample(state, 10, RULES, SECOND).state().belowWarning());
    }

    @Test
    void anAnchorAdvancesItsAlertAtomically() {
        Anchor anchor = Anchor.global(0);
        assertFalse(anchor.alertState().isActive());
        for (int i = 1; i <= 2; i++) {
            assertEquals(Optional.empty(), anchor.advanceAlert(60, RULES, i * SECOND));
        }
        assertEquals(Optional.of(AlertKind.CRITICAL), anchor.advanceAlert(60, RULES, 3 * SECOND));
        assertTrue(anchor.alertState().isActive());
        assertEquals(Optional.of(AlertKind.ENDED), anchor.endAlert());
        assertFalse(anchor.alertState().isActive());
    }

    private static Optional<AlertKind> stayWarning(AlertState state) {
        AlertMachine.Step step = AlertMachine.sample(state, 45, RULES, 100 * SECOND);
        assertEquals(HealthStatus.WARNING, step.state().level());
        return step.event();
    }

    private static Optional<AlertKind> recoverFully(AlertState state) {
        long now = 100 * SECOND;
        Optional<AlertKind> last = Optional.empty();
        for (int i = 0; i < RULES.recoverySamples(); i++) {
            AlertMachine.Step step = AlertMachine.sample(state, 30, RULES, now += SECOND);
            state = step.state();
            last = step.event();
        }
        return last;
    }

    private static AlertState run(String samples) {
        AlertState state = AlertState.INITIAL;
        long now = 0;
        for (String token : samples.split(" ")) {
            state = AlertMachine.sample(state, Double.parseDouble(token), RULES, now += SECOND).state();
        }
        return state;
    }

    private static Arguments row(String name, String samples, String expected, HealthStatus finalLevel) {
        return rowWithCooldown(name, 10, samples, expected, finalLevel);
    }

    private static Arguments rowWithCooldown(String name, int cooldownSeconds, String samples, String expected,
            HealthStatus finalLevel) {
        return Arguments.of(name, cooldownSeconds, samples, expected, finalLevel);
    }
}
