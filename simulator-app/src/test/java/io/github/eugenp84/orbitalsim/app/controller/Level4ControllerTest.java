package io.github.eugenp84.orbitalsim.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.eugenp84.orbitalsim.app.composition.Level4AttemptFactory;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import org.junit.jupiter.api.Test;

/**
 * Tests L4 controller responsibilities without repeating the two-day physics experiment.
 *
 * @author Eugeniu PLATITA
 */
class Level4ControllerTest
{
    private static final double PHYSICS_STEP_SECONDS = 112.5;

    private final Level4Controller controller =
            new Level4Controller(new Level4AttemptFactory());

    /** Verifies that the canonical controller starts with a renderable, seeded attempt. */
    @Test
    void aFreshControllerHoldsTheCanonicalNotStartedAttempt()
    {
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertEquals(new Level4Scenario().initialEpochJdTdb(), controller.initialJdTdb(), 0.0);
        assertEquals(3, controller.snapshot().bodies().size());
        assertTrue(controller.currentDistanceMetres() > 0.0);
        assertEquals(controller.currentDistanceMetres(), controller.minimumDistanceMetres(), 0.0);
        assertEquals(0.0, controller.minimumDistanceElapsedSeconds(), 0.0);
        assertTrue(controller.relativeSpeedAtMinimumMetresPerSecond() > 0.0);
    }

    /** Verifies that lifecycle advancement reaches the Level 4 session through ScenarioPlayer. */
    @Test
    void advancementUpdatesElapsedTimeThroughTheSharedPlayer()
    {
        controller.setSimulatedSecondsPerRealSecond(PHYSICS_STEP_SECONDS);
        controller.toggleRun();
        controller.advanceByWallClock(1.0);

        assertEquals(ScenarioLifecycleState.RUNNING, controller.state());
        assertEquals(
                PHYSICS_STEP_SECONDS,
                controller.elapsedSimulatedSeconds().orElseThrow(),
                0.0);
    }

    /** Verifies that reset replaces the attempt and restores its seeded encounter measurements. */
    @Test
    void resetInstallsAFreshCanonicalAttempt()
    {
        final double initialDistance = controller.currentDistanceMetres();
        final double initialRelativeSpeed = controller.relativeSpeedAtMinimumMetresPerSecond();
        controller.setSimulatedSecondsPerRealSecond(PHYSICS_STEP_SECONDS);
        controller.toggleRun();
        controller.advanceByWallClock(1.0);
        controller.reset();
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertTrue(controller.elapsedSimulatedSeconds().isEmpty());
        assertEquals(initialDistance, controller.currentDistanceMetres(), 0.0);
        assertEquals(initialDistance, controller.minimumDistanceMetres(), 0.0);
        assertEquals(0.0, controller.minimumDistanceElapsedSeconds(), 0.0);
        assertEquals(initialRelativeSpeed, controller.relativeSpeedAtMinimumMetresPerSecond(),0.0);
        assertEquals(PHYSICS_STEP_SECONDS, controller.simulatedSecondsPerRealSecond(), 0.0);
    }
}
