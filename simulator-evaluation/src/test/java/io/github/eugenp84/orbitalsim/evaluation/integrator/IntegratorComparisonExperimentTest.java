package io.github.eugenp84.orbitalsim.evaluation.integrator;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the controlled numerical-integrator comparison experiment.
 *
 * @author Eugeniu PLATITA
 */
class IntegratorComparisonExperimentTest
{
    private static final double POSITION_TOLERANCE_METRES = 1.0e-3;

    private final IntegratorComparisonExperiment experiment =
            new IntegratorComparisonExperiment();

    @Test
    void analyticalReferenceStartsAtPositiveXAxis()
    {
        final Vector3D position =
                IntegratorComparisonExperiment.analyticalRelativePosition(0.0);

        assertEquals(
                PhysicalConstants.ASTRONOMICAL_UNIT,
                position.x(),
                POSITION_TOLERANCE_METRES);
        assertEquals(0.0, position.y(), POSITION_TOLERANCE_METRES);
        assertEquals(0.0, position.z(), POSITION_TOLERANCE_METRES);
    }

    @Test
    void baselineRunCommitsExpectedNumberOfSteps()
    {
        final IntegratorComparisonResult result =
                experiment.run(IntegratorMethod.VELOCITY_VERLET);

        assertEquals(
                IntegratorComparisonExperiment.COMPARISON_STEPS,
                result.completedSteps());

        assertEquals(
                IntegratorComparisonExperiment.COMPARISON_STEPS
                        * IntegratorComparisonExperiment.TIME_STEP_SECONDS,
                result.simulationTimeSeconds(),
                0.0);
    }

    @Test
    void baselineRunCountsActualAccelerationEvaluations()
    {
        final IntegratorComparisonResult euler =
                experiment.run(IntegratorMethod.FORWARD_EULER);
        final IntegratorComparisonResult verlet =
                experiment.run(IntegratorMethod.VELOCITY_VERLET);
        final IntegratorComparisonResult rk4 =
                experiment.run(IntegratorMethod.RUNGE_KUTTA_4);

        assertEquals(1_461L, euler.accelerationEvaluations());
        assertEquals(2_922L, verlet.accelerationEvaluations());
        assertEquals(5_844L, rk4.accelerationEvaluations());
    }

    @Test
    void baselineRunProducesFiniteNonNegativeMeasurements()
    {
        for (final IntegratorMethod method : IntegratorMethod.values())
        {
            final IntegratorComparisonResult result = experiment.run(method);

            assertTrue(Double.isFinite(result.maxRelativeEnergyDeviation()));
            assertTrue(result.maxRelativeEnergyDeviation() >= 0.0);

            assertTrue(Double.isFinite(result.maxTrajectoryErrorMetres()));
            assertTrue(result.maxTrajectoryErrorMetres() >= 0.0);
        }
    }

    @Test
    void methodsShowExpectedTrajectoryAccuracyForBaselineExperiment()
    {
        final IntegratorComparisonResult euler =
                experiment.run(IntegratorMethod.FORWARD_EULER);
        final IntegratorComparisonResult verlet =
                experiment.run(IntegratorMethod.VELOCITY_VERLET);
        final IntegratorComparisonResult rk4 =
                experiment.run(IntegratorMethod.RUNGE_KUTTA_4);

        assertTrue(
                verlet.maxTrajectoryErrorMetres() < euler.maxTrajectoryErrorMetres());

        assertTrue(
                rk4.maxTrajectoryErrorMetres() < verlet.maxTrajectoryErrorMetres());
    }


    //---this is temporary and must be deleted later==========================================================
    @Test
    void printBaselineComparisonResults()
    {
        for (final IntegratorMethod method : IntegratorMethod.values())
        {
            final IntegratorComparisonResult result = experiment.run(method);

            System.out.printf(
                    "%-16s steps=%d, time=%.1f s, maxEnergyDeviation=%.15e, "
                            + "maxTrajectoryError=%.9e m, accelerationEvaluations=%d%n",
                    method.displayName(),
                    result.completedSteps(),
                    result.simulationTimeSeconds(),
                    result.maxRelativeEnergyDeviation(),
                    result.maxTrajectoryErrorMetres(),
                    result.accelerationEvaluations());
        }
    }//-----this must be deleted=========================================================


}