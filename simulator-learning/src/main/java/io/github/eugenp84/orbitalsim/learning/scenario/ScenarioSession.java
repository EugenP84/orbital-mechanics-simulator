package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.simulation.AdvanceResult;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationEngine;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Executes a scenario: owns its progression state, drives the simulation at a bounded learning cadence, and evaluates its completion policy against each
 * committed observation (ADR-008).
 *
 * <h2>Advancement ownership</h2>
 * While a scenario is active, all application-level advancement must be routed through the session, so that no simulated time passes unobserved and corrupts scenario evaluation (ADR-008 §4).
 * The session does not expose its engine reference.
 * It cannot, however, prevent a caller that constructed the engine from retaining its own reference and advancing it directly: this is a <strong>composition-root ownership rule</strong>, not a guarantee this class can enforce.
 *
 * <h2>Learning cadence</h2>
 * Evaluation occurs at a bounded simulated interval, distinct from both the numerical timestep and the rendering frame rate.
 * The cadence arrives as a {@link ScenarioObservationCadence} rather than a bare step count, because a completion policy may need the same cadence to validate its own assumptions;
 * sharing one object removes any route for the two to disagree.
 * The session verifies the cadence describes the engine it drives.
 * <p>
 * A request smaller than one interval, or the sub-interval remainder of a larger request, is retained in a session-level accumulator and consumed by a later request, keeping observations uniformly spaced:
 * a 70-second request at a 30-second interval yields observations at 30 and 60, retaining 10.
 *
 * <h2>Evaluation precision</h2>
 * Because the policy is consulted only at observation boundaries, completion is detected at the first observation at or after the condition becomes true.
 * With a 30-second interval and a 65-second horizon, completion is reported at 90 sec.
 *
 * <h2>Termination</h2>
 * A scenario may terminate for several distinct reasons.
 * A policy may report success ({@link ScenarioLifecycleState#COMPLETED}), learner-facing failure
 * ({@link ScenarioTerminationReason#POLICY_FAILURE}), or that the attempt can no longer be assessed reliably
 * ({@link ScenarioTerminationReason#ASSESSMENT_INVALID}) — from which no learner-facing conclusion should be drawn.
 * Independently, numerical breakdown reported by the engine terminates the scenario with {@link ScenarioTerminationReason#SIMULATION_BREAKDOWN};
 * the policy is not consulted, because the committed result cannot be trusted.
 * <p>
 * A completion policy returning null violates its contract; this is a programming defect and propagates as {@link NullPointerException} .
 * Not classified as a scenario outcome.
 *
 * <p>This class is not thread-safe, driven from a single thread.
 *
 * @author Eugeniu PLATITA
 */
public final class ScenarioSession
{

    /**
     * Tolerance, in units in the last place of the observation interval, when testing whether the accumulator holds a complete interval.
     * <p>
     * Repeated addition and subtraction of second-valued quantities leaves the accumulator fractionally below a mathematically complete interval, which
     * without tolerance strands the interval until a later request — measured, for example, at {@code dt = 0.1 s} with {@code k = 7}, where one observation in a thousand was lost.
     * The accumulator is reduced by a whole interval each cycle, so the shortfall is bounded by a few ulps of the interval rather than growing with the number of requests.
     * Sixteen ulps was the smallest multiple correct across 193 tested cadence shapes.
     */
    private static final int INTERVAL_TOLERANCE_ULPS = 16;

    private final SimulationEngine engine;
    private final ScenarioCompletionPolicy completionPolicy;
    private final ScenarioObservationCadence cadence;
    private final double observationIntervalSeconds;
    private final double intervalThresholdSeconds;

    private ScenarioLifecycleState state = ScenarioLifecycleState.NOT_STARTED;

    private ScenarioTerminationReason terminationReason;
    private double pendingSeconds;
    private double baselineSeconds;

    /**
     * @param engine           the simulation to drive; advancement while the scenario is active must be routed through this session
     * @param completionPolicy consulted once per committed observation
     * @param cadence          the observation cadence, which must describe this engine's timestep
     * @throws NullPointerException     if a collaborator is null
     * @throws IllegalArgumentException if the cadence's timestep differs from the engine's timestep
     */
    public ScenarioSession(
            SimulationEngine engine,
            ScenarioCompletionPolicy completionPolicy,
            ScenarioObservationCadence cadence)
    {

        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.completionPolicy =
                Objects.requireNonNull(completionPolicy, "completionPolicy must not be null");
        this.cadence = Objects.requireNonNull(cadence, "cadence must not be null");

        // Exact comparison, not a tolerance: these are two references to the same configured value, so "nearly equal" would mean one is wrong.
        if (Double.compare(cadence.timeStepSeconds(), engine.integrationStepSeconds()) != 0)
        {
            throw new IllegalArgumentException("cadence timestep (" + cadence.timeStepSeconds()
                            + ") must match the engine timestep ("
                            + engine.integrationStepSeconds() + ")");
        }

        this.observationIntervalSeconds = cadence.intervalSeconds(); //positive and finite by the cadence's own construction invariant.

        // The ulp tolerance is capped at half the interval so the thresholdstays strictly positive;
        // for a subnormal interval 16 ulps can exceed the interval itself, which would make the advancement loop non-terminating.
        double ulpTolerance = INTERVAL_TOLERANCE_ULPS * Math.ulp(observationIntervalSeconds);
        double tolerance = Math.min(ulpTolerance, observationIntervalSeconds / 2.0);
        this.intervalThresholdSeconds = observationIntervalSeconds - tolerance;
    }

    /** The current progression state. */
    public ScenarioLifecycleState state() {
        return state;
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    public Optional<ScenarioTerminationReason> terminationReason() {
        return Optional.ofNullable(terminationReason);
    }

    /** Cadence at which observations are produced */
    public ScenarioObservationCadence cadence() {
        return cadence;
    }

    /** The simulated time between successive evaluations.   */
    public double observationIntervalSeconds()
    {
        return observationIntervalSeconds;
    }

    /** Simulated time received but not yet consumed by a complete interval. */
    public double pendingSeconds() {
        return pendingSeconds;
    }

    /**
     * Simulated time elapsed since the scenario started.
     *
     * @throws IllegalStateException if the scenario has not been started;
     *         elapsed scenario time is undefined before a baseline exists
     */
    public double elapsedScenarioSeconds() {
        if (state == ScenarioLifecycleState.NOT_STARTED)
        {
            throw new IllegalStateException("elapsed scenario time is undefined before the scenario starts");
        }
        return engine.snapshot().simulationTimeSeconds() - baselineSeconds;
    }

    /** The current committed simulation state, for rendering and inspection   */
    public SimulationSnapshot snapshot() {
        return engine.snapshot();
    }

    /**
     * Starts the scenario, recording the simulation's current clock as the baseline from which elapsed scenario time is measured, so a session may begin from an already-advanced simulation.
     *
     * @throws IllegalStateException if the scenario has already been started
     */
    public void start()
    {
        requireState(ScenarioLifecycleState.NOT_STARTED, "start");
        baselineSeconds = engine.snapshot().simulationTimeSeconds();
        state = ScenarioLifecycleState.RUNNING;
    }

    /**
     * Suspends advancement. Requests while paused are ignored and not accumulated, so resuming does not release a burst of simulated time the learner did not observe.
     *
     * @throws IllegalStateException if the scenario is not running
     */
    public void pause() {
        requireState(ScenarioLifecycleState.RUNNING, "pause");
        state = ScenarioLifecycleState.PAUSED;
    }

    /**
     * Resumes advancement.
     *
     * @throws IllegalStateException if the scenario is not paused
     */
    public void resume() {
        requireState(ScenarioLifecycleState.PAUSED, "resume");
        state = ScenarioLifecycleState.RUNNING;
    }

    /**
     * Requests that the scenario advance by the given amount of simulated time.
     * <p>
     * The request is consumed in whole observation intervals, the policy is evaluated after each, and advancement stops as soon as the scenario terminates (!!! a large request does not run to its end and discover completion afterwards).
     * On termination the unconsumed remainder is discarded.
     * Requests made while not running are ignored!
     * <p>
     * A huge but finite request is not policed: it executes a correspondingly large number of observations.
     * The finiteness check below cannot substitute for caller restraint, because adding a small pending value to a very large request absorbs rather than overflowing.
     *
     * @param simulatedSeconds requested simulated time; non-negative and finite
     * @throws IllegalArgumentException if the request is negative, NaN or infinite, or would make the accumulated pending time non-finite
     * @throws NullPointerException     if the completion policy returns null
     */
    public void advanceBy(double simulatedSeconds)
    {
        if (simulatedSeconds < 0.0 || !Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException("simulatedSeconds must be non-negative and finite, Was: " + simulatedSeconds);
        }
        if (state != ScenarioLifecycleState.RUNNING) {
            return;
        }

        // Checked before assignment, so a rejected request leaves existing carried time intact.
        double accumulated = pendingSeconds + simulatedSeconds;
        if (!Double.isFinite(accumulated))
        {
            throw new IllegalArgumentException("accumulated pending time must remain finite: " + pendingSeconds + " + " + simulatedSeconds);
        }
        pendingSeconds = accumulated;

        while (state == ScenarioLifecycleState.RUNNING && pendingSeconds >= intervalThresholdSeconds)
        {

            AdvanceResult result = engine.advanceBy(observationIntervalSeconds);

            pendingSeconds -= observationIntervalSeconds;
            if (pendingSeconds < 0.0)
            {
                // The tolerance admitted an interval fractionally short of a full one; the shortfall is absorbed, not carried negative.
                pendingSeconds = 0.0;
            }

            if (result.brokeDown())
            {
                // The policy is not consulted: it must not draw a pedagogical conclusion from an untrustworthy result!----------
                terminate(ScenarioLifecycleState.FAILED,
                        ScenarioTerminationReason.SIMULATION_BREAKDOWN);
                return;
            }

            ScenarioObservation observation = new ScenarioObservation(result.snapshot(),
                    result.snapshot().simulationTimeSeconds() - baselineSeconds);

            // A null evaluation is a contract violation, surfaced as the programming defect it is rather than disguised as an outcome.
            ScenarioEvaluation evaluation = Objects.requireNonNull(completionPolicy.evaluate(observation),"completionPolicy returned null");

            switch (evaluation)
            {
                case COMPLETE -> terminate(ScenarioLifecycleState.COMPLETED,
                        ScenarioTerminationReason.POLICY_COMPLETION);
                case FAIL -> terminate(ScenarioLifecycleState.FAILED,
                        ScenarioTerminationReason.POLICY_FAILURE);
                case ASSESSMENT_INVALID -> terminate(ScenarioLifecycleState.FAILED,
                        ScenarioTerminationReason.ASSESSMENT_INVALID);
                case CONTINUE -> { /* keep advancing */ }
            }//end switch--------------=========================================
        }// end while loop-----------------=======================
    } // ------------------- end of advanceBy() ----------------------------------------------------------

    private void terminate(ScenarioLifecycleState terminalState, ScenarioTerminationReason reason)
    {
        state = terminalState;
        terminationReason = reason;
        pendingSeconds = 0.0;
    }

    private void requireState(ScenarioLifecycleState required, String operation)
    {
        if (state != required) {
            throw new IllegalStateException("cannot " + operation + " while " + state + "; requires " + required);
        }
    }
}