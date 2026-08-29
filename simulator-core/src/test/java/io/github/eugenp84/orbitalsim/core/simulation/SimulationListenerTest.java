package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationListenerTest {

    private static final double DT = 60.0;

    // --- Fixtures ---

    private static List<BodySnapshot> twoBodies() {
        return List.of(
                new BodySnapshot(
                        new CelestialBody("star-1", "Test Star", CelestialBodyType.STAR,
                                PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS),
                        new StateVector(Vector3D.ZERO, Vector3D.ZERO)),
                new BodySnapshot(
                        new CelestialBody("planet-1", "Test Planet", CelestialBodyType.PLANET,
                                PhysicalConstants.EARTH_MASS, PhysicalConstants.EARTH_RADIUS),
                        new StateVector(
                                new Vector3D(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0),
                                new Vector3D(0, 2.978e4, 0))));
    }

    private static AccelerationModel unusedModel() {
        return states -> {
            Vector3D[] a = new Vector3D[states.length];
            Arrays.fill(a, Vector3D.ZERO);
            return a;
        };
    }

    /** Advances every body by a fixed offset per step. */
    private static Integrator shiftingIntegrator(Vector3D offset) {
        return (states, dt, model) -> {
            StateVector[] next = new StateVector[states.length];
            for (int i = 0; i < states.length; i++) {
                next[i] = new StateVector(
                        states[i].position().add(offset), states[i].velocity());
            }
            return next;
        };
    }

    /** Produces a non-finite state at body 0. */
    private static Integrator breakdownIntegrator() {
        return (states, dt, model) -> {
            StateVector[] next = new StateVector[states.length];
            next[0] = new StateVector(new Vector3D(Double.NaN, 0, 0), Vector3D.ZERO);
            for (int i = 1; i < states.length; i++) {
                next[i] = states[i];
            }
            return next;
        };
    }

    /** Succeeds for the first {@code successes} calls, then breaks down. */
    private static Integrator breakingAfter(int successes) {
        return new Integrator() {
            private int calls = 0;

            @Override
            public StateVector[] step(StateVector[] states, double dt, AccelerationModel model) {
                calls++;
                StateVector[] next = new StateVector[states.length];
                for (int i = 0; i < states.length; i++) {
                    next[i] = (calls > successes && i == 0)
                            ? new StateVector(new Vector3D(Double.NaN, 0, 0), Vector3D.ZERO)
                            : new StateVector(
                            states[i].position().add(new Vector3D(1, 0, 0)),
                            states[i].velocity());
                }
                return next;
            }
        };
    }

    private static SimulationEngine engineWith(Integrator integrator) {
        return new SimulationEngine(twoBodies(), unusedModel(), DT, integrator);
    }

    private static SimulationEngine engineWith(
            Integrator integrator, ListenerErrorHandler handler) {
        return new SimulationEngine(twoBodies(), unusedModel(), DT, integrator, handler);
    }

    /** A listener that records every callback it receives, in order. */
    private static class RecordingListener implements SimulationListener {
        final List<SimulationSnapshot> snapshots = new ArrayList<>();
        final List<StepResult.NumericalBreakdown> breakdowns = new ArrayList<>();
        final List<String> order = new ArrayList<>();

        @Override
        public void onSnapshot(SimulationSnapshot snapshot) {
            snapshots.add(snapshot);
            order.add("snapshot");
        }

        @Override
        public void onNumericalBreakdown(
                StepResult.NumericalBreakdown breakdown, SimulationSnapshot lastCommitted) {
            breakdowns.add(breakdown);
            order.add("breakdown");
        }
    }

    // --- 1-2: step() publication ---

    @Test
    void successfulStepPublishesExactlyOneSnapshotAndNoBreakdown() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.step();

        assertEquals(1, listener.snapshots.size());
        assertEquals(0, listener.breakdowns.size());
        // The published snapshot is the committed state
        assertEquals(1L, listener.snapshots.get(0).completedSteps());
    }

    @Test
    void breakdownStepPublishesExactlyOneBreakdownAndNoSnapshot() {
        SimulationEngine engine = engineWith(breakdownIntegrator());
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.step();

        assertEquals(0, listener.snapshots.size(),
                "no state changed, so no snapshot must be published");
        assertEquals(1, listener.breakdowns.size());
        assertEquals(0, listener.breakdowns.get(0).firstNonFiniteBodyIndex());
    }

    // --- 3-5: advanceBy() publication cadence ---

    @Test
    void advanceByWithZeroCommittedStepsPublishesNothing() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.advanceBy(DT / 2);   // below one step

        assertTrue(listener.order.isEmpty(), "a zero-step advance must publish nothing");
    }

    @Test
    void advanceByWithSeveralStepsPublishesExactlyOneSnapshot() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.advanceBy(3 * DT);   // three substeps, one event

        assertEquals(1, listener.snapshots.size(),
                "publication cadence is per call, not per substep (ADR-006 s7)");
        assertEquals(3L, listener.snapshots.get(0).completedSteps());
        assertEquals(0, listener.breakdowns.size());
    }

    @Test
    void advanceByPartialSuccessPublishesSnapshotThenBreakdownInOrder() {
        SimulationEngine engine = engineWith(breakingAfter(2));
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.advanceBy(5 * DT);   // two succeed, third breaks down

        assertEquals(List.of("snapshot", "breakdown"), listener.order,
                "final committed snapshot must precede the breakdown notification");
        assertEquals(2L, listener.snapshots.get(0).completedSteps());
        assertEquals(3 * DT, listener.breakdowns.get(0).attemptedTimeSeconds());
    }

    @Test
    void advanceByImmediateBreakdownPublishesBreakdownOnly() {
        SimulationEngine engine = engineWith(breakdownIntegrator());
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.advanceBy(3 * DT);   // first substep breaks down

        assertEquals(List.of("breakdown"), listener.order);
    }

    // --- 6-10: failure isolation ---

    @Test
    void aFailingListenerDoesNotPreventOthersFromBeingNotified() {
        SimulationEngine engine = engineWith(
                shiftingIntegrator(new Vector3D(1, 0, 0)),
                (listener, failure) -> { /* swallow for this test */ });

        engine.addListener(new SimulationListener() {
            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                throw new RuntimeException("boom");
            }
        });
        RecordingListener healthy = new RecordingListener();
        engine.addListener(healthy);

        engine.step();

        assertEquals(1, healthy.snapshots.size(),
                "a listener failing must not starve a later listener");
    }

    @Test
    void aFailingListenerDoesNotEscapeStepOrAdvanceBy() {
        SimulationEngine engine = engineWith(
                shiftingIntegrator(new Vector3D(1, 0, 0)),
                (listener, failure) -> { });
        engine.addListener(new SimulationListener() {
            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                throw new RuntimeException("boom");
            }
        });

        assertDoesNotThrow(engine::step);
        assertDoesNotThrow(() -> engine.advanceBy(DT));
    }

    @Test
    void errorHandlerIsInvokedOnceWithTheFailingListenerAndException() {
        List<SimulationListener> reportedListeners = new ArrayList<>();
        List<RuntimeException> reportedFailures = new ArrayList<>();
        ListenerErrorHandler handler = (listener, failure) -> {
            reportedListeners.add(listener);
            reportedFailures.add(failure);
        };

        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)), handler);
        RuntimeException thrown = new RuntimeException("boom");
        SimulationListener faulty = new SimulationListener() {
            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                throw thrown;
            }
        };
        engine.addListener(faulty);

        engine.step();

        assertEquals(1, reportedFailures.size());
        assertSame(faulty, reportedListeners.get(0));
        assertSame(thrown, reportedFailures.get(0));
    }

    @Test
    void errorHandlerThrowing_doesNotAffectCallerOrOtherListeners() {
        SimulationEngine engine = engineWith(
                shiftingIntegrator(new Vector3D(1, 0, 0)),
                (listener, failure) -> { throw new RuntimeException("handler boom"); });

        engine.addListener(new SimulationListener() {
            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                throw new RuntimeException("listener boom");
            }
        });
        RecordingListener healthy = new RecordingListener();
        engine.addListener(healthy);

        // Handler throwing must not escape, and the next listener still runs
        assertDoesNotThrow(engine::step);
        assertEquals(1, healthy.snapshots.size());
    }

    @Test
    void anAssertionErrorFromAListenerPropagates() {
        // ADR-006 s9: the isolation boundary is RuntimeException. AssertionError
        // signals a violated programmer assumption and must not be swallowed.
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        engine.addListener(new SimulationListener() {
            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                throw new AssertionError("must surface");
            }
        });

        assertThrows(AssertionError.class, engine::step);
    }

    // --- 11-12: registration ---

    @Test
    void duplicateRegistrationProducesOnlyOneCallback() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        RecordingListener listener = new RecordingListener();

        engine.addListener(listener);
        engine.addListener(listener);   // idempotent

        engine.step();

        assertEquals(1, listener.snapshots.size());
    }

    @Test
    void aRemovedListenerReceivesNoFurtherCallbacks() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        RecordingListener listener = new RecordingListener();
        engine.addListener(listener);

        engine.step();
        assertTrue(engine.removeListener(listener));
        engine.step();

        assertEquals(1, listener.snapshots.size(), "no callback after removal");
    }

    @Test
    void registrationRejectsNull() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        assertThrows(NullPointerException.class, () -> engine.addListener(null));
        assertThrows(NullPointerException.class, () -> engine.removeListener(null));
    }

    @Test
    void removingAnUnregisteredListenerReturnsFalse() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));
        assertFalse(engine.removeListener(new RecordingListener()));
    }

    // --- 13-14: re-entrant registration during dispatch ---

    @Test
    void aListenerRemovingItselfDuringCallbackCompletesSafelyAndIsAbsentNextTime() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));

        RecordingListener selfRemoving = new RecordingListener() {
            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                super.onSnapshot(snapshot);
                engine.removeListener(this);   // re-entrant modification
            }
        };
        RecordingListener other = new RecordingListener();
        engine.addListener(selfRemoving);
        engine.addListener(other);

        // First dispatch: no ConcurrentModificationException, both notified
        assertDoesNotThrow(engine::step);
        assertEquals(1, selfRemoving.snapshots.size());
        assertEquals(1, other.snapshots.size());

        // Second dispatch: the self-removed listener is gone
        engine.step();
        assertEquals(1, selfRemoving.snapshots.size(), "removal took effect next dispatch");
        assertEquals(2, other.snapshots.size());
    }

    @Test
    void aListenerAddedDuringCallbackReceivesEventsOnlyFromTheNextPublication() {
        SimulationEngine engine = engineWith(shiftingIntegrator(new Vector3D(1, 0, 0)));

        RecordingListener lateArrival = new RecordingListener();
        SimulationListener adder = new SimulationListener() {
            private boolean added = false;

            @Override
            public void onSnapshot(SimulationSnapshot snapshot) {
                if (!added) {
                    engine.addListener(lateArrival);   // re-entrant add
                    added = true;
                }
            }
        };
        engine.addListener(adder);

        engine.step();
        assertEquals(0, lateArrival.snapshots.size(),
                "a listener added mid-dispatch must not be notified during that dispatch");

        engine.step();
        assertEquals(1, lateArrival.snapshots.size(),
                "it receives events from the next publication onward");
    }
}