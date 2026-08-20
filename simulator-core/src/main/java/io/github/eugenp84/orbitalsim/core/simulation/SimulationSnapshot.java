package io.github.eugenp84.orbitalsim.core.simulation;

import java.util.List;
import java.util.Objects;

/**
 * An immutable observation of committed simulation state at an instant.
 * <p>
 * A snapshot always describes <em>committed</em> state: candidates rejected
 * by finiteness validation are never published as snapshots (ADR-006 §5).
 * Observers and callers receive snapshots rather than any runtime object, so
 * no mutable engine state crosses an architectural boundary (ADR-006 §6).
 * <p>
 * Immutability is enforced here, not at call sites: the body list is
 * defensively copied via {@link List#copyOf}, which also rejects null
 * elements, so the published list is immutable regardless of what the caller
 * supplied.
 * <p>
 * {@code completedSteps} is the authoritative clock: it is exact, while
 * {@code simulationTimeSeconds} is derived from it by a single
 * multiplication (ADR-006 §5). Simulation time is elapsed seconds from the
 * initial state, not an astronomical epoch.
 *
 * @param simulationTimeSeconds elapsed simulated time, finite and
 *                              non-negative
 * @param completedSteps        number of committed integration steps since
 *                              construction, non-negative
 * @param bodies                the committed state of every body, in engine
 *                              body order; never null, defensively copied
 *
 * @author Eugeniu PLATITA
 */
public record SimulationSnapshot(
        double simulationTimeSeconds,
        long completedSteps,
        List<BodySnapshot> bodies) {

    public SimulationSnapshot {
        if (!Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0.0) {
            throw new IllegalArgumentException(
                    "simulationTimeSeconds must be finite and non-negative, was: "
                            + simulationTimeSeconds);
        }
        if (completedSteps < 0) {
            throw new IllegalArgumentException(
                    "completedSteps must be non-negative, was: " + completedSteps);
        }
        Objects.requireNonNull(bodies, "bodies must not be null");
        bodies = List.copyOf(bodies);
    }
}