package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonExperiment;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonResult;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorMethod;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests presentation control of the interactive numerical-integrator comparison.
 *
 * @author Eugeniu PLATITA
 */
class IntegratorComparisonControllerTest
{
    private static final double ONE_STEP_WALL_CLOCK_SECONDS =
            IntegratorComparisonExperiment.TIME_STEP_SECONDS
                    / (14.0 * 86_400.0);

    @Test
    void completedRunIsRetainedForSelectedMethod()
    {
        final IntegratorComparisonController controller = new IntegratorComparisonController();

        controller.setSimulatedSecondsPerRealSecond(IntegratorComparisonExperiment.TIME_STEP_SECONDS);
        controller.toggleRun();

        controller.advanceByWallClock(IntegratorComparisonExperiment.COMPARISON_STEPS);

        assertEquals(
                IntegratorComparisonState.COMPLETED,
                controller.state());

        assertTrue(controller.completedResult(IntegratorMethod.FORWARD_EULER).isPresent());
        final IntegratorComparisonResult retained = controller.completedResult(IntegratorMethod.FORWARD_EULER).orElseThrow();

        assertEquals(IntegratorComparisonExperiment.COMPARISON_STEPS, retained.completedSteps());
    }

    @Test
    void freshControllerHasFreshForwardEulerRun()
    {
        final IntegratorComparisonController controller = new IntegratorComparisonController();

        assertEquals(IntegratorMethod.FORWARD_EULER, controller.selectedMethod());
        assertEquals(
                IntegratorComparisonState.READY,
                controller.state());
        assertEquals(0L, controller.currentResult().completedSteps());
        assertEquals(0.0, controller.snapshot().simulationTimeSeconds(), 0.0);
        assertTrue(controller.completedResults().isEmpty());
        assertTrue(controller.canSelectMethod());
    }

    @Test
    void methodCannotChangeWhileRunIsActive()
    {
        final IntegratorComparisonController controller =
                new IntegratorComparisonController();

        controller.toggleRun();
        assertFalse(controller.canSelectMethod());

        assertThrows(
                IllegalStateException.class,
                () -> controller.selectMethod(
                        IntegratorMethod.VELOCITY_VERLET));
    }

    @Test
    void pausePreventsAdvancementAndResumeAllowsIt()
    {
        final IntegratorComparisonController controller =
                new IntegratorComparisonController();

        controller.toggleRun();
        controller.toggleRun();
        controller.advanceByWallClock(ONE_STEP_WALL_CLOCK_SECONDS);
        assertEquals(0L, controller.currentResult().completedSteps());

        controller.toggleRun();
        controller.advanceByWallClock(ONE_STEP_WALL_CLOCK_SECONDS);

        assertEquals(1L, controller.currentResult().completedSteps());
    }

    @Test
    void presentationRateDoesNotChangeFixedNumericalStep()
    {
        final IntegratorComparisonController controller = new IntegratorComparisonController();

        controller.setSimulatedSecondsPerRealSecond(2.0 * IntegratorComparisonExperiment.TIME_STEP_SECONDS);

        controller.toggleRun();
        controller.advanceByWallClock(1.0);

        assertEquals(2L, controller.currentResult().completedSteps());

        assertEquals(
                2.0 * IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                controller.snapshot().simulationTimeSeconds(),
                0.0);
    }

    @Test
    void resetReplacesProgressButRetainsCompletedResults()
    {
        final IntegratorComparisonController controller = new IntegratorComparisonController();

        controller.setSimulatedSecondsPerRealSecond(
                IntegratorComparisonExperiment.TIME_STEP_SECONDS);
        controller.toggleRun();

        controller.advanceByWallClock(IntegratorComparisonExperiment.COMPARISON_STEPS);

        assertTrue(
                controller.completedResult(
                        IntegratorMethod.FORWARD_EULER).isPresent());

        controller.reset();

        assertEquals(
                IntegratorComparisonState.READY,
                controller.state());
        assertEquals(0L, controller.currentResult().completedSteps());

        assertTrue(
                controller.completedResult(
                        IntegratorMethod.FORWARD_EULER).isPresent());
    }

    @Test
    void selectedMethodCreatesFreshRun()
    {
        final IntegratorComparisonController controller = new IntegratorComparisonController();

        controller.selectMethod(IntegratorMethod.RUNGE_KUTTA_4);

        assertEquals(
                IntegratorMethod.RUNGE_KUTTA_4,
                controller.selectedMethod());
        assertEquals(
                IntegratorComparisonState.READY,
                controller.state());
        assertEquals(0L, controller.currentResult().completedSteps());
        assertEquals(0L, controller.currentResult().accelerationEvaluations());
    }

    @Test
    void simulatedTimeIsAccumulatedUntilWholeStepIsAvailable()
    {
        final IntegratorComparisonController controller = new IntegratorComparisonController();

        controller.setSimulatedSecondsPerRealSecond(
                IntegratorComparisonExperiment.TIME_STEP_SECONDS);

        controller.toggleRun();

        controller.advanceByWallClock(0.25);
        controller.advanceByWallClock(0.25);
        controller.advanceByWallClock(0.25);

        assertEquals(0L, controller.currentResult().completedSteps());

        controller.advanceByWallClock(0.25);

        assertEquals(1L, controller.currentResult().completedSteps());

        assertEquals(
                IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                controller.snapshot().simulationTimeSeconds(),
                0.0);
    }

    @Test
    void toggleRunFollowsComparisonLifecycle()
    {
        final IntegratorComparisonController controller =
                new IntegratorComparisonController();

        controller.toggleRun();
        assertEquals(
                IntegratorComparisonState.RUNNING,
                controller.state());

        controller.toggleRun();
        assertEquals(
                IntegratorComparisonState.PAUSED,
                controller.state());

        controller.toggleRun();
        assertEquals(
                IntegratorComparisonState.RUNNING,
                controller.state());
    }
}