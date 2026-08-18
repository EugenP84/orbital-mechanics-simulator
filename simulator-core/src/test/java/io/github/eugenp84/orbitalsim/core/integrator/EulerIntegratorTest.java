package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.GravityCalculator;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EulerIntegratorTest {

    private static final double RELATIVE_TOLERANCE = 1e-9;

    private final EulerIntegrator integrator = new EulerIntegrator();

    /** A model applying the same fixed acceleration to every body. */
    private static AccelerationModel constant(Vector3D acceleration) {
        return states -> {
            Vector3D[] accelerations = new Vector3D[states.length];
            Arrays.fill(accelerations, acceleration);
            return accelerations;
        };
    }

    private static AccelerationModel zero() {
        return constant(Vector3D.ZERO);
    }

    private static void assertVectorEquals(Vector3D expected, Vector3D actual, double scale) {
        double tolerance = scale * RELATIVE_TOLERANCE;
        assertEquals(expected.x(), actual.x(), tolerance, "x component");
        assertEquals(expected.y(), actual.y(), tolerance, "y component");
        assertEquals(expected.z(), actual.z(), tolerance, "z component");
    }

    // --- Algorithm correctness ---

    @Test
    void advancesStateAccordingToForwardEuler() {
        // r = (0, 100, 0), v = (10, 0, 0), a = (0, -10, 0), dt = 2
        //
        //   r_next = r + v·dt = (0,100,0) + (20,0,0) = (20, 100, 0)
        //   v_next = v + a·dt = (10,0,0) + (0,-20,0) = (10, -20, 0)
        //
        // The y-position remains exactly 100 despite a downward acceleration.
        // This is intentional and is the defining feature of Forward Euler:
        // position is advanced using the CURRENT velocity, so acceleration does
        // not affect position until the next step. Semi-implicit (symplectic)
        // Euler would instead give y = 100 + (-20)(2) = 60, so this assertion
        // also guards against silently implementing the wrong Euler variant.
        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};

        StateVector[] result = integrator.step(
                input, 2.0, constant(new Vector3D(0, -10, 0)));

        assertEquals(new Vector3D(20, 100, 0), result[0].position());
        assertEquals(new Vector3D(10, -20, 0), result[0].velocity());
    }

    @Test
    void zeroAccelerationProducesStraightLineMotion() {
        Vector3D position = new Vector3D(1, 2, 3);
        Vector3D velocity = new Vector3D(10, -20, 30);
        double dt = 5.0;

        StateVector[] result = integrator.step(
                new StateVector[]{new StateVector(position, velocity)}, dt, zero());

        assertEquals(position.add(velocity.multiply(dt)), result[0].position());
        assertEquals(velocity, result[0].velocity());
    }

    @Test
    void eachBodyIsAdvancedIndependently() {
        StateVector[] input = {
                new StateVector(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0)),
                new StateVector(new Vector3D(100, 0, 0), new Vector3D(0, 2, 0)),
                new StateVector(new Vector3D(0, 0, 50), new Vector3D(0, 0, -3))};

        StateVector[] result = integrator.step(input, 10.0, zero());

        assertEquals(new Vector3D(10, 0, 0), result[0].position());
        assertEquals(new Vector3D(100, 20, 0), result[1].position());
        assertEquals(new Vector3D(0, 0, 20), result[2].position());
    }

    @Test
    void resultLengthAndOrderMatchInput() {
        StateVector[] input = {
                new StateVector(new Vector3D(1, 0, 0), Vector3D.ZERO),
                new StateVector(new Vector3D(2, 0, 0), Vector3D.ZERO),
                new StateVector(new Vector3D(3, 0, 0), Vector3D.ZERO)};

        StateVector[] result = integrator.step(input, 1.0, zero());

        assertEquals(3, result.length);
        // Stationary bodies under zero acceleration: positions are preserved,
        // confirming order is not permuted
        assertEquals(1.0, result[0].position().x());
        assertEquals(2.0, result[1].position().x());
        assertEquals(3.0, result[2].position().x());
    }

    // --- Purity and snapshot behaviour ---

    @Test
    void inputArrayAndElementsAreUnchanged() {
        StateVector original = new StateVector(
                new Vector3D(0, 100, 0), new Vector3D(10, 0, 0));
        StateVector[] input = {original};

        integrator.step(input, 2.0, constant(new Vector3D(0, -10, 0)));

        // The integrator must not replace the caller's array element.
        // StateVector is immutable, so no deep-copy assertion is needed.
        assertSame(original, input[0]);
        assertEquals(new Vector3D(0, 100, 0), input[0].position());
        assertEquals(new Vector3D(10, 0, 0), input[0].velocity());
    }

    @Test
    void resultArrayIsDistinctFromInput() {
        StateVector[] input = {
                new StateVector(new Vector3D(1, 2, 3), new Vector3D(4, 5, 6))};

        StateVector[] result = integrator.step(input, 1.0, zero());

        assertNotSame(input, result);
    }

    @Test
    void repeatedStepsFromTheSameInputProduceEqualResults() {
        // The integrator holds no state between calls (ADR-005 §1)
        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};
        AccelerationModel model = constant(new Vector3D(0, -10, 0));

        StateVector[] first = integrator.step(input, 2.0, model);
        StateVector[] second = integrator.step(input, 2.0, model);

        assertEquals(first[0], second[0]);
    }

    // --- Computational characteristic ---

    @Test
    void evaluatesTheAccelerationModelExactlyOncePerStep() {
        // Forward Euler requires a single derivative evaluation per step.
        // This is an implementation characteristic rather than part of the
        // Integrator contract, but it is the baseline against which Velocity
        // Verlet and RK4 are compared for computational cost (Objective 6).
        AtomicInteger evaluations = new AtomicInteger();

        AccelerationModel counting = states -> {
            evaluations.incrementAndGet();
            Vector3D[] accelerations = new Vector3D[states.length];
            Arrays.fill(accelerations, Vector3D.ZERO);
            return accelerations;
        };

        integrator.step(
                new StateVector[]{new StateVector(Vector3D.ZERO, Vector3D.ZERO)},
                1.0, counting);

        assertEquals(1, evaluations.get());
    }

    // --- Boundary behaviour ---

    @Test
    void emptySystemProducesEmptyResult() {
        StateVector[] result = integrator.step(new StateVector[0], 1.0, zero());

        assertEquals(0, result.length);
    }

    @Test
    void emptySystemStillValidatesTheTimestep() {
        // Argument validation precedes the collaborator call, so an invalid
        // timestep is rejected even when there is nothing to integrate
        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(new StateVector[0], -1.0, zero()));
    }

    // --- Argument contract ---

    @Test
    void nullArgumentsAreRejected() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(NullPointerException.class,
                () -> integrator.step(null, 1.0, zero()));
        assertThrows(NullPointerException.class,
                () -> integrator.step(states, 1.0, null));
        assertThrows(NullPointerException.class,
                () -> integrator.step(new StateVector[]{null}, 1.0, zero()));
    }

    @Test
    void invalidTimestepIsRejected() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(states, 0.0, zero()));
        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(states, -1.0, zero()));
        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(states, Double.NaN, zero()));
        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(states, Double.POSITIVE_INFINITY, zero()));
    }

    // --- AccelerationModel contract enforcement ---

    @Test
    void nullAccelerationArrayIsRejected() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> null));
    }

    @Test
    void mismatchedAccelerationCountIsRejected() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> new Vector3D[s.length + 1]));
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> new Vector3D[0]));
    }

    @Test
    void nullAccelerationElementIsRejected() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> new Vector3D[]{null}));
    }

    @Test
    void nonFiniteAccelerationIsRejected() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0,
                        s -> new Vector3D[]{new Vector3D(Double.NaN, 0, 0)}));
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0,
                        s -> new Vector3D[]{new Vector3D(0, Double.POSITIVE_INFINITY, 0)}));
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0,
                        s -> new Vector3D[]{new Vector3D(0, 0, Double.NEGATIVE_INFINITY)}));
    }

    @Test
    void modelViolationIsDistinguishedFromCallerError() {
        // ADR-005 §6: three failure sources, three exception types
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(NullPointerException.class,
                () -> integrator.step(null, 1.0, zero()));           // absent argument
        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(states, -1.0, zero()));        // invalid value
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> null));      // broken collaborator
    }

    // --- Composition with the production acceleration model ---

    @Test
    void composesWithNBodyAccelerationCalculator() {
        // Verifies that Euler consumes the production AccelerationModel
        // correctly for a single step. Orbital accuracy over many steps is not
        // in scope here; that belongs to the evaluation suite.
        double sunMass = PhysicalConstants.SOLAR_MASS;
        double earthMass = PhysicalConstants.EARTH_MASS;
        double au = PhysicalConstants.ASTRONOMICAL_UNIT;
        double orbitalSpeed = 2.978e4;
        double dt = 3600.0;

        StateVector sun = new StateVector(Vector3D.ZERO, Vector3D.ZERO);
        StateVector earth = new StateVector(
                new Vector3D(au, 0, 0), new Vector3D(0, orbitalSpeed, 0));
        StateVector[] input = {sun, earth};

        AccelerationModel model = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(sunMass),
                        GravitationalBodyConfig.contributing(earthMass)});

        StateVector[] result = integrator.step(input, dt, model);

        // Expected accelerations come from GravityCalculator, which is
        // independently validated against known physical values
        Vector3D sunAcceleration = GravityCalculator.accelerationBetween(
                sun.position(), earth.position(), earthMass);
        Vector3D earthAcceleration = GravityCalculator.accelerationBetween(
                earth.position(), sun.position(), sunMass);

        assertVectorEquals(
                sun.position().add(sun.velocity().multiply(dt)),
                result[0].position(), au);
        assertVectorEquals(
                sun.velocity().add(sunAcceleration.multiply(dt)),
                result[0].velocity(), orbitalSpeed);
        assertVectorEquals(
                earth.position().add(earth.velocity().multiply(dt)),
                result[1].position(), au);
        assertVectorEquals(
                earth.velocity().add(earthAcceleration.multiply(dt)),
                result[1].velocity(), orbitalSpeed);
    }

    @Test
    void earthAcceleratesTowardsTheSunInOneStep() {
        // Physical sanity check on composition: Earth's velocity acquires a
        // component directed back towards the Sun
        double au = PhysicalConstants.ASTRONOMICAL_UNIT;

        StateVector[] input = {
                new StateVector(Vector3D.ZERO, Vector3D.ZERO),
                new StateVector(new Vector3D(au, 0, 0), new Vector3D(0, 2.978e4, 0))};

        AccelerationModel model = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                        GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS)});

        StateVector[] result = integrator.step(input, 3600.0, model);

        // Earth started at +x with no x-velocity; after one step it moves inward
        assertTrue(result[1].velocity().x() < 0.0);
        // The tangential component is essentially unchanged over one hour
        assertEquals(2.978e4, result[1].velocity().y(), 1.0);
    }
}