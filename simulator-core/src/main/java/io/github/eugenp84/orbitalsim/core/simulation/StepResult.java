package io.github.eugenp84.orbitalsim.core.simulation;

import java.util.Objects;

/**
 * The synchronous outcome of a single {@code step()} call.
 * <p>
 * The interface is sealed so that callers can switch exhaustively over the outcome.
 *
 * @author Eugeniu PLATITA
 */
public sealed interface StepResult
{
    /**
     * The step was committed: all candidate states were finite, body states were updated, and simulation time advanced.
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
     * Nothing was committed: the engine retains the previous state and clock.
     *
     * @param attemptedTimeSeconds    the simulation time the rejected step would have reached; finite and non-negative
     * @param firstNonFiniteBodyIndex the lowest body index at which a non-finite component was detected; non-negative
     */
    record NumericalBreakdown(
            double attemptedTimeSeconds,
            int firstNonFiniteBodyIndex) implements StepResult
    {
        public NumericalBreakdown {
            if (!Double.isFinite(attemptedTimeSeconds) || attemptedTimeSeconds < 0.0) {
                throw new IllegalArgumentException("attemptedTimeSeconds must be finite and non-negative, was: " + attemptedTimeSeconds);
            }
            if (firstNonFiniteBodyIndex < 0) {
                throw new IllegalArgumentException("firstNonFiniteBodyIndex must be non-negative, was: " + firstNonFiniteBodyIndex);
            }
        }
    }
}