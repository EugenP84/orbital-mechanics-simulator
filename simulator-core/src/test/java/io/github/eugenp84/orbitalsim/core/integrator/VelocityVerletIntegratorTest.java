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

class VelocityVerletIntegratorTest {

    private static final double RELATIVE_TOLERANCE = 1e-9;

    private final VelocityVerletIntegrator integrator = new VelocityVerletIntegrator();

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
        // An absolute floor keeps the tolerance meaningful when the expected magnitude is at or near zero, while remaining relative for large quantities.
        double tolerance = Math.max(1.0, Math.abs(scale)) * RELATIVE_TOLERANCE;
        assertEquals(expected.x(), actual.x(), tolerance, "x component");
        assertEquals(expected.y(), actual.y(), tolerance, "y component");
        assertEquals(expected.z(), actual.z(), tolerance, "z component");
    }

    // ---------------------------- Algorithm correctness ---=============================

    @Test
    void advancesStateAccordingToVelocityVerlet() {
        // r = (0, 100, 0), v = (10, 0, 0), a = (0, -10, 0), dt = 2
        //
        //   v_half = v + ½·a·dt = (10,0,0) + (0,-10,0) = (10, -10, 0)
        //   r_next = r + v_half·dt = (0,100,0) + (20,-20,0) = (20, 80, 0)
        //   v_next = v_half + ½·a·dt = (10,-10,0) + (0,-10,0) = (10, -20, 0)
        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};

        StateVector[] result = integrator.step(input, 2.0, constant(new Vector3D(0, -10, 0)));

        assertEquals(new Vector3D(20, 80, 0), result[0].position());
        assertEquals(new Vector3D(10, -20, 0), result[0].velocity());
    }

    @Test
    void reproducesAnalyticalSolutionUnderConstantAcceleration() {
        // Velocity Verlet has no local truncation error when acceleration is constant: the update is algebraically identical to
        //   r = r₀ + v₀·t + ½·a·t²      v = v₀ + a·t
        // !!!!!!! A tolerance is still used because floating-point rounding remains!!!!!!!!!!!!!
        Vector3D r0 = new Vector3D(5, -3, 11);
        Vector3D v0 = new Vector3D(2, 7, -4);
        Vector3D a = new Vector3D(-1, 0.5, 3);
        double dt = 4.0;

        StateVector[] result = integrator.step(new StateVector[]{new StateVector(r0, v0)}, dt, constant(a));

        Vector3D expectedPosition = r0
                .add(v0.multiply(dt))
                .add(a.multiply(0.5 * dt * dt));
        Vector3D expectedVelocity = v0.add(a.multiply(dt));

        assertVectorEquals(expectedPosition, result[0].position(), expectedPosition.magnitude());
        assertVectorEquals(expectedVelocity, result[0].velocity(), expectedVelocity.magnitude());
    }

    @Test
    void zeroAccelerationProducesStraightLineMotion() {
        Vector3D position = new Vector3D(1, 2, 3);
        Vector3D velocity = new Vector3D(10, -20, 30);
        double dt = 5.0;

        StateVector[] result = integrator.step(new StateVector[]{new StateVector(position, velocity)}, dt, zero());

        assertEquals(position.add(velocity.multiply(dt)), result[0].position());
        assertEquals(velocity, result[0].velocity());
    }

    @Test
    void eachBodyIsAdvancedIndependently() {
        StateVector[] input = {
                new StateVector(new Vector3D(0, 0, 0), new Vector3D(1, 0, 0)),
                new StateVector(new Vector3D(100, 0, 0), new Vector3D(0, 2, 0))};

        StateVector[] result = integrator.step(input, 10.0, zero());

        assertEquals(new Vector3D(10, 0, 0), result[0].position());
        assertEquals(new Vector3D(100, 20, 0), result[1].position());
    }

    // --- Multi-stage evaluation behaviour ---

    @Test
    void evaluatesTheAccelerationModelTwicePerStep() {
        // Two evaluations per step under the current stateless step signature.
        // An implementation characteristic, not part of the Integrator contract;
        // it is the comparison baseline for Objective 6 alongside Forward Euler's single evaluation.
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

        assertEquals(2, evaluations.get());
    }

    @Test
    void secondEvaluationOccursAtTheUpdatedPositions() {
        // The property Forward Euler could not exercise: the model is queried
        // at a trial state that is not the committed state of any body.
        // The first evaluation sees r(t); the second sees r(t + Δt).
        List<Vector3D[]> observedPositions = new ArrayList<>();

        Vector3D acceleration = new Vector3D(0, -10, 0);
        AccelerationModel recording = states -> {
            Vector3D[] positions = new Vector3D[states.length];
            for (int i = 0; i < states.length; i++) {
                positions[i] = states[i].position();
            }
            observedPositions.add(positions);

            Vector3D[] accelerations = new Vector3D[states.length];
            Arrays.fill(accelerations, acceleration);
            return accelerations;
        };

        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};

        StateVector[] result = integrator.step(input, 2.0, recording);

        assertEquals(2, observedPositions.size());
        assertEquals(new Vector3D(0, 100, 0), observedPositions.get(0)[0],"first evaluation must see the current position");
        assertEquals(new Vector3D(20, 80, 0), observedPositions.get(1)[0],"second evaluation must see the updated position");
        assertEquals(observedPositions.get(1)[0], result[0].position(),"the position evaluated second is the position returned");
    }

    @Test
    void trialStateCarriesTheHalfStepVelocity() {
        // Design-characterisation test, not an algorithm-correctness test.
        // The trial state is not a simultaneous snapshot: its position is at t + Δt while its velocity is at t + Δt/2.
        List<Vector3D> observedVelocities = new ArrayList<>();

        AccelerationModel recording = states -> {
            observedVelocities.add(states[0].velocity());
            return new Vector3D[]{new Vector3D(0, -10, 0)};
        };

        integrator.step(
                new StateVector[]{
                        new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))},
                2.0, recording);

        // v_half = (10,0,0) + ½·(0,-10,0)·2 = (10,-10,0)
        assertEquals(new Vector3D(10, 0, 0), observedVelocities.get(0));
        assertEquals(new Vector3D(10, -10, 0), observedVelocities.get(1));
    }

    // --- Purity and snapshot behaviour ---

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

    // --- Boundary behaviour ---

    @Test
    void emptySystemProducesEmptyResult() {
        assertEquals(0, integrator.step(new StateVector[0], 1.0, zero()).length);
    }

    @Test
    void emptySystemStillValidatesTheTimestep() {
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
    void malformedAccelerationResultIsRejectedOnTheSecondEvaluation() {
        // The second evaluation is validated as thoroughly as the first: a model conforming initially and failing later must still be caught
        StateVector[] states = {
                new StateVector(Vector3D.ZERO, new Vector3D(1, 0, 0))};

        AtomicInteger calls = new AtomicInteger();
        AccelerationModel failingOnSecondCall = s ->
                calls.incrementAndGet() == 1
                        ? new Vector3D[]{Vector3D.ZERO}
                        : new Vector3D[]{new Vector3D(Double.POSITIVE_INFINITY, 0, 0)};

        assertThrows(IllegalStateException.class,
                () -> integrator.step(states, 1.0, failingOnSecondCall));
        assertEquals(2, calls.get(), "the failure must occur on the second evaluation");
    }

    @Test
    void failureOnTheSecondEvaluationLeavesInputUnchanged() {
        // Multi-stage methods must still satisfy the two-phase contract: a step that throws part-way through has committed nothing
        StateVector original = new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0));
        StateVector[] input = {original};

        AtomicInteger calls = new AtomicInteger();
        AccelerationModel failingOnSecondCall = s ->
                calls.incrementAndGet() == 1
                        ? new Vector3D[]{new Vector3D(0, -10, 0)}
                        : null;

        assertThrows(IllegalStateException.class,
                () -> integrator.step(input, 2.0, failingOnSecondCall));

        assertSame(original, input[0]);
        assertEquals(new Vector3D(0, 100, 0), input[0].position());
        assertEquals(new Vector3D(10, 0, 0), input[0].velocity());
    }

    // ---------------------- Composition and comparison --------------

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

        // Earth acquires an inward velocity component and retains its tangential motion over one hour. Both bodies contribute gravity,
        // so the Sun also moves slightly; - correct for a fully coupled two-body system.
        assertTrue(result[1].velocity().x() < 0.0);
        assertEquals(orbitalSpeed, result[1].velocity().y(), 1.0);
    }

    @Test
    void differsFromForwardEulerOnTheSameInputs() {
        // Both methods receive identical inputs, so any difference in the returned position is attributable to the integration scheme.
        // Under constant acceleration the Verlet result also coincides with the analytical solution, while Forward Euler does not.
        StateVector[] input = {
                new StateVector(new Vector3D(0, 100, 0), new Vector3D(10, 0, 0))};
        AccelerationModel model = constant(new Vector3D(0, -10, 0));

        StateVector euler = new EulerIntegrator().step(input, 2.0, model)[0];
        StateVector verlet = integrator.step(input, 2.0, model)[0];

        assertEquals(euler.velocity(), verlet.velocity());
        assertNotEquals(euler.position(), verlet.position());
        assertEquals(new Vector3D(20, 100, 0), euler.position());
        assertEquals(new Vector3D(20, 80, 0), verlet.position());
    }
}