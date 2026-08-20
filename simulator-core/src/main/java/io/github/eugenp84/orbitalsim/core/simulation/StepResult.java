package io.github.eugenp84.orbitalsim.core.simulation;

import java.util.Objects;

/**
 * The synchronous outcome of a single {@code step()} call.
 * <p>
 * The result is how the <em>caller controlling execution</em> learns what
 * happened; observer notification is a separate mechanism for decoupled
 * components, and neither substitutes for the other (ADR-006 §8). Numerical
 * breakdown is an observable outcome, not an exception (ADR-005 §9), so it is
 * represented as a result variant rather than a throwable.
 * <p>
 * The interface is sealed so that callers can switch exhaustively over the
 * outcome. A future collision variant — a physically valid scenario event,
 * categorically distinct from numerical breakdown — can be admitted without
 * redesigning the result mechanism (ADR-006 §8); no collision model is
 * defined yet.
 *
 * @author Eugeniu PLATITA
 */
public sealed interface StepResult {

    /**
     * The step was committed: all candidate states were finite, body states
     * were updated, and simulation time advanced.
     *
     * @param snapshot the newly committed state, never null
     */
    record Advanced(SimulationSnapshot snapshot) implements StepResult {

        public Advanced {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
        }
    }

    /**
     * The integrator produced a candidate containing a non-finite component.
     * Nothing was committed: the engine retains the previous state and clock
     * (ADR-006 §5).
     * <p>
     * As an architectural boundary type, the record enforces what it can
     * itself (ADR-006 §6): the attempted time satisfies the same temporal
     * invariant as a snapshot's clock, and the body index is non-negative.
     * That the index falls within the engine's body count is an engine
     * invariant — the record cannot know the count.
     *
     * @param attemptedTimeSeconds    the simulation time the rejected step
     *                                would have reached; finite and
     *                                non-negative
     * @param firstNonFiniteBodyIndex the lowest body index at which a
     *                                non-finite component was detected;
     *                                non-negative
     */
    record NumericalBreakdown(
            double attemptedTimeSeconds,
            int firstNonFiniteBodyIndex) implements StepResult {

        public NumericalBreakdown {
            if (!Double.isFinite(attemptedTimeSeconds) || attemptedTimeSeconds < 0.0) {
                throw new IllegalArgumentException(
                        "attemptedTimeSeconds must be finite and non-negative, was: "
                                + attemptedTimeSeconds);
            }
            if (firstNonFiniteBodyIndex < 0) {
                throw new IllegalArgumentException(
                        "firstNonFiniteBodyIndex must be non-negative, was: "
                                + firstNonFiniteBodyIndex);
            }
        }
    }
}