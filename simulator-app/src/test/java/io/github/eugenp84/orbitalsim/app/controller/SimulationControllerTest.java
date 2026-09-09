package io.github.eugenp84.orbitalsim.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SimulationController}.
 * <p>
 * The controller carries no JavaFX dependency, so these run without a JavaFX toolkit.
 * The transition, reset, guarding and speed-validation tests do not depend on the Level 1 physics thresholds.
 * The single termination test uses the real default Level 1 composition — circular initial condition,
 * the scenario's completion horizon and the controller's default presentation speed — and asserts only that some terminal state is reached;
 * It does not duplicate the completion policy's detailed criteria.
 *
 * @author Eugeniu PLATITA
 */
class SimulationControllerTest
{
    private static final double SECONDS_PER_DAY = 86_400.0;

    // Enough real seconds, at the controller's default presentation speed, for the default circular Level 1 attempt to reach a terminal state within one call.
    // This depends on the current default composition, not on the exact thresholds.
    private static final double AMPLE_WALL_CLOCK_SECONDS = 100.0;

    @Test
    void aFreshControllerIsNotStartedAndToggleable()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertTrue(controller.canToggleRun());
    }

    @Test
    void toggleRunCyclesStartPauseResume()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, controller.state());
        controller.toggleRun();
        assertEquals(ScenarioLifecycleState.PAUSED, controller.state());
        controller.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, controller.state());
    }

    @Test
    void resetReturnsToNotStartedFromRunning() {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        controller.reset();
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertTrue(controller.canToggleRun());
    }

    @Test
    void advancingDoesNothingWhileNotStarted()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        final double before = controller.snapshot().simulationTimeSeconds();
        controller.advanceByWallClock(1.0);
        assertEquals(before, controller.snapshot().simulationTimeSeconds());
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
    }

    @Test
    void advancingProgressesSimulationWhileRunning()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        final double before = controller.snapshot().simulationTimeSeconds();
        controller.advanceByWallClock(0.1);
        assertTrue(controller.snapshot().simulationTimeSeconds() > before);
    }

    @Test
    void aTerminatedDefaultAttemptCannotBeToggledButCanBeReset()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        controller.advanceByWallClock(AMPLE_WALL_CLOCK_SECONDS);

        final ScenarioLifecycleState terminal = controller.state();
        assertTrue(terminal == ScenarioLifecycleState.COMPLETED
                || terminal == ScenarioLifecycleState.FAILED);
        assertFalse(controller.canToggleRun());

        controller.reset();
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertTrue(controller.canToggleRun());
    }

    @Test
    void advanceRejectsNegativeWallClockEvenWhenNotStarted()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        // NOT_STARTED: proves the lifecycle guard does not mask invalid input.
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertThrows(IllegalArgumentException.class, () -> controller.advanceByWallClock(-1.0));
    }

    @Test
    void advanceRejectsNonFiniteWallClockEvenWhenNotStarted()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        assertThrows(IllegalArgumentException.class,
                () -> controller.advanceByWallClock(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> controller.advanceByWallClock(Double.POSITIVE_INFINITY));
    }

    @Test
    void advanceRejectsFiniteInputsWhoseProductOverflows()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        controller.setSimulatedSecondsPerRealSecond(Double.MAX_VALUE);
        // Both operands are finite; their product is not. The controller must reject
        // it before a non-finite duration crosses into the learning layer.
        assertThrows(IllegalArgumentException.class,
                () -> controller.advanceByWallClock(Double.MAX_VALUE));
    }

    @Test
    void speedMustBePositiveAndFinite() {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        assertThrows(IllegalArgumentException.class,
                () -> controller.setSimulatedSecondsPerRealSecond(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> controller.setSimulatedSecondsPerRealSecond(-1.0));
        assertThrows(IllegalArgumentException.class,
                () -> controller.setSimulatedSecondsPerRealSecond(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> controller.setSimulatedSecondsPerRealSecond(Double.POSITIVE_INFINITY));

        controller.setSimulatedSecondsPerRealSecond(SECONDS_PER_DAY);
        assertEquals(SECONDS_PER_DAY, controller.simulatedSecondsPerRealSecond());
    }

    @Test
    void noTerminationReasonBeforeTerminating()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        assertTrue(controller.terminationReason().isEmpty());
        controller.toggleRun();
        assertTrue(controller.terminationReason().isEmpty());
    }

    @Test
    void aTerminatedAttemptReportsAReason()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        controller.advanceByWallClock(AMPLE_WALL_CLOCK_SECONDS);
        assertTrue(controller.terminationReason().isPresent());
    }

    @Test
    void elapsedIsEmptyBeforeStartAndPresentWhileRunning()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        assertTrue(controller.elapsedSimulatedSeconds().isEmpty());
        controller.toggleRun();
        controller.advanceByWallClock(0.1);
        assertTrue(controller.elapsedSimulatedSeconds().isPresent());
    }

    @Test
    void elapsedIsEmptyBeforeStartAndDefinedFromTheMomentTheAttemptStarts()
    {
        final SimulationController controller =  new SimulationController(new Level1Scenario());
        assertTrue(controller.elapsedSimulatedSeconds().isEmpty());
        controller.toggleRun();
        assertTrue(controller.elapsedSimulatedSeconds().isPresent());
        assertEquals(0.0, controller.elapsedSimulatedSeconds().getAsDouble());
        controller.advanceByWallClock(0.1);
        assertTrue(controller.elapsedSimulatedSeconds().getAsDouble() > 0.0);
    }

    @Test
    void configureAttemptAtCircularSpeedCreatesANotStartedAttempt() {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        final AttemptCreationResult result =
                controller.configureAttempt(controller.circularSpeedMetresPerSecond());
        assertEquals(AttemptCreationResult.CREATED, result);
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
    }

    @Test
    void configureAttemptRejectsNonPositiveOrNonFiniteSpeed()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        assertThrows(IllegalArgumentException.class, () -> controller.configureAttempt(0.0));
        assertThrows(IllegalArgumentException.class, () -> controller.configureAttempt(-1.0));
        assertThrows(IllegalArgumentException.class, () -> controller.configureAttempt(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> controller.configureAttempt(Double.POSITIVE_INFINITY));
    }

    @Test
    void anUnassessableSpeedIsRefusedAndLeavesTheRunningAttemptIntact()
    {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        controller.advanceByWallClock(0.1);

        final double elapsedBefore = controller.elapsedSimulatedSeconds().getAsDouble();
        final double unassessableSpeed = 0.1 * controller.circularSpeedMetresPerSecond();

        final AttemptCreationResult result = controller.configureAttempt(unassessableSpeed);

        assertEquals(AttemptCreationResult.NOT_ASSESSABLE, result);
        // The previous, running attempt survived: same state and same progress.
        assertEquals(ScenarioLifecycleState.RUNNING, controller.state());
        assertEquals(elapsedBefore, controller.elapsedSimulatedSeconds().getAsDouble());
    }

    @Test
    void anAssessableNonCircularSpeedReplacesTheAttemptWithAFreshOne() {
        final SimulationController controller = new SimulationController(new Level1Scenario());
        controller.toggleRun();
        controller.advanceByWallClock(0.1);

        final double assessableSpeed = 1.2 * controller.circularSpeedMetresPerSecond();
        final AttemptCreationResult result = controller.configureAttempt(assessableSpeed);

        assertEquals(AttemptCreationResult.CREATED, result);
        // A fresh attempt: back to not-started, so elapsed is undefined again.
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertTrue(controller.elapsedSimulatedSeconds().isEmpty());
    }
}