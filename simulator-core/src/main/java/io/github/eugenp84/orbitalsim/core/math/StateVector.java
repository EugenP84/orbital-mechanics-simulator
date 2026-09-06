package io.github.eugenp84.orbitalsim.core.math;

import java.util.Objects;

/**
 * An immutable snapshot of a celestial body's kinematic state:
 * position and velocity at a given instant.
 * Integrators consume a state vector and produce a new one for the next time step.
 * Both components are guaranteed non-null:
 * a state vector missing either is not meaningful anywhere in the simulator, so the invariant is enforced here rather than re-checked by every consumer.
 * <p>
 * Component values are not constrained to be finite.
 * Finiteness is validated at the boundaries of the physical model, where it matters, rather than on every construction — state vectors are created several times per body per simulation step (ADR-003).
 *
 * @param position position in metres, never null
 * @param velocity velocity in m·s⁻¹, never null
 *
 * @author Eugeniu PLATITA
 */
public record StateVector(Vector3D position, Vector3D velocity)
{

    public StateVector
    {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(velocity, "velocity must not be null");
    }
}