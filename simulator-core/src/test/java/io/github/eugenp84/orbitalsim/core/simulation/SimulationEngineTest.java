package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.integrator.EulerIntegrator;
import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private static final double DT = 60.0;

    // --------------========= Test fixtures ----------------------------------

    private static CelestialBody star() {
        return new CelestialBody(
                "star-1", "Test Star", CelestialBodyType.STAR,
                PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS);
    }

    private static CelestialBody planet() {
        return new CelestialBody(
                "planet-1", "Test Planet", CelestialBodyType.PLANET,
                PhysicalConstants.EARTH_MASS, PhysicalConstants.EARTH_RADIUS);
    }

    private static List<BodySnapshot> twoBodies() {
        return List.of(
                new BodySnapshot(star(),
                        new StateVector(Vector3D.ZERO, Vector3D.ZERO)),
                new BodySnapshot(planet(),
                        new StateVector(
                                new Vector3D(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0),
                                new Vector3D(0, 2.978e4, 0))));
    }

    /** A model that is never actually consulted by the stub integrators. */
    private static AccelerationModel unusedModel() {
        return states -> {
            Vector3D[] a = new Vector3D[states.length];
            Arrays.fill(a, Vector3D.ZERO);
            return a;
        };
    }

    /**
     * A deterministic stub: shifts every body's position by a fixed offset per step and leaves velocity unchanged.
     * Distinguishable outputs allow strategy-switch verification without numerical machinery.
     */
    private static Integrator shiftingIntegrator(Vector3D offsetPerStep) {
        return (states, dt, model) -> {
            StateVector[] next = new StateVector[states.length];
            for (int i = 0; i < states.length; i++) {
                next[i] = new StateVector(
                        states[i].position().add(offsetPerStep),
                        states[i].velocity());
            }
            return next;
        };
    }

    /** Produces a non-finite state at exactly one body index. */
    private static Integrator breakdownIntegrator(int atIndex) {
        return (states, dt, model) -> {
            StateVector[] next = new StateVector[states.length];
            for (int i = 0; i < states.length; i++) {
                next[i] = (i == atIndex)
                        ? new StateVector(
                        new Vector3D(Double.NaN, 0, 0), states[i].velocity())
                        : states[i];
            }
            return next;
        };
    }

    private static SimulationEngine engineWith(Integrator integrator) {
        return new SimulationEngine(twoBodies(), unusedModel(), DT, integrator);
    }

    // ------------------ Construction contract ----

    @Test
    void constructorRejectsInvalidArguments() {
        List<BodySnapshot> bodies = twoBodies();
        AccelerationModel model = unusedModel();
        Integrator integrator = new EulerIntegrator();

        assertThrows(NullPointerException.class,
                () -> new SimulationEngine(null, model, DT, integrator));
        assertThrows(NullPointerException.class,
                () -> new SimulationEngine(bodies, null, DT, integrator));
        assertThrows(NullPointerException.class,
                () -> new SimulationEngine(bodies, model, DT, null));

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(List.of(), model, DT, integrator));

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(bodies, model, 0.0, integrator));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(bodies, model, -1.0, integrator));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(bodies, model, Double.NaN, integrator));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(bodies, model, Double.POSITIVE_INFINITY, integrator));
    }

    @Test
    void constructorRejectsNullBodyElement() {
        // Built with ArrayList deliberately: List.of would itself reject the null during fixture construction, testing the JDK rather than the
        // engine's documented contract (implemented via List.copyOf)
        List<BodySnapshot> bodies = new ArrayList<>(twoBodies());
        bodies.set(1, null);

        assertThrows(NullPointerException.class,
                () -> new SimulationEngine(bodies, unusedModel(), DT, new EulerIntegrator()));
    }

    @Test
    void constructionDefensivelyIsolatesTheBodyList() {
        List<BodySnapshot> mutable = new ArrayList<>(twoBodies());
        SimulationEngine engine = new SimulationEngine(
                mutable, unusedModel(), DT, shiftingIntegrator(new Vector3D(1, 0, 0)));

        mutable.clear();

        assertEquals(2, engine.snapshot().bodies().size());
    }

    // -----------------===- Initial state =============---

    @Test
    void initialSnapshotIsAtTimeZeroWithZeroSteps() {
        SimulationEngine engine = engineWith(new EulerIntegrator());

        SimulationSnapshot snapshot = engine.snapshot();

        assertEquals(0.0, snapshot.simulationTimeSeconds());
        assertEquals(0L, snapshot.completedSteps());
        assertEquals(2, snapshot.bodies().size());
        assertEquals("star-1", snapshot.bodies().get(0).definition().id());
        assertEquals("planet-1", snapshot.bodies().get(1).definition().id());
    }

    // ---================ Successful step path ===================================---

    @Test
    void successfulStepCommitsAllStatesAndAdvancesTheClockOnce() {
        Vector3D shift = new Vector3D(100, 0, 0);
        SimulationEngine engine = engineWith(shiftingIntegrator(shift));

        SimulationSnapshot before = engine.snapshot();
        StepResult result = engine.step();

        assertInstanceOf(StepResult.Advanced.class, result);
        SimulationSnapshot after = ((StepResult.Advanced) result).snapshot();

        assertEquals(1L, after.completedSteps());
        assertEquals(DT, after.simulationTimeSeconds());

        for (int i = 0; i < 2; i++) {
            assertEquals(
                    before.bodies().get(i).state().position().add(shift),
                    after.bodies().get(i).state().position(),
                    "body " + i + " must be shifted");
        }
    }

    @Test
    void twoStepsYieldTwoCompletedStepsAndDerivedTime() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));

        engine.step();
        StepResult second = engine.step();

        SimulationSnapshot snapshot = ((StepResult.Advanced) second).snapshot();
        assertEquals(2L, snapshot.completedSteps());
        assertEquals(2 * DT, snapshot.simulationTimeSeconds());
        assertEquals(snapshot.completedSteps() * DT, snapshot.simulationTimeSeconds());
    }

    @Test
    void engineSnapshotMatchesTheResultSnapshotAfterAStep() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(5, 0, 0)));

        StepResult result = engine.step();

        assertEquals(((StepResult.Advanced) result).snapshot(), engine.snapshot());
    }

    // ---------------------- Numerical breakdown path -------------------------------

    @Test
    void nonFiniteCandidateYieldsNumericalBreakdown() {
        SimulationEngine engine = engineWith(breakdownIntegrator(0));

        StepResult result = engine.step();

        assertInstanceOf(StepResult.NumericalBreakdown.class, result);
    }

    @Test
    void breakdownLeavesStateAndClockUntouched() {
        SimulationEngine engine = engineWith(breakdownIntegrator(1));
        SimulationSnapshot before = engine.snapshot();

        engine.step();

        SimulationSnapshot after = engine.snapshot();
        assertEquals(before, after,
                "a rejected candidate must leave state and clock exactly as they were");
        assertEquals(0L, after.completedSteps());
        assertEquals(0.0, after.simulationTimeSeconds());
    }

    @Test
    void breakdownReportsAttemptedTimeAndLowestInvalidIndex() {
        SimulationEngine engine = engineWith(breakdownIntegrator(1));

        // Advance twice successfully first, then break down
        engine.selectIntegrator(shiftingIntegrator(new Vector3D(1, 0, 0)));
        engine.step();
        engine.step();
        engine.selectIntegrator(breakdownIntegrator(1));

        StepResult result = engine.step();
        StepResult.NumericalBreakdown breakdown =
                assertInstanceOf(StepResult.NumericalBreakdown.class, result);

        assertEquals(3 * DT, breakdown.attemptedTimeSeconds(),
                "attempted time is (completedSteps + 1) x dt");
        assertEquals(1, breakdown.firstNonFiniteBodyIndex());

        assertEquals(2L, engine.snapshot().completedSteps(),
                "the failed attempt must not advance the clock");
    }

    @Test
    void lowestNonFiniteIndexIsReportedWhenSeveralAreInvalid() {
        Integrator allInvalid = (states, dt, model) -> {
            StateVector[] next = new StateVector[states.length];
            for (int i = 0; i < states.length; i++) {
                next[i] = new StateVector(
                        new Vector3D(Double.POSITIVE_INFINITY, 0, 0), Vector3D.ZERO);
            }
            return next;
        };
        SimulationEngine engine = engineWith(allInvalid);

        StepResult.NumericalBreakdown breakdown = assertInstanceOf(
                StepResult.NumericalBreakdown.class, engine.step());

        assertEquals(0, breakdown.firstNonFiniteBodyIndex());
    }

    // ---================ Malformed integrator (collaborator defect, not breakdown) ===---

    @Test
    void nullCandidateArrayIsACollaboratorDefectAndCommitsNothing() {
        SimulationEngine engine = engineWith((states, dt, model) -> null);
        SimulationSnapshot before = engine.snapshot();

        assertThrows(IllegalStateException.class, engine::step);

        // Full snapshot equality, not just the step counter: a counter-only
        // assertion would pass even if body states were partially written
        // before the exception escaped
        assertEquals(before, engine.snapshot(),
                "a collaborator defect must not commit state or advance the clock");
    }

    @Test
    void wrongLengthCandidateIsACollaboratorDefectAndCommitsNothing() {
        SimulationEngine shortEngine = engineWith(
                (states, dt, model) -> new StateVector[states.length - 1]);
        SimulationSnapshot beforeShort = shortEngine.snapshot();

        assertThrows(IllegalStateException.class, shortEngine::step);
        assertEquals(beforeShort, shortEngine.snapshot());

        // A LONGER array must also be rejected: silently committing the first N states would ignore candidate states without any error
        SimulationEngine longEngine = engineWith((states, dt, model) -> {
            StateVector[] tooMany = new StateVector[states.length + 1];
            Arrays.fill(tooMany, new StateVector(Vector3D.ZERO, Vector3D.ZERO));
            return tooMany;
        });
        SimulationSnapshot beforeLong = longEngine.snapshot();

        assertThrows(IllegalStateException.class, longEngine::step);
        assertEquals(beforeLong, longEngine.snapshot());
    }

    @Test
    void nullCandidateElementIsACollaboratorDefectAndCommitsNothing() {
        SimulationEngine engine = engineWith((states, dt, model) -> {
            StateVector[] next = new StateVector[states.length];
            next[0] = new StateVector(Vector3D.ZERO, Vector3D.ZERO);
            // next[1] left null
            return next;
        });
        SimulationSnapshot before = engine.snapshot();

        assertThrows(IllegalStateException.class, engine::step);
        assertEquals(before, engine.snapshot());
    }

    @Test
    void collaboratorDefectIsNeverReportedAsNumericalBreakdown() {
        SimulationEngine engine = engineWith((states, dt, model) -> null);

        // The defect surfaces as an exception, not as a StepResult variant — the taxonomy of ADR-005 §4 applied at the engine boundary
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, engine::step);
        assertTrue(thrown.getMessage().contains("Integrator"));
    }

    // --- Integrator strategy switching ---

    @Test
    void selectIntegratorChangesStrategyWithoutChangingState() {
        Vector3D firstShift = new Vector3D(10, 0, 0);
        Vector3D secondShift = new Vector3D(0, 20, 0);

        SimulationEngine engine = engineWith(shiftingIntegrator(firstShift));
        engine.step();

        SimulationSnapshot beforeSwitch = engine.snapshot();
        engine.selectIntegrator(shiftingIntegrator(secondShift));
        SimulationSnapshot afterSwitch = engine.snapshot();

        assertEquals(beforeSwitch, afterSwitch,
                "switching the strategy must not alter state or clock (ADR-006 s10)");
        assertEquals(DT, engine.integrationStepSeconds(), "timestep unchanged");

        // The next step uses the new strategy
        StepResult result = engine.step();
        SimulationSnapshot after = ((StepResult.Advanced) result).snapshot();

        Vector3D expected = beforeSwitch.bodies().get(0).state().position().add(secondShift);
        assertEquals(expected, after.bodies().get(0).state().position());
    }

    @Test
    void selectIntegratorRejectsNull() {
        SimulationEngine engine = engineWith(new EulerIntegrator());
        assertThrows(NullPointerException.class, () -> engine.selectIntegrator(null));
    }

    // --======================- Snapshot isolation =======================================---

    @Test
    void snapshotCannotBeUsedToMutateEngineState() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));

        SimulationSnapshot snapshot = engine.snapshot();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.bodies().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.bodies().add(snapshot.bodies().get(0)));

        assertEquals(2, engine.snapshot().bodies().size());
    }

    // --------------- Composition with the real numerical stack -------------------------

    @Test
    void composesWithRealIntegratorAndAccelerationModel() {
        // One step of the full production stack: engine -> Euler -> N-body
        // model. Numerical accuracy is covered by the integrator suites; this
        // verifies only that the layers connect and commit
        AccelerationModel gravity = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                        GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS)});

        SimulationEngine engine = new SimulationEngine(
                twoBodies(), gravity, 3600.0, new EulerIntegrator());

        StepResult result = engine.step();

        SimulationSnapshot after = assertInstanceOf(StepResult.Advanced.class, result).snapshot();
        assertEquals(1L, after.completedSteps());
        assertEquals(3600.0, after.simulationTimeSeconds());

        // The planet moved tangentially and acquired an inward velocity component
        StateVector planetState = after.bodies().get(1).state();
        assertTrue(planetState.position().y() > 0.0);
        assertTrue(planetState.velocity().x() < 0.0);
    }
}