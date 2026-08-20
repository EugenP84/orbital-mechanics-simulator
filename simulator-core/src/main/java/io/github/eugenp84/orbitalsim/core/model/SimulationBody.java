package io.github.eugenp84.orbitalsim.core.model;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import java.util.Objects;

/**
 * The stable runtime entity used throughout a simulation run:
 * associates an immutable {@link CelestialBody} definition with its
 * current, replaceable {@link StateVector} snapshot.
 * <p>
 * Observers (renderer, hint engine) may safely hold references to a
 * SimulationBody across simulation steps; only the state reference is
 * replaced, never the object itself (see ADR-002, Option C rationale).
 * <p>
 * State replacement is an atomic reference swap of a complete immutable
 * snapshot. Callers (integrators via the engine) must compute all next
 * states before committing any of them.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationBody {

    private final CelestialBody definition;
    private StateVector state;

    public SimulationBody(CelestialBody definition, StateVector initialState) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.state = Objects.requireNonNull(initialState, "initialState must not be null");
    }

    public CelestialBody definition() {
        return definition;
    }

    public StateVector state() {
        return state;
    }

    public void updateState(StateVector newState) {
        this.state = Objects.requireNonNull(newState, "newState must not be null");
    }
}