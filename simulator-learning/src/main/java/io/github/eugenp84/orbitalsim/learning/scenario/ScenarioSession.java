package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.simulation.AdvanceResult;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationEngine;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes a scenario: owns its progression state, drives the simulation at a  bounded learning cadence, and evaluates its completion policy against each
 * committed observation (ADR-008 §1, §3, §4).
 *
 * <h2>Advancement ownership</h2>
 * While a scenario is active, all application-level advancement must be routed through the session, so that no simulated time passes unobserved and corrupts scenario evaluation (ADR-008 §4).
 * The session does not expose its engine reference.
 * It cannot, however, prevent a caller that constructed the engine from retaining its own reference and advancing it directly: this is a <strong>composition-root ownership rule</strong>, not a guarantee this class can enforce.
 * The application must not retain or expose a second advancement path to the same engine.
 *
 * <h2>Learning cadence</h2>
 * Evaluation occurs at a bounded simulated interval, distinct from both the numerical timestep and the rendering frame rate. The interval is specified as
 * an integer number of numerical steps, {@code k}, and derived as  {@code k × dt}; expressing it this way makes the ADR-008 §4 integer-multiple
 * invariant structural rather than a floating-point comparison.
 * Each completed interval corresponds to exactly {@code k} committed numerical steps.
 * <p>
 * A request smaller than one interval, or the sub-interval remainder of a larger request, is retained in a session-level accumulator and consumed by a later
 * request. This mirrors the engine's numerical accumulator one level up and keeps observations uniformly spaced: a 70-second request at a 30-second
 * interval yields observations at 30 and 60, retaining 10, rather than an irregular observation at 70.
 *
 * <h2>Evaluation precision</h2>
 * Because the policy is consulted only at observation boundaries, completion is detected at the first observation <em>at or after</em> the condition becomes
 * true, not at the instant it becomes true. With a 30-second interval and a 65-second horizon, completion is reported at 90 seconds.
 * The observation interval therefore bounds the temporal precision of completion detection, as well as the aliasing risk noted in ADR-008 §4.
 *
 * <h2>Termination</h2>
 * Numerical breakdown and policy failure both terminate the scenario as
 * {@link ScenarioLifecycleState#FAILED}, but are distinguished by
 * {@link #terminationReason()}: a breakdown means the simulation cannot be trusted and no pedagogical conclusion is drawn, whereas a policy failure is a valid outcome of a trustworthy simulation.
 * On breakdown the completion policy is not consulted, because it must not evaluate an untrustworthy result.
 * <p>
 * A completion policy returning null violates its contract; this is a programming defect and propagates as {@link NullPointerException} rather than being classified as a scenario failure.
 *
 * <p>This class is not thread-safe; it is driven from a single thread!!!
 *
 * @author Eugeniu PLATITA
 */
public final class ScenarioSession
{

    /**
     * Tolerance, in units in the last place of the observation interval, when testing whether the accumulator holds a complete interval.
     * <p>
     * Repeated addition and subtraction of second-valued quantities leaves the accumulator fractionally below a mathematically complete interval, which
     * without tolerance strands the interval until a later request — measured, for example, at {@code dt = 0.1 s} with {@code k = 7},
     * where one observation in a thousand was lost.
     * The accumulator is reduced by a whole interval each cycle, so the shortfall is bounded by a few ulps of the interval rather than growing with the number of requests.
     * Sixteen ulps was the smallest multiple correct across 193 tested cadence shapes,
     * including long runs; at a 0.7 s interval that is ~1.8e-15 s, far too small to admit a genuinely incomplete interval.
     */
    private static final int INTERVAL_TOLERANCE_ULPS = 16;

    private final SimulationEngine engine;
    private final ScenarioCompletionPolicy completionPolicy;
    private final double observationIntervalSeconds;
    private final double intervalThresholdSeconds;

    private ScenarioLifecycleState state = ScenarioLifecycleState.NOT_STARTED;
    private ScenarioTerminationReason terminationReason;
    private double pendingSeconds;
    private double baselineSeconds;

    /**
     * @param engine              the simulation to drive; advancement while the
     *                            scenario is active must be routed through this
     *                            session
     * @param completionPolicy    consulted once per committed observation
     * @param stepsPerObservation numerical steps per evaluation ({@code k});
     *                            must be positive and must yield a finite,
     *                            positive observation interval
     * @throws NullPointerException     if a collaborator is null
     * @throws IllegalArgumentException if {@code stepsPerObservation} is not
     *                                  positive, or the derived interval is not
     *                                  positive and finite
     */
    public ScenarioSession(
            SimulationEngine engine,
            ScenarioCompletionPolicy completionPolicy,
            int stepsPerObservation)
    {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.completionPolicy =
                Objects.requireNonNull(completionPolicy, "completionPolicy must not be null");
        if (stepsPerObservation <= 0) {
            throw new IllegalArgumentException(
                    "stepsPerObservation must be positive, was: " + stepsPerObservation);
        }

        // The derived interval is validated, not just its inputs: a large step count multiplied by a large timestep can overflow to infinity, which
        // would make the cadence unsatisfiable and silently suppress every observation.
        double interval = stepsPerObservation * engine.integrationStepSeconds();
        if (!Double.isFinite(interval) || interval <= 0.0) {
            throw new IllegalArgumentException(
                    "derived observation interval must be positive and finite, was: " + interval);
        }
        this.observationIntervalSeconds = interval;

        // IMPORtant: The ulp tolerance is capped at half the interval so the threshold remains strictly positive.
        // For any realistic interval the cap is inactive and the tolerance is exactly 16 ulps.
        // This mirrors the finite-accumulator guard in SimulationEngine.advanceBy (ADR-006 p.4).
        double ulpTolerance = INTERVAL_TOLERANCE_ULPS * Math.ulp(interval);
        double tolerance = Math.min(ulpTolerance, interval / 2.0);
        this.intervalThresholdSeconds = interval - tolerance;
    }

    /** The current progression state. */
    public ScenarioLifecycleState state() {
        return state;
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    public Optional<ScenarioTerminationReason> terminationReason() {
        return Optional.ofNullable(terminationReason);
    }

    /** The simulated time between successive evaluations. */
    public double observationIntervalSeconds() {
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
     *         elapsed scenario time is undefined before a baseline exists.
     *         The value remains meaningful once started, including while paused and after termination.
     */
    public double elapsedScenarioSeconds() {
        if (state == ScenarioLifecycleState.NOT_STARTED) {
            throw new IllegalStateException(
                    "elapsed scenario time is undefined before the scenario starts");
        }
        return engine.snapshot().simulationTimeSeconds() - baselineSeconds;
    }

    /** The current committed simulation state, for rendering and inspection. */
    public SimulationSnapshot snapshot() {
        return engine.snapshot();
    }

    /**
     * Starts the scenario, recording the simulation's current clock as the baseline from which elapsed scenario time is measured. A session may therefore begin from an already-advanced simulation.
     *
     * @throws IllegalStateException if the scenario has already been started
     */
    public void start() {
        requireState(ScenarioLifecycleState.NOT_STARTED, "start");
        baselineSeconds = engine.snapshot().simulationTimeSeconds();
        state = ScenarioLifecycleState.RUNNING;
    }

    /**
     * Suspends advancement. Advancement requests while paused are ignored and are not accumulated, so resuming does not release a burst of simulated time the learner did not observe.
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
     * The request is added to the session accumulator and consumed in whole observation intervals;
     * the completion policy is evaluated after each, and advancement stops as soon as the scenario completes or fails — a large
     * request does not run to its end and discover completion afterwards.
     * On termination the unconsumed remainder is discarded, mirroring the engine's treatment of carried time after breakdown (ADR-006 §3).
     * <p>
     * Requests made while the scenario is not running are ignored.
     *
     * @param simulatedSeconds requested simulated time; non-negative and finite
     * @throws IllegalArgumentException if the request is negative, NaN or infinite, or would make the accumulated pending time non-finite
     * @throws NullPointerException     if the completion policy returns null
     */
    public void advanceBy(double simulatedSeconds)
    {
        if (simulatedSeconds < 0.0 || !Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException(
                    "simulatedSeconds must be non-negative and finite, was: " + simulatedSeconds);
        }
        if (state != ScenarioLifecycleState.RUNNING) {
            return;
        }

        // Two finite values can sum to infinity; the accumulated result ischecked before it is committed, so a rejected request leaves the existing pending time intact.
        double accumulated = pendingSeconds + simulatedSeconds;
        if (!Double.isFinite(accumulated)) {
            throw new IllegalArgumentException(
                    "accumulated pending time must remain finite: "
                            + pendingSeconds + " + " + simulatedSeconds);
        }
        pendingSeconds = accumulated;

        while (state == ScenarioLifecycleState.RUNNING
                && pendingSeconds >= intervalThresholdSeconds) {

            AdvanceResult result = engine.advanceBy(observationIntervalSeconds);

            pendingSeconds -= observationIntervalSeconds;
            if (pendingSeconds < 0.0) {
                // The tolerance admitted an interval fractionally short of a full one.
                // the shortfall is absorbed rather than carried as negative balance.
                pendingSeconds = 0.0;
            }

            if (result.brokeDown()) {
                // The policy is not consulted: it must not draw a conclusion from an untrustworthy result.
                terminate(ScenarioLifecycleState.FAILED,
                        ScenarioTerminationReason.SIMULATION_BREAKDOWN);
                return;
            }

            ScenarioObservation observation = new ScenarioObservation(
                    result.snapshot(),
                    result.snapshot().simulationTimeSeconds() - baselineSeconds);

            // A null evaluation is a policy contract violation — a programming defect.
            // It is surfaced as such rather than being disguised as an scenario outcome.
            ScenarioEvaluation evaluation = Objects.requireNonNull(
                    completionPolicy.evaluate(observation),
                    "completionPolicy returned null");

            switch (evaluation)
            {
                case COMPLETE -> terminate(
                        ScenarioLifecycleState.COMPLETED,
                        ScenarioTerminationReason.POLICY_COMPLETION);

                case FAIL -> terminate(
                        ScenarioLifecycleState.FAILED,
                        ScenarioTerminationReason.POLICY_FAILURE);

                case ASSESSMENT_INVALID -> terminate(
                        ScenarioLifecycleState.FAILED,
                        ScenarioTerminationReason.ASSESSMENT_INVALID);

                case CONTINUE -> {
                    // Keep advancing
                }
            } // end switch ---------
        }
    } // --- end advanceBy() -------------=============================================

    private void terminate(ScenarioLifecycleState terminalState, ScenarioTerminationReason reason)
    {
        state = terminalState;
        terminationReason = reason;
        pendingSeconds = 0.0;
    }

    private void requireState(ScenarioLifecycleState required, String operation) {
        if (state != required) {
            throw new IllegalStateException(
                    "cannot " + operation + " while " + state + "; requires " + required);
        }
    }
}