package io.github.eugenp84.orbitalsim.core.math;

/**
 * An immutable snapshot of a body's kinematic state: position and
 * velocity at a given instant. Integrators consume a state vector and
 * produce a new one for the next time step.
 */
public record StateVector(Vector3D position, Vector3D velocity) {
}