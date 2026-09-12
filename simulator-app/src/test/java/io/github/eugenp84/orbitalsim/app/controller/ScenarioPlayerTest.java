package io.github.eugenp84.orbitalsim.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.learning.scenario.Level0Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioSession;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ScenarioPlayer}, the level-agnostic lifecycle/presentation core. Driven with real
 * Level 0 and Level 1 sessions to demonstrate the player carries no level-specific knowledge.
 *
 * @author Eugeniu PLATITA
 */
class ScenarioPlayerTest
{
    private static final Level0Scenario L0_SCENARIO = new Level0Scenario();
    private static final Level1Scenario L1_SCENARIO = new Level1Scenario();

    private static ScenarioSession freshSession()
    {
        return L0_SCENARIO.createAttempt(
                PhysicalConstants.EARTH_MASS,
                PhysicalConstants.EARTH_RADIUS,
                PhysicalConstants.ASTRONOMICAL_UNIT,
                25_000.0).session();
    }

    private static ScenarioSession freshL1Session()
    {
        return L1_SCENARIO.createAttempt(L1_SCENARIO.circularSpeedMetresPerSecond()).session();
    }

    @Test
    void aFreshPlayerIsNotStartedAndToggleable()
    {
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        assertEquals(ScenarioLifecycleState.NOT_STARTED, player.state());
        assertTrue(player.canToggleRun());
        assertTrue(player.elapsedSimulatedSeconds().isEmpty());
    }

    @Test
    void toggleRunStartsANotStartedSession()
    {
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        player.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, player.state());
    }

    @Test
    void toggleRunCyclesStartPauseResume()
    {
        // Pause/resume are pure lifecycle transitions, tested on an L1 session that does not complete
        // at its first observation, so the running state persists across the cycle.
        final ScenarioPlayer player = new ScenarioPlayer(freshL1Session());
        player.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, player.state());
        player.toggleRun();
        assertEquals(ScenarioLifecycleState.PAUSED, player.state());
        player.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, player.state());
    }

    @Test
    void advanceByWallClockReachesTheSessionWhileRunning()
    {
        final ScenarioSession session = freshSession();
        final ScenarioPlayer player = new ScenarioPlayer(session);
        player.toggleRun();

        // One observation interval of simulated time = interval / presentation-rate of wall-clock.
        // Level 0 completes at its first observation, so one interval is enough to reach COMPLETED.
        final double wallClockForOneObservation =
                session.observationIntervalSeconds() / player.simulatedSecondsPerRealSecond();
        player.advanceByWallClock(wallClockForOneObservation);

        assertEquals(ScenarioLifecycleState.COMPLETED, player.state());
    }

    @Test
    void aCompletedSessionCannotBeToggled()
    {
        final ScenarioSession session = freshSession();
        final ScenarioPlayer player = new ScenarioPlayer(session);
        player.toggleRun();
        player.advanceByWallClock(
                session.observationIntervalSeconds() / player.simulatedSecondsPerRealSecond());

        assertEquals(ScenarioLifecycleState.COMPLETED, player.state());
        assertFalse(player.canToggleRun());

        // toggleRun on a terminal session is a safe no-op: it does not revive it.
        player.toggleRun();
        assertEquals(ScenarioLifecycleState.COMPLETED, player.state());
    }

    @Test
    void advanceByWallClockRejectsBadInputInAnyState()
    {
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        // NOT_STARTED: the argument is validated regardless of lifecycle state.
        assertThrows(IllegalArgumentException.class, () -> player.advanceByWallClock(-1.0));
        assertThrows(IllegalArgumentException.class, () -> player.advanceByWallClock(Double.NaN));
    }

    @Test
    void advanceRejectsAProductThatOverflows()
    {
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        player.toggleRun();
        player.setSimulatedSecondsPerRealSecond(Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> player.advanceByWallClock(Double.MAX_VALUE));
    }

    @Test
    void setSpeedRejectsNonPositiveOrNonFinite()
    {
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        assertThrows(IllegalArgumentException.class,
                () -> player.setSimulatedSecondsPerRealSecond(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> player.setSimulatedSecondsPerRealSecond(Double.NaN));
    }

    @Test
    void installReplacesTheSessionAndRetainsPresentationSpeed()
    {
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        player.toggleRun();
        player.setSimulatedSecondsPerRealSecond(123_456.0);

        player.install(freshSession());

        // New session: back to not-started. Presentation speed is a view setting and survives.
        assertEquals(ScenarioLifecycleState.NOT_STARTED, player.state());
        assertEquals(123_456.0, player.simulatedSecondsPerRealSecond(), 1e-9);
    }

    @Test
    void constructionAndInstallRejectNull()
    {
        assertThrows(NullPointerException.class, () -> new ScenarioPlayer(null));
        final ScenarioPlayer player = new ScenarioPlayer(freshSession());
        assertThrows(NullPointerException.class, () -> player.install(null));
    }
}