package io.github.eugenp84.orbitalsim.learning.scenario;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import java.util.Objects;

/**
 * Data-neutral initial conditions for the canonical L4 Apophis experiment.
 * <p>
 * The states are heliocentric Cartesian states in the simulator's SI units and must refer to the same TDB epoch.
 * This record deliberately belongs to the learning module rather than exposing a Horizons data-layer type across the scenario boundary.
 *
 * @param apophisState Apophis heliocentric state in metres and metres per second
 * @param earthState   Earth heliocentric state in metres and metres per second
 * @param jdTdb        common Julian Day epoch in TDB
 *
 * @author Eugeniu PLATITA
 */
public record Level4InitialConditions(
        StateVector apophisState,
        StateVector earthState,
        double jdTdb)
{
    /** Validates the common epoch and both SI state vectors. */
    public Level4InitialConditions
    {
        Objects.requireNonNull(apophisState, "apophisState must not be null");
        Objects.requireNonNull(earthState, "earthState must not be null");
        if (!Double.isFinite(jdTdb) || jdTdb <= 0.0)
        {
            throw new IllegalArgumentException("jdTdb must be positive and finite");
        }
        requireFinite(apophisState, "apophisState");
        requireFinite(earthState, "earthState");
    }

    /**
     * Verifies that every state component is finite at the scenario boundary.
     *
     * @param state state to inspect
     * @param name  state name used in the failure message
     */
    private static void requireFinite(final StateVector state, final String name)
    {
        final boolean finite = Double.isFinite(state.position().x())
                && Double.isFinite(state.position().y())
                && Double.isFinite(state.position().z())
                && Double.isFinite(state.velocity().x())
                && Double.isFinite(state.velocity().y())
                && Double.isFinite(state.velocity().z());
        if (!finite)
        {
            throw new IllegalArgumentException(name + " components must be finite");
        }
    }
}
