package io.github.eugenp84.orbitalsim.core.simulation;

/**
 * A decoupled observer of simulation progress.
 * <p>
 * ADR-006 fixes the semantics of observation and deliberately leaves the
 * Java shape to implementation; a single interface with default methods is
 * chosen so that a renderer interested only in snapshots need not implement
 * a failure handler, and vice versa.
 * <p>
 * <strong>Cadence</strong> (ADR-006 §7): one snapshot notification per
 * successful {@code step()}; one per {@code advanceBy()} call that committed
 * at least one substep, carrying the final committed snapshot; none for
 * zero-step calls. On breakdown after successful substeps, the final
 * committed snapshot is published first, then the failure notification — in
 * that order.
 * <p>
 * <strong>Isolation</strong> (ADR-006 §9): a {@code RuntimeException} thrown
 * by a listener is contained — it does not disturb the engine, its caller,
 * the committed step, or the remaining listeners. {@code AssertionError} and
 * serious JVM errors are not caught. Notification always occurs after
 * commit, so nothing a listener does can affect simulation state.
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
     * An integration step was rejected because the candidate contained a
     * non-finite component. The engine retains its previous state and clock;
     * during {@code advanceBy()}, the accumulator has been reset.
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