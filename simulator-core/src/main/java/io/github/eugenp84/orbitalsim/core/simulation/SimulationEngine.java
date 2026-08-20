package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.model.SimulationBody;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /**
     * @param initialBodies          definition–state pairs for every body, in
     *                               the body order all snapshots will use;
     *                               must contain at least one body
     * @param accelerationModel      the physical model, fixed for the
     *                               engine's lifetime
     * @param integrationStepSeconds the fixed numerical step size; positive
     *                               and finite (ADR-005 §7)
     * @param integrator             the initially selected strategy
     * @throws NullPointerException     if any argument or list element is null
     * @throws IllegalArgumentException if the body list is empty or the
     *                                  timestep is not positive and finite
     */
    public SimulationEngine(
            List<BodySnapshot> initialBodies,
            AccelerationModel accelerationModel,
            double integrationStepSeconds,
            Integrator integrator) {

        Objects.requireNonNull(initialBodies, "initialBodies must not be null");
        this.accelerationModel =
                Objects.requireNonNull(accelerationModel, "accelerationModel must not be null");
        this.integrator = Objects.requireNonNull(integrator, "integrator must not be null");

        if (initialBodies.isEmpty()) {
            throw new IllegalArgumentException("at least one body is required");
        }
        if (integrationStepSeconds <= 0.0 || !Double.isFinite(integrationStepSeconds)) {
            throw new IllegalArgumentException(
                    "integrationStepSeconds must be positive and finite, was: "
                            + integrationStepSeconds);
        }
        this.integrationStepSeconds = integrationStepSeconds;

        // List.copyOf rejects null elements; BodySnapshot enforces non-null
        // components. Engine-private SimulationBody aggregates are built from
        // the pairs (ADR-006 §4) — never from parallel collections.
        List<BodySnapshot> pairs = List.copyOf(initialBodies);
        this.bodies = new ArrayList<>(pairs.size());
        for (BodySnapshot pair : pairs) {
            this.bodies.add(new SimulationBody(pair.definition(), pair.state()));
        }

        this.completedSteps = 0L;
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
    public void selectIntegrator(Integrator integrator) {
        this.integrator = Objects.requireNonNull(integrator, "integrator must not be null");
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
    public StepResult step() {
        return integrateOneStep();
    }

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

        // Atomic commit: all states, then the counter. No failure point
        // exists here — structure and finiteness were validated above, and
        // updateState performs no further checks that could throw for a
        // validated candidate
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