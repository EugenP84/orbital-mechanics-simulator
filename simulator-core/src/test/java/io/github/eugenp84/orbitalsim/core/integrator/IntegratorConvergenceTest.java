package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Convergence regression checks for the integration methods.
 * <p>
 * Each measures how a method's global error changes as the timestep is refined, checking that the observed
 * behaviour is broadly consistent with the convergence order claimed in that method's documentation.
 * They are kept separate from the per-integrator contract suites because they assert numerical behaviour rather than implementation correctness and run over many steps.
 * <p>
 * <strong>Scope and limitations.</strong> Orbital closure error aggregates radial and phase error, and cancellation between them can distort a ratio
 * computed from only two timesteps.
 * The acceptance bands below are therefore deliberately wide, and are weaker at low orders:
 * for first order the band admits any ratio above 1, which the preceding "error decreased" assertion already guarantees.
 *
 * @author Eugeniu PLATITA
 */
class IntegratorConvergenceTest {

    private static final double RADIUS = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double CIRCULAR_SPEED = Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT * PhysicalConstants.SOLAR_MASS / RADIUS);
    private static final double PERIOD = 2.0 * Math.PI * RADIUS / CIRCULAR_SPEED;

    /**
     * The orbiting body is non-contributing, so the central body remains exactly stationary and the orbit is exactly circular. The orbiting
     * body's own mass does not affect its trajectory.
     */
    private static final AccelerationModel CENTRAL_FIELD = new NBodyAccelerationCalculator(
            new GravitationalBodyConfig[]{
                    GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                    GravitationalBodyConfig.nonContributing(1.0e3)});

    /** Relative position error after integrating one full circular orbit. */
    private static double closureError(Integrator method, int steps) {
        StateVector[] states = {
                new StateVector(Vector3D.ZERO, Vector3D.ZERO),
                new StateVector(new Vector3D(RADIUS, 0, 0),
                        new Vector3D(0, CIRCULAR_SPEED, 0))};

        double dt = PERIOD / steps;
        for (int i = 0; i < steps; i++) {
            states = method.step(states, dt, CENTRAL_FIELD);
        }

        return states[1].position().distanceTo(new Vector3D(RADIUS, 0, 0)) / RADIUS;
    }

    /**
     * Asserts that halving the timestep reduces the error, and that the reduction is within a factor of two of 2^order. The band is wide by
     * design: the objective is to detect gross departure from the expected order, not to determine the order precisely.
     */
    private static void assertConvergenceOrder(
            Integrator method, int coarseSteps, int expectedOrder) {

        double coarseError = closureError(method, coarseSteps);
        double fineError = closureError(method, coarseSteps * 2);

        assertTrue(fineError < coarseError,
                "halving the timestep must reduce the error, was "
                        + coarseError + " then " + fineError);

        double ratio = coarseError / fineError;
        double expected = Math.pow(2.0, expectedOrder);

        assertTrue(ratio > expected / 2.0 && ratio < expected * 2.0,
                "expected order " + expectedOrder + " (ratio near " + expected
                        + "), measured ratio was " + ratio);
    }

    @Test
    void forwardEulerConvergesAtFirstOrder() {
        // Forward Euler requires a finer timestep here than the higher-order
        // methods before the measured closure error enters a sufficiently clear asymptotic regime for an order estimate.
        assertConvergenceOrder(new EulerIntegrator(), 8_000, 1);
    }

    @Test
    void velocityVerletConvergesAtSecondOrder() {
        assertConvergenceOrder(new VelocityVerletIntegrator(), 200, 2);
    }

    @Test
    void rungeKutta4ConvergesAtFourthOrder() {
        assertConvergenceOrder(new RungeKutta4Integrator(), 200, 4);
    }
}