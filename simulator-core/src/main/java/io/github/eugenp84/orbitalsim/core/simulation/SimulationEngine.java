package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.model.SimulationBody;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Owner of committed simulation state (ADR-006).
 * <p>
 * The engine holds the current state of every body, a fixed integration
 * timestep, an acceleration model, and a selected integration strategy. It
 * exposes deterministic operations only: it owns no loop, no threads and no
 * notion of wall-clock time (ADR-006 §2). It is not thread-safe; callers
 * drive it from a single thread.
 * <p>
 * <strong>Bodies</strong> (ADR-006 §4). Committed state lives in
 * engine-private {@link SimulationBody} aggregates, constructed from the
 * paired {@link BodySnapshot} list supplied at construction. No runtime
 * object crosses the engine boundary: results and events carry immutable
 * snapshots only. Unlike the mathematical integration and energy primitives,
 * which define empty systems (ADR-005 §10), the engine requires at least one
 * configured body: an empty mathematical system is well-defined, but an
 * engine instance represents an executable scenario, all of which contain
 * bodies.
 * <p>
 * <strong>Commit discipline</strong> (ADR-006 §5). Each step extracts the
 * committed snapshot, invokes the integrator, verifies the structural return
 * contract, and inspects every component of the candidate for finiteness —
 * the inspection ADR-005 §9 assigns to the caller. Only a fully finite
 * candidate is committed; commit updates all body states and the step
 * counter together, so state and clock move atomically or not at all. A
 * rejected candidate leaves the engine exactly as it was.
 * <p>
 * The commit loop is atomic because it contains no failure point: candidate
 * structure and finiteness are fully validated before it begins, and
 * {@link SimulationBody#updateState} is required to accept any non-null
 * {@code StateVector} from the validated commit path without further
 * validation or side effects. Adding policy checks to {@code updateState}
 * would silently break this atomicity argument.
 * <p>
 * <strong>Failure taxonomy.</strong> An integrator that violates its
 * structural return contract — null array, wrong length, null element — is a
 * defective collaborator and raises {@link IllegalStateException}, mirroring
 * the treatment of {@code AccelerationModel} violations inside the
 * integrators (ADR-005 §4). A structurally valid candidate containing a
 * non-finite component is an observable numerical outcome and yields
 * {@link StepResult.NumericalBreakdown} (ADR-005 §9). The two are never
 * conflated.
 * <p>
 * <strong>Clock</strong> (ADR-006 §5). The authoritative clock is the exact
 * integer {@code completedSteps}; elapsed simulated time is derived from it
 * by a single multiplication. Simulation time is elapsed seconds from the
 * initial state, never an astronomical epoch.
 * <p>
 * <strong>Integrator strategy</strong> (ADR-006 §10). The integrator is the
 * one deliberately mutable piece of configuration, replaceable between
 * completed integration operations via {@link #selectIntegrator}; changing it
 * alters nothing else.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationEngine {

    private final List<SimulationBody> bodies;
    private final AccelerationModel accelerationModel;
    private final double integrationStepSeconds;

    private Integrator integrator;
    private long completedSteps;
    private double accumulatedSeconds;

    private final Set<SimulationListener> listeners = new LinkedHashSet<>();
    private final ListenerErrorHandler listenerErrorHandler;

    /**
     * Constructs an engine with a default listener-error handler that reports
     * to {@code System.err}. Equivalent to the five-argument constructor with
     * a default handler; existing callers need not supply one.
     */
    public SimulationEngine(
            List<BodySnapshot> initialBodies,
            AccelerationModel accelerationModel,
            double integrationStepSeconds,
            Integrator integrator) {
        this(initialBodies, accelerationModel, integrationStepSeconds, integrator,
                defaultListenerErrorHandler());
    }

    /**
     * @param initialBodies          definition–state pairs for every body, in
     *                               the body order all snapshots will use;
     *                               must contain at least one body
     * @param accelerationModel      the physical model, fixed for the
     *                               engine's lifetime
     * @param integrationStepSeconds the fixed numerical step size; positive
     *                               and finite (ADR-005 §7)
     * @param integrator             the initially selected strategy
     * @param listenerErrorHandler   receives isolated listener failures
     *                               (ADR-006 §9)
     * @throws NullPointerException     if any argument or list element is null
     * @throws IllegalArgumentException if the body list is empty or the
     *                                  timestep is not positive and finite
     */
    public SimulationEngine(
            List<BodySnapshot> initialBodies,
            AccelerationModel accelerationModel,
            double integrationStepSeconds,
            Integrator integrator,
            ListenerErrorHandler listenerErrorHandler)
    {

        Objects.requireNonNull(initialBodies, "initialBodies must not be null");
        this.accelerationModel =
                Objects.requireNonNull(accelerationModel, "accelerationModel must not be null");
        this.integrator = Objects.requireNonNull(integrator, "integrator must not be null");
        this.listenerErrorHandler =
                Objects.requireNonNull(listenerErrorHandler, "listenerErrorHandler must not be null");

        if (initialBodies.isEmpty()) {
            throw new IllegalArgumentException("at least one body is required");
        }
        if (integrationStepSeconds <= 0.0 || !Double.isFinite(integrationStepSeconds)) {
            throw new IllegalArgumentException(
                    "integrationStepSeconds must be positive and finite, was: "
                            + integrationStepSeconds);
        }
        this.integrationStepSeconds = integrationStepSeconds;

        List<BodySnapshot> pairs = List.copyOf(initialBodies);
        this.bodies = new ArrayList<>(pairs.size());
        for (BodySnapshot pair : pairs) {
            this.bodies.add(new SimulationBody(pair.definition(), pair.state()));
        }

        this.completedSteps = 0L;
        this.accumulatedSeconds = 0.0;
    }

    private static ListenerErrorHandler defaultListenerErrorHandler() {
        return (listener, failure) -> {
            System.err.println("SimulationListener threw: " + failure);
            failure.printStackTrace();
        };
    }


    /** The fixed numerical step size in seconds. */
    public double integrationStepSeconds() {
        return integrationStepSeconds;
    }

    /**
     * The committed state of the simulation as an immutable snapshot.
     */
    public SimulationSnapshot snapshot() {
        List<BodySnapshot> observed = new ArrayList<>(bodies.size());
        for (SimulationBody body : bodies) {
            observed.add(new BodySnapshot(body.definition(), body.state()));
        }
        return new SimulationSnapshot(
                completedSteps * integrationStepSeconds, completedSteps, observed);
    }

    /**
     * Replaces the integration strategy (ADR-006 §10). Permitted only between
     * completed integration operations; changes nothing but the strategy —
     * not bodies, not the clock, not the timestep, not the model.
     *
     * @throws NullPointerException if the integrator is null
     */
    public void selectIntegrator(Integrator integrator)
    {
        this.integrator = Objects.requireNonNull(integrator, "integrator must not be null");
    }

    /**
     * Registers a listener to receive snapshot and breakdown notifications.
     * Registration is idempotent: adding the same listener twice does not
     * produce duplicate notifications. Registration changes made during a
     * notification take effect from the next notification.
     *
     * @throws NullPointerException if the listener is null
     */
    public void addListener(SimulationListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener. Listener arguments must be
     * non-null throughout this API, so a null argument is rejected rather
     * than silently ignored.
     *
     * @return true if the listener was registered and has been removed
     * @throws NullPointerException if the listener is null
     */
    public boolean removeListener(SimulationListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        return listeners.remove(listener);
    }

    private void publishSnapshot(SimulationSnapshot snapshot) {
        dispatch(listener -> listener.onSnapshot(snapshot));
    }

    private void publishBreakdown(
            StepResult.NumericalBreakdown breakdown, SimulationSnapshot lastCommitted) {
        dispatch(listener -> listener.onNumericalBreakdown(breakdown, lastCommitted));
    }

    /**
     * Invokes a notification on every registered listener, isolating each.
     * A {@code RuntimeException} from one listener is reported to the error
     * handler and does not prevent the remaining listeners from being
     * notified, disturb the committed step, or reach the caller;
     * {@code AssertionError} and serious JVM errors are not caught
     * (ADR-006 §9).
     * <p>
     * Dispatch iterates a copy of the registration set. This is an
     * implementation decision, not a requirement of ADR-006: it makes
     * re-entrant registration safe, so a listener that adds or removes
     * listeners during its own callback does not disturb the dispatch in
     * progress. Such changes take effect from the next notification.
     */
    private void dispatch(Consumer<SimulationListener> notification)
    {
        for (SimulationListener listener : List.copyOf(listeners)) {
            try {
                notification.accept(listener);
            } catch (RuntimeException failure) {
                reportListenerFailure(listener, failure);
            }
        }
    }

    /**
     * Reports a listener failure through the error handler. If the handler
     * itself throws, a non-recursive best-effort fallback is written to
     * {@code System.err} naming both the original listener failure and the
     * handler's own failure — the listener failure is never routed back
     * through the handler mechanism. Serious JVM errors are not caught here.
     */
    private void reportListenerFailure(SimulationListener listener, RuntimeException failure) {
        try {
            listenerErrorHandler.onListenerError(listener, failure);
        } catch (RuntimeException handlerFailure) {
            System.err.println(
                    "ListenerErrorHandler threw while reporting listener failure. "
                            + "Original failure: " + failure
                            + "; handler failure: " + handlerFailure);
        }
    }

    /**
     * Attempts exactly one integration step of the fixed timestep
     * (ADR-006 §3) and reports the outcome synchronously (ADR-006 §8).
     * <p>
     * On success, all body states and the step counter are updated together
     * and the new snapshot is returned. On numerical breakdown, nothing is
     * committed: the engine retains its previous state and clock, and the
     * result identifies the attempted time and the first body at which a
     * non-finite component was detected.
     *
     * @throws IllegalStateException if the integrator violates its structural
     *         return contract; nothing is committed in that case either
     */
    public StepResult step()
    {
        StepResult result = integrateOneStep();

        switch (result) {
            case StepResult.Advanced advanced -> publishSnapshot(advanced.snapshot());
            case StepResult.NumericalBreakdown breakdown ->
                    publishBreakdown(breakdown, snapshot());
        }

        return result;
    }


    /**
     * Advances the simulation by the requested amount of simulated time,
     * executing whole fixed substeps and carrying any remainder (ADR-006 §3).
     * <p>
     * The requested seconds are added to the internal accumulator; while the
     * accumulator holds at least one full timestep, substeps of exactly
     * {@link #integrationStepSeconds()} are executed. Only successfully
     * committed substeps are deducted. The numerical step size never varies:
     * a larger request produces more substeps of identical size, never a
     * larger step.
     * <p>
     * {@code advanceBy(0.0)} is a valid no-op request: zero simulated time is
     * legitimate, and accepting it keeps the API closed under ordinary arithmetic.
     * Under the normal invariant the accumulator rests strictly
     * below the timestep between calls, so a zero request commits zero steps
     * and changes nothing.
     * <p>
     * <strong>Atomicity is per substep, not per call.</strong> This operation
     * is deliberately not transactional: ADR-006 §8 defines partial success,
     * and substeps committed before a failure remain committed.
     * <p>
     * <strong>On numerical breakdown</strong>, the accumulator is reset to
     * zero and all unconsumed requested time is discarded (normative,
     * ADR-006 §3): the engine rests at the last committed state and clock,
     * and the result reports how many substeps succeeded and where breakdown
     * occurred. No further substeps are attempted.
     * <p>
     * <strong>On exceptional termination</strong> — any runtime exception
     * escaping an internal substep — the accumulator is likewise reset to
     * zero before the exception is rethrown, so a later call cannot
     * implicitly continue an operation that terminated abnormally. A
     * structural integrator defect ({@code IllegalStateException}) is one
     * source; a physics-boundary rejection such as coincident interacting
     * bodies ({@code IllegalArgumentException} from the gravity layer) is
     * another. Substeps committed before the exception remain committed.
     * <p>
     * The request must be non-negative and finite, and must not cause the
     * accumulated requested time to overflow to infinity; the check runs
     * before any mutation, so a rejected request leaves carried time intact.
     * Two individually finite values can sum to positive infinity, and an
     * infinite accumulator would make the substep loop non-terminating. A
     * huge but still finite request is not policed: requesting far more
     * simulated time than the caller is prepared to wait for is the caller's
     * responsibility.
     * <p>
     * Observer publication for this operation (one event per call, ADR-006
     * §7) is introduced together with listener dispatch; this method
     * currently publishes nothing.
     *
     * @param simulatedSeconds requested simulated time; non-negative and
     *                         finite, zero permitted
     * @return the outcome of the whole operation, always carrying the last
     *         committed snapshot
     * @throws IllegalArgumentException if the request is negative, NaN or
     *                                  infinite, or would overflow the
     *                                  accumulated requested time
     * @throws IllegalStateException    if the integrator violates its
     *                                  structural return contract
     */
    public AdvanceResult advanceBy(double simulatedSeconds)
    {
        if (simulatedSeconds < 0.0 || !Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException(
                    "simulatedSeconds must be non-negative and finite, was: "
                            + simulatedSeconds);
        }

        // Checked before assignment so a rejected request cannot corrupt existing carried time.
        // Two individually finite values can sum to positive infinity, allowing an infinite accumulator would make the substep loop non-terminating.
        double updatedAccumulator = accumulatedSeconds + simulatedSeconds;
        if (!Double.isFinite(updatedAccumulator))
        {
            throw new IllegalArgumentException(
                    "simulatedSeconds causes the accumulated requested time to overflow: "
                            + accumulatedSeconds + " + " + simulatedSeconds);
        }// end if
        accumulatedSeconds = updatedAccumulator;

        long stepsCommitted = 0L;

        try {
            while (accumulatedSeconds >= integrationStepSeconds)
            {
                switch (integrateOneStep())
                {
                    case StepResult.Advanced advanced -> {
                        accumulatedSeconds -= integrationStepSeconds;
                        stepsCommitted++;
                    }
                    case StepResult.NumericalBreakdown breakdown -> {
                        // Normative reset (ADR-006 §3): committed substeps stand, unconsumed time is discarded, nothing further is attempted
                        accumulatedSeconds = 0.0;

                        // Publication (ADR-006 §7): one shared snapshot for both the observers and the returned result.
                        // If any substep committed, publish the final snapshot first, then the breakdown — in that order.
                        SimulationSnapshot finalSnapshot = snapshot();
                        if (stepsCommitted > 0)
                        {
                            publishSnapshot(finalSnapshot);
                        }
                        publishBreakdown(breakdown, finalSnapshot);

                        return new AdvanceResult(finalSnapshot, stepsCommitted, 0.0, breakdown);
                    }
                }
            }
        }
        catch (RuntimeException failure)
        {
            // Exceptional termination: committed substeps stand, but carried time is discarded so no later call implicitly continues an abnormally terminated operation.
            // No publication occurs on this path — the operation did not complete normally.
            accumulatedSeconds = 0.0;
            throw failure;
        }

        // Normal completion. Publish one snapshot only if at least one substep committed; a zero-step call publishes nothing (ADR-006 §7).
        // The same snapshot instance is returned to the caller and sent to observers.
        SimulationSnapshot finalSnapshot = snapshot();
        if (stepsCommitted > 0)
        {
            publishSnapshot(finalSnapshot);
        }
        return new AdvanceResult(finalSnapshot, stepsCommitted, accumulatedSeconds, null);
    } // --- end of advanceBy() --------------------------------------------------------------------------------------------

    /**
     * The single-step primitive that {@code step()} — and, in a later
     * iteration, {@code advanceBy()} — drives (ADR-006 §7). Publication
     * policy will live in the public operations, not here.
     */
    private StepResult integrateOneStep() {
        StateVector[] currentStates = new StateVector[bodies.size()];
        for (int i = 0; i < bodies.size(); i++) {
            currentStates[i] = bodies.get(i).state();
        }

        StateVector[] candidate =
                integrator.step(currentStates, integrationStepSeconds, accelerationModel);
        requireValidCandidateStructure(candidate);

        double attemptedTime = (completedSteps + 1) * integrationStepSeconds;

        int invalidBodyIndex = firstNonFiniteBodyIndex(candidate);
        if (invalidBodyIndex >= 0) {
            // Reject: state and clock remain exactly as they were (ADR-006 §5)
            return new StepResult.NumericalBreakdown(attemptedTime, invalidBodyIndex);
        }

        // Atomic commit: all states, then the counter.
        // No failure point exists here — structure and finiteness were validated above, and
        // updateState performs no further checks that could throw for a validated candidate
        for (int i = 0; i < bodies.size(); i++) {
            bodies.get(i).updateState(candidate[i]);
        }
        completedSteps++;

        return new StepResult.Advanced(snapshot());
    }

    /**
     * Verifies the structural return contract of {@link Integrator#step}
     * before the candidate is inspected or committed. A violation is a
     * collaborator defect, not a numerical outcome, and is never converted
     * into a {@code NumericalBreakdown} — mirroring how integrators treat
     * {@code AccelerationModel} contract violations (ADR-005 §4).
     */
    private void requireValidCandidateStructure(StateVector[] candidate) {
        if (candidate == null) {
            throw new IllegalStateException("Integrator returned null");
        }
        if (candidate.length != bodies.size()) {
            throw new IllegalStateException(
                    "Integrator returned " + candidate.length
                            + " states for " + bodies.size() + " bodies");
        }
        for (int i = 0; i < candidate.length; i++) {
            if (candidate[i] == null) {
                throw new IllegalStateException(
                        "Integrator returned null state at index " + i);
            }
        }
    }

    /**
     * The finiteness inspection ADR-005 §9 assigns to the caller of an
     * integrator: index of the first body whose candidate state contains a
     * non-finite component, or -1 if the whole candidate is finite.
     */
    private static int firstNonFiniteBodyIndex(StateVector[] candidate) {
        for (int i = 0; i < candidate.length; i++) {
            StateVector state = candidate[i];
            if (!isFinite(state)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isFinite(StateVector state) {
        return Double.isFinite(state.position().x())
                && Double.isFinite(state.position().y())
                && Double.isFinite(state.position().z())
                && Double.isFinite(state.velocity().x())
                && Double.isFinite(state.velocity().y())
                && Double.isFinite(state.velocity().z());
    }
}