package io.github.eugenp84.orbitalsim.core.simulation;

import java.util.List;
import java.util.Objects;

/**
 * An immutable observation of committed simulation state at an instant.
 * <p>
 * {@code completedSteps} is the authoritative clock: it is exact, while
 * {@code simulationTimeSeconds} is derived from it by a single multiplication.
 * Simulation time is elapsed seconds from the initial state, not an astronomical epoch.
 *
 * @param simulationTimeSeconds elapsed simulated time, finite and non-negative
 * @param completedSteps        number of committed integration steps since construction, non-negative
 * @param bodies                the committed state of every body, in engine body order; never null, defensively copied
 *
 * @author Eugeniu PLATITA
 */
public record SimulationSnapshot(
        double simulationTimeSeconds,
        long completedSteps,
        List<BodySnapshot> bodies) {

    public SimulationSnapshot
    {
        if (!Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0.0) {
            throw new IllegalArgumentException("simulationTimeSeconds must be finite and non-negative, was: " + simulationTimeSeconds);
        }
        if (completedSteps < 0) {
            throw new IllegalArgumentException("completedSteps must be non-negative, was: " + completedSteps);
        }
        Objects.requireNonNull(bodies, "bodies must not be null");
        bodies = List.copyOf(bodies);
    }
}