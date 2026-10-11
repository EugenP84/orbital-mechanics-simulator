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
 * Cross-method regression checks for the three integration strategies.
 * <p>
 * These tests assert relative empirical behaviour across integrators for one controlled scenario, rather than the correctness of any single implementation.
 * They are deliberately kept out of the per-integrator suites:
 * a cross-method assertion placed inside, {@code RungeKutta4IntegratorTest} would allow a change to a different integrator to fail RK4's suite while RK4 itself remained correct.
 *
 * @author Eugeniu PLATITA
 */
class IntegratorComparisonTest {

    private static final double RADIUS = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double CIRCULAR_SPEED = Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT * PhysicalConstants.SOLAR_MASS / RADIUS);
    private static final double PERIOD = 2.0 * Math.PI * RADIUS / CIRCULAR_SPEED;
    private static final int STEPS = 400;

    private static final AccelerationModel CENTRAL_FIELD = new NBodyAccelerationCalculator(
            new GravitationalBodyConfig[]{
                    GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                    GravitationalBodyConfig.nonContributing(1.0e3)});

    private static StateVector[] initialStates() {
        return new StateVector[]{
                new StateVector(Vector3D.ZERO, Vector3D.ZERO),
                new StateVector(new Vector3D(RADIUS, 0, 0),
                        new Vector3D(0, CIRCULAR_SPEED, 0))};
    }

    private static double closureError(Integrator method) {
        StateVector[] states = initialStates();

        double dt = PERIOD / STEPS;
        for (int i = 0; i < STEPS; i++) {
            states = method.step(states, dt, CENTRAL_FIELD);
        }

        return states[1].position().distanceTo(new Vector3D(RADIUS, 0, 0)) / RADIUS;
    }

    @Test
    void methodsShowExpectedAccuracyOrderingOverOneOrbit() {
        double eulerError = closureError(new EulerIntegrator());
        double verletError = closureError(new VelocityVerletIntegrator());
        double rk4Error = closureError(new RungeKutta4Integrator());

        assertTrue(rk4Error < verletError,"RK4 error " + rk4Error + " should be below Verlet's " + verletError);
        assertTrue(verletError < eulerError,"Verlet error " + verletError + " should be below Euler's " + eulerError);
    }

    @Test
    void eulerAndVerletDifferByTheExpectedSecondOrderTerm() {
        // The single-step position difference between the two methods is analytically known rather than arbitrary:
        //
        //   Euler:  r + v·Δt
        //   Verlet: r + (v + ½·a·Δt)·Δt  =  r + v·Δt + ½·a·Δt²
        //
        // so they differ by exactly ½·a·Δt². Asserting that quantity checks the relationship between the methods rather than merely that both produce a similar answer.

        StateVector[] input = initialStates();
        double dt = PERIOD / 100_000;

        Vector3D euler = new EulerIntegrator().step(input, dt, CENTRAL_FIELD)[1].position();
        Vector3D verlet = new VelocityVerletIntegrator().step(input, dt, CENTRAL_FIELD)[1].position();

        double centralAcceleration = PhysicalConstants.GRAVITATIONAL_CONSTANT * PhysicalConstants.SOLAR_MASS / (RADIUS * RADIUS);
        double expectedDifference = 0.5 * centralAcceleration * dt * dt;

        assertEquals(expectedDifference, euler.distanceTo(verlet),expectedDifference * 1e-6);
    }

    @Test
    void verletAndRungeKutta4AgreeFarMoreCloselyThanEuler() {
        // Both second- and fourth-order methods include the ½·a·Δt² term, so over one step they agree to a much higher order than either agrees with Forward Euler.
        StateVector[] input = initialStates();
        double dt = PERIOD / 100_000;

        Vector3D euler = new EulerIntegrator().step(input, dt, CENTRAL_FIELD)[1].position();
        Vector3D verlet = new VelocityVerletIntegrator().step(input, dt, CENTRAL_FIELD)[1].position();
        Vector3D rk4 = new RungeKutta4Integrator().step(input, dt, CENTRAL_FIELD)[1].position();

        double verletToRk4 = verlet.distanceTo(rk4);
        double eulerToVerlet = euler.distanceTo(verlet);

        assertTrue(verletToRk4 < eulerToVerlet / 1_000.0,
                "Verlet and RK4 should agree far more closely than either does with Euler: "
                        + verletToRk4 + " vs " + eulerToVerlet);
    }
}