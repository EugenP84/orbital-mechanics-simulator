package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;

import java.util.Objects;

/**
 * An immutable pairing of a celestial body definition with a state at an
 * instant.
 * <p>
 * The type serves two roles with identical meaning (ADR-006 §6): as the
 * engine's construction input, pairing each definition with its initial
 * state, and as the per-body element of a published
 * {@link SimulationSnapshot}. In both roles it crosses an architectural
 * boundary, so it enforces its own invariants rather than relying on
 * call-site discipline.
 * <p>
 * Both components are immutable, so instances may be freely shared.
 *
 * @param definition the immutable body definition, never null
 * @param state      the body's state at the instant described, never null
 *
 * @author Eugeniu PLATITA
 */
public record BodySnapshot(CelestialBody definition, StateVector state) {

    public BodySnapshot {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}