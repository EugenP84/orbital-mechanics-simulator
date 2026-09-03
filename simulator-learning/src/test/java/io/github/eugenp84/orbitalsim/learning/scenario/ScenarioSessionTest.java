package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioSessionTest {

    private static final double DT = 10.0;
    private static final int K = 3;                 // observation interval = 30 s
    private static final double INTERVAL = K * DT;

    // --- Fixtures ---

    private static List<BodySnapshot> oneBody() {
        return List.of(new BodySnapshot(
                new CelestialBody("star-1", "Star", CelestialBodyType.STAR,
                        PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS),
                new StateVector(Vector3D.ZERO, Vector3D.ZERO)));
    }

    private static AccelerationModel noAcceleration() {
        return states -> {
            Vector3D[] a = new Vector3D[states.length];
            Arrays.fill(a, Vector3D.ZERO);
            return a;
        };
    }

    /**
     * Identity integrator: simulated time advances, state does not move.
     * Cadence behaviour is the subject of these tests, not motion.
     */
    private static Integrator identityIntegrator() {
        return (states, dt, model) -> states.clone();
    }

    private static SimulationEngine engineWithTimestep(double dt) {
        return new SimulationEngine(oneBody(), noAcceleration(), dt, identityIntegrator());
    }

    private static SimulationEngine engine() {
        return engineWithTimestep(DT);
    }

    /** An engine whose integrator produces a non-finite state after N steps. */
    private static SimulationEngine engineBreakingAfter(int successfulSteps) {
        Integrator breaking = new Integrator() {
            private int calls = 0;

            @Override
            public StateVector[] step(StateVector[] states, double dt, AccelerationModel model) {
                if (++calls > successfulSteps) {
                    return new StateVector[]{
                            new StateVector(new Vector3D(Double.NaN, 0, 0), Vector3D.ZERO)};
                }
                return states.clone();
            }
        };
        return new SimulationEngine(oneBody(), noAcceleration(), DT, breaking);
    }

    /** Records every observation the session presents, and can complete on demand. */
    private static final class RecordingPolicy implements ScenarioCompletionPolicy {
        final List<Double> observedTimes = new ArrayList<>();
        final List<Long> observedSteps = new ArrayList<>();
        private final double completeAtOrAfterElapsedSeconds;   // NaN = never complete

        private RecordingPolicy(double completeAtOrAfterElapsedSeconds) {
            this.completeAtOrAfterElapsedSeconds = completeAtOrAfterElapsedSeconds;
        }

        static RecordingPolicy neverCompleting() {
            return new RecordingPolicy(Double.NaN);
        }

        static RecordingPolicy completingAt(double elapsedSeconds) {
            return new RecordingPolicy(elapsedSeconds);
        }

        @Override
        public ScenarioEvaluation evaluate(ScenarioObservation observation) {
            observedTimes.add(observation.snapshot().simulationTimeSeconds());
            observedSteps.add(observation.snapshot().completedSteps());
            if (!Double.isNaN(completeAtOrAfterElapsedSeconds)
                    && observation.elapsedScenarioSeconds() >= completeAtOrAfterElapsedSeconds) {
                return ScenarioEvaluation.COMPLETE;
            }
            return ScenarioEvaluation.CONTINUE;
        }
    }

    private static ScenarioSession started(ScenarioCompletionPolicy policy) {
        ScenarioSession session = new ScenarioSession(engine(), policy, K);
        session.start();
        return session;
    }

    // --- Construction and lifecycle ---

    @Test
    void constructorValidatesCollaboratorsAndInterval() {
        SimulationEngine e = engine();
        ScenarioCompletionPolicy p = RecordingPolicy.neverCompleting();

        assertThrows(NullPointerException.class, () -> new ScenarioSession(null, p, K));
        assertThrows(NullPointerException.class, () -> new ScenarioSession(e, null, K));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSession(e, p, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScenarioSession(e, p, -1));
    }

    @Test
    void constructorRejectsAnOverflowingObservationInterval() {
        SimulationEngine huge = engineWithTimestep(1.0e308);
        assertThrows(IllegalArgumentException.class,
                () -> new ScenarioSession(huge, RecordingPolicy.neverCompleting(), 1000));
    }

    @Test
    void observationIntervalIsAnExactIntegerMultipleOfTheTimestep() {
        ScenarioSession session =
                new ScenarioSession(engine(), RecordingPolicy.neverCompleting(), K);
        assertEquals(INTERVAL, session.observationIntervalSeconds(), 1e-12);
    }

    @Test
    void aNewSessionIsNotStarted() {
        ScenarioSession session =
                new ScenarioSession(engine(), RecordingPolicy.neverCompleting(), K);
        assertEquals(ScenarioLifecycleState.NOT_STARTED, session.state());
    }

    @Test
    void invalidLifecycleTransitionsAreRejected() {
        ScenarioSession session =
                new ScenarioSession(engine(), RecordingPolicy.neverCompleting(), K);

        assertThrows(IllegalStateException.class, session::pause);
        assertThrows(IllegalStateException.class, session::resume);

        session.start();
        assertEquals(ScenarioLifecycleState.RUNNING, session.state());
        assertThrows(IllegalStateException.class, session::start);
        assertThrows(IllegalStateException.class, session::resume);

        session.pause();
        assertEquals(ScenarioLifecycleState.PAUSED, session.state());
        assertThrows(IllegalStateException.class, session::pause);
    }

    // --- Cadence ---

    @Test
    void aRequestBelowOneIntervalIsRetainedAndProducesNoObservation() {
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = started(policy);

        session.advanceBy(INTERVAL - DT);   // 20 s of a 30 s interval

        assertTrue(policy.observedTimes.isEmpty(), "no complete interval elapsed");
        assertEquals(INTERVAL - DT, session.pendingSeconds(), 1e-12);
        assertEquals(0.0, session.snapshot().simulationTimeSeconds(), 1e-12);
    }

    @Test
    void observationsAreUniformlySpacedByTheLearningInterval() {
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = started(policy);

        session.advanceBy(3 * INTERVAL);    // 90 s

        assertEquals(List.of(30.0, 60.0, 90.0), policy.observedTimes);
        assertEquals(0.0, session.pendingSeconds(), 1e-12);
    }

    @Test
    void eachObservationRepresentsExactlyKCommittedSteps() {
        // completedSteps is part of SimulationSnapshot's published contract
        // (ADR-006 §6), so this asserts on the engine's contract, not internals
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = started(policy);

        session.advanceBy(3 * INTERVAL);

        assertEquals(List.of(3L, 6L, 9L), policy.observedSteps);
    }

    @Test
    void aSubIntervalRemainderIsRetainedAndConsumedByALaterRequest() {
        // 70 s at a 30 s interval: observations at 30 and 60, retaining 10 —
        // not an irregular observation at 70
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = started(policy);

        session.advanceBy(70.0);
        assertEquals(List.of(30.0, 60.0), policy.observedTimes);
        assertEquals(10.0, session.pendingSeconds(), 1e-12);

        session.advanceBy(20.0);            // 10 retained + 20 = one more interval
        assertEquals(List.of(30.0, 60.0, 90.0), policy.observedTimes);
        assertEquals(0.0, session.pendingSeconds(), 1e-12);
    }

    @Test
    void cadenceIsIndependentOfRequestSize() {
        RecordingPolicy small = RecordingPolicy.neverCompleting();
        ScenarioSession a = started(small);
        for (int i = 0; i < 9; i++) {
            a.advanceBy(DT);                // 9 x 10 s
        }

        RecordingPolicy large = RecordingPolicy.neverCompleting();
        ScenarioSession b = started(large);
        b.advanceBy(90.0);

        assertEquals(large.observedTimes, small.observedTimes);
        assertEquals(List.of(30.0, 60.0, 90.0), small.observedTimes);
    }

    @Test
    void cadenceSurvivesRepeatedNonBinaryExactAccumulation() {
        // dt = 0.1 with k = 7: repeated add/subtract of non-representable
        // values strands a complete interval without the comparison tolerance
        double dt = 0.1;
        int k = 7;
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = new ScenarioSession(engineWithTimestep(dt), policy, k);
        session.start();

        for (int i = 0; i < 7000; i++) {
            session.advanceBy(dt);
        }

        // Count: no observation stranded by accumulated rounding
        assertEquals(1000, policy.observedSteps.size());

        // Integrity: every observation falls on an exact k-step boundary, so no
        // observation boundary was misplaced
        for (int i = 0; i < policy.observedSteps.size(); i++) {
            assertEquals((long) (i + 1) * k, policy.observedSteps.get(i),
                    "observation " + i + " must fall on an exact k-step boundary");
        }
        assertEquals(7000L, policy.observedSteps.get(policy.observedSteps.size() - 1));

        // Simulated times compared with tolerance rather than exact equality
        for (int i = 0; i < policy.observedTimes.size(); i++) {
            assertEquals((i + 1) * k * dt, policy.observedTimes.get(i), 1e-9);
        }
    }

    @Test
    void anOverflowingAccumulationIsRejectedWithoutDisturbingPendingTime()
    {
        // Overflow requires both operands to be large. After any completed advance the loop drains pending below the interval, so pending can
        // only become large when the interval itself is large — the same
        // reachability condition as the engine's accumulator guard
        // (ADR-006 §4). Note that a small pending plus Double.MAX_VALUE does
        // NOT overflow: it absorbs back to MAX_VALUE.
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session =
                new ScenarioSession(engineWithTimestep(1.0e308), policy, 1);
        session.start();

        session.advanceBy(9.0e307);     // below the 1e308 interval: retained
        assertTrue(policy.observedTimes.isEmpty(), "no complete interval elapsed");
        assertEquals(9.0e307, session.pendingSeconds());

        assertThrows(IllegalArgumentException.class,
                () -> session.advanceBy(Double.MAX_VALUE));

        assertEquals(9.0e307, session.pendingSeconds(),
                "a rejected request must leave pending time intact");
    }

    @Test
    void aSubnormalObservationIntervalStillYieldsAPositiveThreshold() {
        // 16 ulps exceeds a subnormal interval; uncapped, the threshold would
        // go negative and the advancement loop would not terminate. A timeout
        // is the correct instrument here because the failure mode is a hang.
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session =
                new ScenarioSession(engineWithTimestep(Double.MIN_VALUE), policy, 1);
        session.start();

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> session.advanceBy(0.0));
        assertTrue(policy.observedTimes.isEmpty(),
                "a zero request must not trigger an observation");
    }

    // --- Completion ---

    @Test
    void advancementStopsAtCompletionRatherThanRunningOutTheRequest() {
        // The central reason chunking exists: a 300 s request with a 60 s
        // horizon must stop at 60, not execute ten intervals and notice after
        RecordingPolicy policy = RecordingPolicy.completingAt(60.0);
        ScenarioSession session = started(policy);

        session.advanceBy(300.0);

        assertEquals(ScenarioLifecycleState.COMPLETED, session.state());
        assertEquals(List.of(30.0, 60.0), policy.observedTimes);
        assertEquals(60.0, session.snapshot().simulationTimeSeconds(), 1e-12);
    }

    @Test
    void unconsumedTimeIsDiscardedOnCompletion() {
        RecordingPolicy policy = RecordingPolicy.completingAt(60.0);
        ScenarioSession session = started(policy);

        session.advanceBy(300.0);

        assertEquals(0.0, session.pendingSeconds(), 1e-12);
    }

    @Test
    void noAdvancementOccursAfterCompletion() {
        RecordingPolicy policy = RecordingPolicy.completingAt(60.0);
        ScenarioSession session = started(policy);
        session.advanceBy(300.0);

        double timeAtCompletion = session.snapshot().simulationTimeSeconds();
        int observationsAtCompletion = policy.observedTimes.size();

        session.advanceBy(300.0);

        assertEquals(timeAtCompletion, session.snapshot().simulationTimeSeconds(), 1e-12);
        assertEquals(observationsAtCompletion, policy.observedTimes.size());
    }

    @Test
    void completionIsDetectedAtTheFirstObservationAtOrAfterTheCondition() {
        // Evaluation is bounded by the observation interval, so a 65 s horizon
        // is detected at the 90 s observation (ADR-008 §4)
        RecordingPolicy policy = RecordingPolicy.completingAt(65.0);
        ScenarioSession session = started(policy);

        session.advanceBy(300.0);

        assertEquals(List.of(30.0, 60.0, 90.0), policy.observedTimes);
        assertEquals(90.0, session.snapshot().simulationTimeSeconds(), 1e-12);
    }

    @Test
    void aFailingPolicyTerminatesTheScenarioAsFailed() {
        ScenarioSession session = started(observation -> ScenarioEvaluation.FAIL);

        session.advanceBy(300.0);

        assertEquals(ScenarioLifecycleState.FAILED, session.state());
        assertEquals(INTERVAL, session.snapshot().simulationTimeSeconds(), 1e-12);
    }

    @Test
    void policyFailureAndCompletionCarryDistinctTerminationReasons() {
        ScenarioSession failing = started(observation -> ScenarioEvaluation.FAIL);
        failing.advanceBy(INTERVAL);
        assertEquals(ScenarioLifecycleState.FAILED, failing.state());
        assertEquals(Optional.of(ScenarioTerminationReason.POLICY_FAILURE),
                failing.terminationReason());

        ScenarioSession completing = started(RecordingPolicy.completingAt(30.0));
        completing.advanceBy(INTERVAL);
        assertEquals(ScenarioLifecycleState.COMPLETED, completing.state());
        assertEquals(Optional.of(ScenarioTerminationReason.POLICY_COMPLETION),
                completing.terminationReason());
    }

    @Test
    void aRunningScenarioHasNoTerminationReason() {
        ScenarioSession session = started(RecordingPolicy.neverCompleting());
        assertTrue(session.terminationReason().isEmpty());
    }

    @Test
    void aNullEvaluationIsSurfacedAsAProgrammingDefect() {
        ScenarioSession session = started(observation -> null);
        assertThrows(NullPointerException.class, () -> session.advanceBy(INTERVAL));
    }

    // --- Engine breakdown (technical failure, not pedagogical) ---

    @Test
    void engineBreakdownFailsTheScenarioWithoutConsultingThePolicy() {
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session =
                new ScenarioSession(engineBreakingAfter(2 * K), policy, K);
        session.start();

        session.advanceBy(10 * INTERVAL);

        assertEquals(ScenarioLifecycleState.FAILED, session.state());
        assertEquals(Optional.of(ScenarioTerminationReason.SIMULATION_BREAKDOWN),
                session.terminationReason());
        assertEquals(List.of(30.0, 60.0), policy.observedTimes,
                "the policy must not evaluate an untrustworthy observation");
        assertEquals(0.0, session.pendingSeconds(), 1e-12);
    }

    @Test
    void noAdvancementOccursAfterBreakdown() {
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session =
                new ScenarioSession(engineBreakingAfter(2 * K), policy, K);
        session.start();
        session.advanceBy(10 * INTERVAL);

        double timeAtFailure = session.snapshot().simulationTimeSeconds();
        session.advanceBy(10 * INTERVAL);

        assertEquals(timeAtFailure, session.snapshot().simulationTimeSeconds(), 1e-12);
        assertEquals(2, policy.observedTimes.size());
    }

    // --- Elapsed scenario time ---

    @Test
    void horizonIsMeasuredFromScenarioStartNotEngineConstruction() {
        SimulationEngine e = engine();
        e.advanceBy(5 * INTERVAL);          // 150 s before the scenario starts

        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = new ScenarioSession(e, policy, K);
        session.start();

        assertEquals(0.0, session.elapsedScenarioSeconds(), 1e-12);

        session.advanceBy(2 * INTERVAL);
        assertEquals(2 * INTERVAL, session.elapsedScenarioSeconds(), 1e-9);
        assertEquals(7 * INTERVAL, session.snapshot().simulationTimeSeconds(), 1e-9,
                "the engine clock keeps running from where it was");
    }

    @Test
    void elapsedScenarioTimeIsUndefinedBeforeStart() {
        SimulationEngine e = engine();
        e.advanceBy(5 * INTERVAL);          // engine clock already at 150 s

        ScenarioSession session =
                new ScenarioSession(e, RecordingPolicy.neverCompleting(), K);

        assertThrows(IllegalStateException.class, session::elapsedScenarioSeconds,
                "an unstarted scenario has no baseline, so elapsed time is undefined");

        session.start();
        assertEquals(0.0, session.elapsedScenarioSeconds(), 1e-12);
    }

    @Test
    void elapsedScenarioTimeRemainsAvailableWhilePausedAndAfterTermination() {
        ScenarioSession paused = started(RecordingPolicy.neverCompleting());
        paused.advanceBy(INTERVAL);
        paused.pause();
        assertEquals(INTERVAL, paused.elapsedScenarioSeconds(), 1e-9);

        ScenarioSession completed = started(RecordingPolicy.completingAt(INTERVAL));
        completed.advanceBy(INTERVAL);
        assertEquals(ScenarioLifecycleState.COMPLETED, completed.state());
        assertEquals(INTERVAL, completed.elapsedScenarioSeconds(), 1e-9);
    }

    // --- Pause ---

    @Test
    void pausePreventsAdvancementAndDoesNotAccumulate() {
        RecordingPolicy policy = RecordingPolicy.neverCompleting();
        ScenarioSession session = started(policy);
        session.advanceBy(INTERVAL);
        session.pause();

        session.advanceBy(300.0);           // ignored, and not banked

        assertEquals(List.of(30.0), policy.observedTimes);
        assertEquals(0.0, session.pendingSeconds(), 1e-12);

        session.resume();
        session.advanceBy(INTERVAL);
        assertEquals(List.of(30.0, 60.0), policy.observedTimes,
                "resuming must not release simulated time banked while paused");
    }

    // --- Input validation ---

    @Test
    void advanceRejectsNegativeAndNonFiniteRequests() {
        ScenarioSession session = started(RecordingPolicy.neverCompleting());

        assertThrows(IllegalArgumentException.class, () -> session.advanceBy(-1.0));
        assertThrows(IllegalArgumentException.class, () -> session.advanceBy(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> session.advanceBy(Double.POSITIVE_INFINITY));
    }
}