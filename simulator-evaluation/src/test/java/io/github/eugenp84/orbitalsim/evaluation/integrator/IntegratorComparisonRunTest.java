package io.github.eugenp84.orbitalsim.evaluation.integrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests incremental execution of one numerical-integrator comparison run.
 *
 * @author Eugeniu PLATITA
 */
class IntegratorComparisonRunTest
{
    @Test
    void freshRunStartsAtInitialState()
    {
        final IntegratorComparisonRun run =
                new IntegratorComparisonRun(
                        IntegratorMethod.FORWARD_EULER,
                        IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                        2L);

        final IntegratorComparisonResult result = run.result();

        assertEquals(0L, result.completedSteps());
        assertEquals(0.0, result.simulationTimeSeconds(), 0.0);
        assertEquals(0.0, result.maxRelativeEnergyDeviation(), 0.0);
        assertEquals(0.0, result.maxTrajectoryErrorMetres(), 0.0);
        assertEquals(0L, result.accelerationEvaluations());
        assertFalse(run.isComplete());
    }

    @Test
    void stepAdvancesExactlyOneCommittedStep()
    {
        final IntegratorComparisonRun run =
                new IntegratorComparisonRun(
                        IntegratorMethod.FORWARD_EULER,
                        IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                        2L);

        run.step();

        final IntegratorComparisonResult result = run.result();

        assertEquals(1L, result.completedSteps());
        assertEquals(
                IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                result.simulationTimeSeconds(),
                0.0);
        assertEquals(1L, result.accelerationEvaluations());
        assertFalse(run.isComplete());
    }

    @Test
    void runCompletesAtConfiguredStepCount()
    {
        final IntegratorComparisonRun run =
                new IntegratorComparisonRun(
                        IntegratorMethod.VELOCITY_VERLET,
                        IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                        2L);

        run.step();
        run.step();

        assertTrue(run.isComplete());
        assertEquals(2L, run.result().completedSteps());
        assertEquals(4L, run.result().accelerationEvaluations());
    }

    @Test
    void stepAfterCompletionIsRejected()
    {
        final IntegratorComparisonRun run =
                new IntegratorComparisonRun(
                        IntegratorMethod.RUNGE_KUTTA_4,
                        IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                        1L);

        run.step();

        assertThrows(IllegalStateException.class, run::step);
    }
}