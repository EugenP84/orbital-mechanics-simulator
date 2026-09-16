package io.github.eugenp84.orbitalsim.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level3Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Level3Controller}: default attempt, configuration replacement, reset, envelope
 * exposure, and the eccentricity read-through. Six-year physics is covered by Level3ScenarioTest.
 *
 * @author Eugeniu PLATITA
 */
class Level3ControllerTest
{
    private final Level3Controller controller = new Level3Controller(new Level3Scenario());

    @Test
    void aFreshControllerHoldsANotStartedDefaultWithinTheEnvelope()
    {
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertTrue(controller.configuredPerturberMassKilograms() >= controller.minPerturberMassKilograms()
                && controller.configuredPerturberMassKilograms() <= controller.maxPerturberMassKilograms());
        assertTrue(controller.configuredPerturberDistanceMetres() >= controller.minPerturberDistanceMetres()
                && controller.configuredPerturberDistanceMetres() <= controller.maxPerturberDistanceMetres());
    }

    @Test
    void configureAttemptInstallsANewNotStartedAttempt()
    {
        controller.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, controller.state());

        final double mass = controller.maxPerturberMassKilograms();
        final double distance = controller.minPerturberDistanceMetres();
        controller.configureAttempt(mass, distance);

        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertEquals(mass, controller.configuredPerturberMassKilograms(), 0.0);
        assertEquals(distance, controller.configuredPerturberDistanceMetres(), 0.0);
    }

    @Test
    void anOutOfEnvelopeConfigurationThrowsAndLeavesThePreviousAttemptIntact()
    {
        final double massBefore = controller.configuredPerturberMassKilograms();
        final double distanceBefore = controller.configuredPerturberDistanceMetres();
        final SimulationSnapshot snapshotBefore = controller.snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> controller.configureAttempt(
                        0.5 * controller.minPerturberMassKilograms(),
                        controller.minPerturberDistanceMetres()));

        assertEquals(massBefore, controller.configuredPerturberMassKilograms(), 0.0);
        assertEquals(distanceBefore, controller.configuredPerturberDistanceMetres(), 0.0);
        assertEquals(snapshotBefore, controller.snapshot());
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
    }

    @Test
    void maxEccentricityFollowsTheLiveAccumulatorOfTheInstalledAttempt()
    {
        // Strongest envelope config so the perturbation reliably grows e_max; verify the controller's
        // read-through follows the installed attempt's live accumulator, not a constant.
        controller.configureAttempt(
                controller.maxPerturberMassKilograms(), controller.minPerturberDistanceMetres());
        final double initial = controller.maxEccentricity();

        // Pin the presentation rate to 1 simulated day per real second and advance exactly 180
        // simulated days — well within the six-year horizon and enough for e_max to rise.
        controller.setSimulatedSecondsPerRealSecond(86_400.0);
        controller.toggleRun();
        for (int i = 0; i < 180; i++)
        {
            controller.advanceByWallClock(1.0);
        }

        assertTrue(controller.maxEccentricity() > initial);
    }

    @Test
    void maxEccentricityReadsThroughToTheCurrentAttempt()
    {
        // Seeded to the current attempt's initial eccentricity: finite and non-negative before start.
        final double e = controller.maxEccentricity();
        assertTrue(Double.isFinite(e) && e >= 0.0);
    }

    @Test
    void resetRebuildsTheCurrentConfigurationAsNotStarted()
    {
        final double mass = controller.minPerturberMassKilograms();
        final double distance = controller.maxPerturberDistanceMetres();
        controller.configureAttempt(mass, distance);
        controller.toggleRun();

        controller.reset();

        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertEquals(mass, controller.configuredPerturberMassKilograms(), 0.0);
        assertEquals(distance, controller.configuredPerturberDistanceMetres(), 0.0);
    }

    @Test
    void constructionRejectsNullScenario()
    {
        assertThrows(NullPointerException.class, () -> new Level3Controller(null));
    }
}