package io.github.eugenp84.orbitalsim.data.horizons;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import java.util.Objects;

/**
 * Earth and Apophis Cartesian states at one common Horizons TDB epoch.
 * State-vector components are normalised to SI units before construction.
 *
 * @param apophisState Apophis heliocentric state in metres and metres per second
 * @param earthState   Earth heliocentric state in metres and metres per second
 * @param jdTdb        common Julian Day epoch in TDB
 *
 * @author Eugeniu PLATITA
 */
public record HorizonsEpochState(
        StateVector apophisState,
        StateVector earthState,
        double jdTdb) {

    /**
     * Validates the common epoch and both normalised states.
     */
    public HorizonsEpochState
    {
        Objects.requireNonNull(apophisState, "apophisState must not be null");
        Objects.requireNonNull(earthState, "earthState must not be null");
        if (!Double.isFinite(jdTdb) || jdTdb <= 0.0) {
            throw new IllegalArgumentException("jdTdb must be positive and finite");
        }
        requireFinite(apophisState, "apophisState");
        requireFinite(earthState, "earthState");
    }

    /**
     * Verifies that every component of a state is finite at the external-data boundary,
     * because {@link StateVector} deliberately does not enforce this.
     *
     * @param state state to inspect
     * @param name  state name used in the failure message
     */
    private static void requireFinite(final StateVector state, final String name)
    {
        boolean finite = Double.isFinite(state.position().x())
                && Double.isFinite(state.position().y())
                && Double.isFinite(state.position().z())
                && Double.isFinite(state.velocity().x())
                && Double.isFinite(state.velocity().y())
                && Double.isFinite(state.velocity().z());
        if (!finite) {
            throw new IllegalArgumentException(name + " components must be finite");
        }
    }
}
