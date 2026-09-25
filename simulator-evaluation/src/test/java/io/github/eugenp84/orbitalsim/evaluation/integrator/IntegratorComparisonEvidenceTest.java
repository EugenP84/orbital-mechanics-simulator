package io.github.eugenp84.orbitalsim.evaluation.integrator;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Produces the formal timestep-refinement evidence for the numerical-integrator comparison.
 *
 * <p>Every method is evaluated over the same 365-day simulated duration.
 * The selected timesteps divide that duration exactly, so no fractional final integration step or duration adjustment is required.</p>
 *
 * @author Eugeniu PLATITA
 */
final class IntegratorComparisonEvidenceTest
{
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double EXPERIMENT_DAYS = 365.0;
    private static final double EXPERIMENT_DURATION_SECONDS = EXPERIMENT_DAYS * SECONDS_PER_DAY;
    private static final double[] TIME_STEPS_SECONDS = {86_400.0, 43_200.0, 21_600.0, 10_800.0, 3_600.0};

    @Test
    void reportsTimestepRefinementMatrix()
    {
        final IntegratorComparisonExperiment experiment = new IntegratorComparisonExperiment();
        System.out.println("integrator,timestep_seconds,completed_steps," + "simulation_time_seconds,max_relative_energy_deviation," + "max_trajectory_error_metres,acceleration_evaluations");

        for (final IntegratorMethod method : IntegratorMethod.values())
        {
            for (final double timeStepSeconds : TIME_STEPS_SECONDS)
            {
                final long steps = Math.round(EXPERIMENT_DURATION_SECONDS / timeStepSeconds);
                final IntegratorComparisonResult result = experiment.run(method, timeStepSeconds, steps);
                assertEquals(steps, result.completedSteps());
                assertEquals(EXPERIMENT_DURATION_SECONDS, result.simulationTimeSeconds(),0.0);
                assertTrue(Double.isFinite(result.maxRelativeEnergyDeviation()));
                assertTrue(Double.isFinite(result.maxTrajectoryErrorMetres()));
                print(result, timeStepSeconds);
            }// end for loop
        }
    }

    private static void print(final IntegratorComparisonResult result, final double timeStepSeconds) {
        System.out.printf(
                Locale.ROOT,
                "%s,%.1f,%d,%.1f,%.15e,%.15e,%d%n",
                result.method().displayName(),
                timeStepSeconds,
                result.completedSteps(),
                result.simulationTimeSeconds(),
                result.maxRelativeEnergyDeviation(),
                result.maxTrajectoryErrorMetres(),
                result.accelerationEvaluations());
    }
}