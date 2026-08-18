package io.github.eugenp84.orbitalsim.core.physics;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;

import java.util.Objects;

/**
 * Evaluates the N-body gravitational acceleration model for a fixed set of
 * configured bodies at arbitrary supplied states.
 * <p>
 * The body configuration — masses and interaction policy — is fixed at
 * construction; only the state at which the model is evaluated varies per
 * call. This allows Euler, Velocity Verlet and Runge–Kutta 4 to query an
 * identical physical model at whatever trial states their algorithms require,
 * which is a precondition for attributing observed differences to the
 * integration methods rather than to differences in the model they evaluate
 * (ADR-004).
 * <p>
 * Acceleration is accumulated per target over every contributing source:
 * <pre>
 *     a_i = Σ over contributing j ≠ i of  G · m_j / r_ij² · r̂_ij
 * </pre>
 * Each contribution is delegated to
 * {@link GravityCalculator#accelerationBetween}. A body configured with
 * {@code contributesGravity = false} still receives acceleration from every
 * contributing source but adds nothing to any other body, which is how the
 * restricted three-body model is expressed.
 * <p>
 * Instances are immutable and hold no state between evaluations: the same
 * calculator may be reused across the multiple trial-state evaluations
 * performed within a single integration step.
 * <p>
 * Complete {@link StateVector} snapshots are accepted although Newtonian
 * gravity depends only on position, because integrators construct complete
 * trial states; only {@code position()} is read.
 * <p>
 * All quantities are in canonical SI units.
 */
public final class NBodyAccelerationCalculator {

    private final GravitationalBodyConfig[] bodyConfigs;

    /**
     * @param bodyConfigs configuration for each body, in the order in which
     *                    states will subsequently be supplied; may be empty
     * @throws NullPointerException if the array or any element is null
     */
    public NBodyAccelerationCalculator(GravitationalBodyConfig[] bodyConfigs) {
        Objects.requireNonNull(bodyConfigs, "bodyConfigs must not be null");

        // Copied so that later mutation of the caller's array cannot alter the
        // physical model. Elements are immutable records, so a shallow copy is
        // sufficient.
        this.bodyConfigs = bodyConfigs.clone();

        for (int i = 0; i < this.bodyConfigs.length; i++) {
            Objects.requireNonNull(this.bodyConfigs[i],
                    "body configuration at index " + i + " must not be null");
        }
    }

    /**
     * Gravitational acceleration on each body at the supplied states.
     *
     * @param states state of each body, in the same order as the configuration
     *               supplied at construction
     * @return acceleration in m·s⁻² for each body, in the same order; a fresh
     *         array on every call
     * @throws NullPointerException     if the array or any element is null
     * @throws IllegalArgumentException if the number of states does not match
     *         the number of configured bodies, or if any evaluated
     *         source-target interaction produces an invalid geometry (see
     *         {@link GravityCalculator#accelerationBetween}). Interactions
     *         involving a non-contributing source are never evaluated and
     *         therefore cannot raise this exception.
     */
    public Vector3D[] accelerationsAt(StateVector[] states) {
        requireMatchingStates(states);

        Vector3D[] accelerations = new Vector3D[bodyConfigs.length];

        for (int target = 0; target < bodyConfigs.length; target++) {
            Vector3D total = Vector3D.ZERO;

            for (int source = 0; source < bodyConfigs.length; source++) {
                if (source == target || !bodyConfigs[source].contributesGravity()) {
                    continue;
                }
                total = total.add(GravityCalculator.accelerationBetween(
                        states[target].position(),
                        states[source].position(),
                        bodyConfigs[source].mass()));
            }

            accelerations[target] = total;
        }

        return accelerations;
    }

    private void requireMatchingStates(StateVector[] states) {
        Objects.requireNonNull(states, "states must not be null");

        if (states.length != bodyConfigs.length) {
            throw new IllegalArgumentException(
                    "states must match the configured body count, expected "
                            + bodyConfigs.length + " but was " + states.length);
        }
        for (int i = 0; i < states.length; i++) {
            Objects.requireNonNull(states[i], "state at index " + i + " must not be null");
        }
    }
}