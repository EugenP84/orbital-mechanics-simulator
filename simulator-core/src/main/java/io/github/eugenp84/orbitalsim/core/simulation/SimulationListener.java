package io.github.eugenp84.orbitalsim.core.simulation;

/**
 * A decoupled observer of simulation progress.
 * <p>
 * Payloads are immutable; listeners may retain them freely.
 *
 * @author Eugeniu PLATITA
 */
public interface SimulationListener {

    /**
     * A new state was committed.
     *
     * @param snapshot the committed state; immutable
     */
    default void onSnapshot(SimulationSnapshot snapshot) {
        // Listeners interested only in failures may ignore snapshots
    }

    /**
     * An integration step was rejected because the candidate contained a non-finite component.
     *
     * @param breakdown the failure description
     * @param lastCommitted the state the engine continues to hold; immutable
     */
    default void onNumericalBreakdown(
            StepResult.NumericalBreakdown breakdown,
            SimulationSnapshot lastCommitted) {
        // Listeners interested only in snapshots may ignore failures
    }
}