package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RungeKutta4IntegratorTest {

    private static final double RELATIVE_TOLERANCE = 1e-9;

    private final RungeKutta4Integrator integrator = new RungeKutta4Integrator();

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
        // An absolute floor keeps the tolerance meaningful when the expected
        // magnitude is at or near zero, while remaining relative for large quantities.
        double tolerance = Math.max(1.0, Math.abs(scale)) * RELATIVE_TOLERANCE;
        assertEquals(expected.x(), actual.x(), tolerance, "x component");
        assertEquals(expected.y(), actual.y(), tolerance, "y component");
        assertEquals(expected.z(), actual.z(), tolerance, "z component");
    }

    // ---============================ Algorithm correctness ---

    @Test
    void reproducesAnalyticalSolutionUnderConstantAcceleration() {
        // With constant acceleration all four stages return the same value, so
        // the weighted average reduces to that value and RK4 reproduces the
        // kinematic solution with no local truncation error. Only floating-
        // point rounding remains, hence the tolerance.
        Vector3D r0 = new Vector3D(5, -3, 11);
        Vector3D v0 = new Vector3D(2, 7, -4);
        Vector3D a = new Vector3D(-1, 0.5, 3);
        double dt = 4.0;

        StateVector[] result = integrator.step(
                new StateVector[]{new StateVector(r0, v0)}, dt, constant(a));

        Vector3D expectedPosition = r0
                .add(v0.multiply(dt))
                .add(a.multiply(0.5 * dt * dt));
        Vector3D expectedVelocity = v0.add(a.multiply(dt));

        assertVectorEquals(expectedPosition, result[0].position(), expectedPosition.magnitude());
        assertVectorEquals(expectedVelocity, result[0].velocity(), expectedVelocity.magnitude());
    }

    @Test
    void matchesTheHarmonicOscillatorAnalyticalSolution() {
        // a = -ω²r with ω = 1, starting at r = (1,0,0), v = 0, has the exact
        // solution x(t) = cos t, v(t) = -sin t. Unlike the constant- and zero-acceleration cases, acceleration here changes at every trial
        // position, so this exercises the RK4 stage structure itself — and it does so without involving GravityCalculator.
        //
        // Integration ends at t = 2.0 rather than at a full period: for a linear oscillator RK4's leading error term is a pure phase error, so
        // amplitude error at a period boundary converges one order faster and would misrepresent the method's accuracy.
        AccelerationModel harmonic = states -> {
            Vector3D[] accelerations = new Vector3D[states.length];
            for (int i = 0; i < states.length; i++) {
                accelerations[i] = states[i].position().multiply(-1.0);
            }
            return accelerations;
        };

        double endTime = 2.0;
        int steps = 40;
        double dt = endTime / steps;

        StateVector[] states = {
                new StateVector(new Vector3D(1, 0, 0), Vector3D.ZERO)};

        for (int i = 0; i < steps; i++) {
            states = integrator.step(states, dt, harmonic);
        }

        assertEquals(Math.cos(endTime), states[0].position().x(), 1e-6);
        assertEquals(-Math.sin(endTime), states[0].velocity().x(), 1e-6);
    }

    @Test
    void agreesWithVelocityVerletUnderConstantAcceleration() {
        // Both methods are exact for constant acceleration, so they must agree with each other and with the analytical result
        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};
        AccelerationModel model = constant(new Vector3D(0, -10, 0));

        StateVector rk4 = integrator.step(input, 2.0, model)[0];
        StateVector verlet = new VelocityVerletIntegrator().step(input, 2.0, model)[0];

        assertVectorEquals(verlet.position(), rk4.position(), 100.0);
        assertVectorEquals(verlet.velocity(), rk4.velocity(), 20.0);
        assertVectorEquals(new Vector3D(20, 80, 0), rk4.position(), 100.0);
    }

    @Test
    void zeroAccelerationProducesStraightLineMotion() {
        Vector3D position = new Vector3D(1, 2, 3);
        Vector3D velocity = new Vector3D(10, -20, 30);
        double dt = 5.0;

        StateVector[] result = integrator.step(new StateVector[]{new StateVector(position, velocity)}, dt, zero());

        assertVectorEquals(position.add(velocity.multiply(dt)), result[0].position(), 200.0);
        assertVectorEquals(velocity, result[0].velocity(), 30.0);
    }

    @Test
    void eachBodyIsAdvancedIndependently() {
        StateVector[] input = {
                new StateVector(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0)),
                new StateVector(new Vector3D(100, 0, 0), new Vector3D(0, 2, 0))};

        StateVector[] result = integrator.step(input, 10.0, zero());

        assertVectorEquals(new Vector3D(10, 0, 0), result[0].position(), 100.0);
        assertVectorEquals(new Vector3D(100, 20, 0), result[1].position(), 100.0);
    }

    // --- Multi-stage evaluation behaviour ---

    @Test
    void evaluatesTheAccelerationModelFourTimesPerStep() {
        // Four evaluations per step, intrinsic to classical RK4.
        // Compare with Forward Euler (one) and Velocity Verlet (two) — the basis of the computational cost comparison in Objective 6.
        AtomicInteger evaluations = new AtomicInteger();

        AccelerationModel counting = states -> {
            evaluations.incrementAndGet();
            Vector3D[] accelerations = new Vector3D[states.length];
            Arrays.fill(accelerations, Vector3D.ZERO);
            return accelerations;
        };

        integrator.step(
                new StateVector[]{new StateVector(Vector3D.ZERO, new Vector3D(1, 0, 0))},
                1.0, counting);

        assertEquals(4, evaluations.get());
    }

    @Test
    void stagesAreEvaluatedAtTheExpectedTrialPositions() {
        // Verifies the stage structure: k₁ at r, k₂ and k₃ at midpoints reached using the preceding stage, k₄ at the endpoint reached using k₃.
        //
        // With zero acceleration the velocity never changes, so the trial positions are analytically simple:
        //   stage 1: r
        //   stage 2: r + ½·v·Δt
        //   stage 3: r + ½·v·Δt   (k₂ᵥ equals v when acceleration is zero)
        //   stage 4: r + v·Δt
        List<Vector3D> observed = new ArrayList<>();

        AccelerationModel recording = states -> {
            observed.add(states[0].position());
            return new Vector3D[]{Vector3D.ZERO};
        };

        Vector3D r = new Vector3D(0, 0, 0);
        Vector3D v = new Vector3D(100, 0, 0);
        double dt = 2.0;

        integrator.step(new StateVector[]{new StateVector(r, v)}, dt, recording);

        assertEquals(4, observed.size());
        assertVectorEquals(r, observed.get(0), 100.0);
        assertVectorEquals(new Vector3D(100, 0, 0), observed.get(1), 100.0);
        assertVectorEquals(new Vector3D(100, 0, 0), observed.get(2), 100.0);
        assertVectorEquals(new Vector3D(200, 0, 0), observed.get(3), 100.0);
    }

    @Test
    void intermediateStagesCarryStageAlignedVelocities() {
        // Each RK4 trial state's position and velocity form the stage approximation associated with the same stage time,
        // unlike Velocity Verlet's deliberately staggered carrier. With constant acceleration a and zero initial velocity:
        //   stage 2 velocity = ½·a·Δt
        //   stage 4 velocity = k₃ₐ·Δt = a·Δt
        List<Vector3D> observed = new ArrayList<>();

        Vector3D acceleration = new Vector3D(0, -10, 0);
        AccelerationModel recording = states -> {
            observed.add(states[0].velocity());
            return new Vector3D[]{acceleration};
        };

        integrator.step(
                new StateVector[]{new StateVector(Vector3D.ZERO, Vector3D.ZERO)},
                2.0, recording);

        assertVectorEquals(Vector3D.ZERO, observed.get(0), 1.0);
        assertVectorEquals(new Vector3D(0, -10, 0), observed.get(1), 20.0);
        assertVectorEquals(new Vector3D(0, -10, 0), observed.get(2), 20.0);
        assertVectorEquals(new Vector3D(0, -20, 0), observed.get(3), 20.0);
    }

    @Test
    void everyStageIsAdvancedFromTheInitialState() {
        // A common RK4 error is to advance stage 3 from stage 2 rather than again from the state at the start of the step.
        // With constant velocity and zero acceleration,
        // cumulative advancement would place stage 3 at r + v·Δt rather than r + ½·v·Δt, and stage 4 at r + 1.5·v·Δt rather than r + v·Δt.
        List<Vector3D> observed = new ArrayList<>();

        AccelerationModel recording = states -> {
            observed.add(states[0].position());
            return new Vector3D[]{Vector3D.ZERO};
        };

        integrator.step(
                new StateVector[]{
                        new StateVector(Vector3D.ZERO, new Vector3D(100, 0, 0))},
                2.0, recording);

        assertVectorEquals(new Vector3D(100, 0, 0), observed.get(2), 100.0);
        assertVectorEquals(new Vector3D(200, 0, 0), observed.get(3), 100.0);
    }

    // ------------------------- Purity and snapshot behaviour ---===============--

    @Test
    void inputArrayAndElementsAreUnchanged() {
        StateVector original = new StateVector(
                new Vector3D(0, 100, 0), new Vector3D(10, 0, 0));
        StateVector[] input = {original};

        integrator.step(input, 2.0, constant(new Vector3D(0, -10, 0)));

        assertSame(original, input[0]);
        assertEquals(new Vector3D(0, 100, 0), input[0].position());
        assertEquals(new Vector3D(10, 0, 0), input[0].velocity());
    }

    @Test
    void resultArrayIsDistinctFromInput() {
        StateVector[] input = {
                new StateVector(new Vector3D(1, 2, 3), new Vector3D(4, 5, 6))};

        assertNotSame(input, integrator.step(input, 1.0, zero()));
    }

    @Test
    void repeatedStepsFromTheSameInputProduceEqualResults() {
        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};
        AccelerationModel model = constant(new Vector3D(0, -10, 0));

        StateVector[] first = integrator.step(input, 2.0, model);
        StateVector[] second = integrator.step(input, 2.0, model);

        assertEquals(first[0], second[0]);
    }

    // ------------ Boundary behaviour ---------------------

    @Test
    void emptySystemProducesEmptyResult() {
        assertEquals(0, integrator.step(new StateVector[0], 1.0, zero()).length);
    }

    @Test
    void emptySystemStillValidatesTheTimestep() {
        assertThrows(IllegalArgumentException.class,
                () -> integrator.step(new StateVector[0], -1.0, zero()));
    }

    // --======================--- Argument contract ---

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

    // --------------- AccelerationModel contract enforcement ---------------------------------

    @Test
    void malformedAccelerationResultIsRejectedOnTheFirstEvaluation() {
        StateVector[] states = {new StateVector(Vector3D.ZERO, Vector3D.ZERO)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> null));
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> new Vector3D[s.length + 1]));
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, s -> new Vector3D[]{null}));
        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0,
                        s -> new Vector3D[]{new Vector3D(Double.NaN, 0, 0)}));
    }

    @Test
    void everyStageValidatesTheAccelerationModelResult() {
        // Each of the four evaluations is validated. A model conforming for the
        // first n calls and failing on call n+1 must still be caught.
        for (int failingCall = 1; failingCall <= 4; failingCall++) {
            AtomicInteger calls = new AtomicInteger();
            int target = failingCall;

            AccelerationModel failingOnNthCall = s ->
                    calls.incrementAndGet() == target
                            ? new Vector3D[]{new Vector3D(Double.NaN, 0, 0)}
                            : new Vector3D[]{Vector3D.ZERO};

            StateVector[] states = {
                    new StateVector(Vector3D.ZERO, new Vector3D(1, 0, 0))};

            assertThrows(IllegalStateException.class,
                    () -> integrator.step(states, 1.0, failingOnNthCall),
                    "a failure on evaluation " + target + " must be detected");
            assertEquals(target, calls.get(),
                    "the step must stop at evaluation " + target);
        }
    }

    @Test
    void failureOnALaterStageLeavesInputUnchanged() {
        // The two-phase contract holds for a four-stage method: a step failing at its third evaluation has committed nothing
        StateVector original = new StateVector(
                new Vector3D(0, 100, 0), new Vector3D(10, 0, 0));
        StateVector[] input = {original};

        AtomicInteger calls = new AtomicInteger();
        AccelerationModel failingOnThirdCall = s ->
                calls.incrementAndGet() == 3
                        ? null
                        : new Vector3D[]{new Vector3D(0, -10, 0)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(input, 2.0, failingOnThirdCall));

        assertSame(original, input[0]);
        assertEquals(new Vector3D(0, 100, 0), input[0].position());
        assertEquals(new Vector3D(10, 0, 0), input[0].velocity());
    }

    // --------- Composition with the production acceleration model !!!!!!!!!!!!!! -------------

    @Test
    void composesWithNBodyAccelerationCalculator() {
        double au = PhysicalConstants.ASTRONOMICAL_UNIT;
        double orbitalSpeed = 2.978e4;

        StateVector[] input = {
                new StateVector(Vector3D.ZERO, Vector3D.ZERO),
                new StateVector(new Vector3D(au, 0, 0), new Vector3D(0, orbitalSpeed, 0))};

        AccelerationModel model = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                        GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS)});

        StateVector[] result = integrator.step(input, 3600.0, model);

        assertTrue(result[1].velocity().x() < 0.0);
        assertEquals(orbitalSpeed, result[1].velocity().y(), 1.0);
    }
}