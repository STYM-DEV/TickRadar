package dev.stym.tickradar.engine;

import java.util.Comparator;
import java.util.Optional;

public final class AlertMachine {

    static final int COUNTER_LIMIT = 1_000_000;

    private static final Comparator<AlertState> MOST_ADVANCED = Comparator
            .comparing(AlertState::level, Comparator.comparingInt(AlertMachine::rank))
            .thenComparingInt(AlertState::aboveCritical)
            .thenComparingInt(AlertState::aboveWarning)
            .thenComparing(AlertState::emitted);

    public record Step(AlertState state, Optional<AlertKind> event) {
    }

    private AlertMachine() {
    }

    public static Step sample(AlertState state, double mspt, AlertRules rules, long nowNanos) {
        if (!ValueFormat.isUsable(mspt)) {
            return new Step(state, Optional.empty());
        }
        Thresholds thresholds = rules.thresholds();
        AlertState counted = new AlertState(state.level(),
                mspt >= thresholds.warning() ? increment(state.aboveWarning()) : 0,
                mspt >= thresholds.critical() ? increment(state.aboveCritical()) : 0,
                mspt < thresholds.warning() ? increment(state.belowWarning()) : 0,
                state.emitted(), state.pending(), state.lastWarningNanos(), state.lastCriticalNanos());
        int trigger = rules.triggerSamples();
        if (counted.level() != HealthStatus.CRITICAL && counted.isTriggered(HealthStatus.CRITICAL, trigger)) {
            return raise(counted, HealthStatus.CRITICAL, rules, nowNanos);
        }
        if (counted.level() == HealthStatus.OK && counted.isTriggered(HealthStatus.WARNING, trigger)) {
            return raise(counted, HealthStatus.WARNING, rules, nowNanos);
        }
        if (counted.pending() && counted.isTriggered(counted.level(), trigger)) {
            return raise(counted, counted.level(), rules, nowNanos);
        }
        if (counted.pending() && counted.level() == HealthStatus.CRITICAL && canReleaseWarning(counted, rules, nowNanos)) {
            return releaseWarning(counted, nowNanos);
        }
        if (counted.isActive() && counted.belowWarning() >= rules.recoverySamples()) {
            return recover(counted, AlertKind.RECOVERED);
        }
        return new Step(counted, Optional.empty());
    }

    public static boolean isSteady(AlertState state, double mspt, AlertRules rules) {
        return state.level() == HealthStatus.OK && !state.pending() && state.aboveWarning() == 0 && state.aboveCritical() == 0
                && state.belowWarning() >= rules.recoverySamples() && ValueFormat.isUsable(mspt)
                && mspt < rules.thresholds().warning();
    }

    public static Step end(AlertState state) {
        if (!state.isActive()) {
            return new Step(state, Optional.empty());
        }
        return recover(state, AlertKind.ENDED);
    }

    public static Step reset(AlertState state) {
        return new Step(AlertState.INITIAL, Optional.empty());
    }

    public static AlertState merge(AlertState kept, AlertState absorbed) {
        boolean absorbedIsAhead = MOST_ADVANCED.compare(absorbed, kept) > 0;
        AlertState advanced = absorbedIsAhead ? absorbed : kept;
        AlertState other = absorbedIsAhead ? kept : absorbed;
        boolean alreadyAnnounced = other.level() == advanced.level() && other.isAnnounced();
        return new AlertState(advanced.level(), advanced.aboveWarning(), advanced.aboveCritical(), advanced.belowWarning(),
                advanced.emitted() || other.emitted(), advanced.pending() && !alreadyAnnounced,
                latest(kept.lastWarningNanos(), absorbed.lastWarningNanos()),
                latest(kept.lastCriticalNanos(), absorbed.lastCriticalNanos()));
    }

    private static Step raise(AlertState state, HealthStatus level, AlertRules rules, long nowNanos) {
        if (state.isCoolingDown(level, nowNanos, rules.cooldownNanos())) {
            return holdBack(state, level, rules, nowNanos);
        }
        AlertState raised = new AlertState(level, state.aboveWarning(), state.aboveCritical(), state.belowWarning(), true,
                false, level == HealthStatus.WARNING ? nowNanos : state.lastWarningNanos(),
                level == HealthStatus.CRITICAL ? nowNanos : state.lastCriticalNanos());
        return new Step(raised, Optional.of(kindOf(level)));
    }

    private static Step holdBack(AlertState state, HealthStatus level, AlertRules rules, long nowNanos) {
        AlertState heldBack = withLevel(state, level, state.emitted(), true);
        if (level == HealthStatus.CRITICAL && canReleaseWarning(heldBack, rules, nowNanos)) {
            return releaseWarning(heldBack, nowNanos);
        }
        return new Step(heldBack, Optional.empty());
    }

    private static boolean canReleaseWarning(AlertState heldBackCritical, AlertRules rules, long nowNanos) {
        return !heldBackCritical.emitted()
                && heldBackCritical.isTriggered(HealthStatus.WARNING, rules.triggerSamples())
                && !heldBackCritical.isCoolingDown(HealthStatus.WARNING, nowNanos, rules.cooldownNanos());
    }

    private static Step releaseWarning(AlertState heldBackCritical, long nowNanos) {
        AlertState warned = new AlertState(HealthStatus.CRITICAL, heldBackCritical.aboveWarning(),
                heldBackCritical.aboveCritical(), heldBackCritical.belowWarning(), true, true, nowNanos,
                heldBackCritical.lastCriticalNanos());
        return new Step(warned, Optional.of(AlertKind.WARNING));
    }

    private static Step recover(AlertState state, AlertKind kind) {
        AlertState recovered = withLevel(state, HealthStatus.OK, false, false);
        return new Step(recovered, state.emitted() ? Optional.of(kind) : Optional.empty());
    }

    private static AlertState withLevel(AlertState state, HealthStatus level, boolean emitted, boolean pending) {
        return new AlertState(level, state.aboveWarning(), state.aboveCritical(), state.belowWarning(), emitted, pending,
                state.lastWarningNanos(), state.lastCriticalNanos());
    }

    private static AlertKind kindOf(HealthStatus level) {
        return level == HealthStatus.CRITICAL ? AlertKind.CRITICAL : AlertKind.WARNING;
    }

    private static int increment(int count) {
        return Math.min(count + 1, COUNTER_LIMIT);
    }

    private static long latest(long first, long second) {
        if (first == AlertState.NEVER) {
            return second;
        }
        if (second == AlertState.NEVER) {
            return first;
        }
        return second - first > 0 ? second : first;
    }

    private static int rank(HealthStatus level) {
        return switch (level) {
            case OK, UNAVAILABLE -> 0;
            case WARNING -> 1;
            case CRITICAL -> 2;
        };
    }
}
