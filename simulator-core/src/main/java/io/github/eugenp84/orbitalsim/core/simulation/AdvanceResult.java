package io.github.eugenp84.orbitalsim.core.simulation;

import java.util.Objects;

/**
 * The synchronous outcome of one {@code advanceBy()} call, which may commit several fixed substeps, none, or some followed by numerical breakdown.
 * <p>
 * The snapshot is always the engine's last committed state, so a caller never needs a second call to learn where the simulation rests.
 * The defined cases are distinguishable by field combination:
 * <ul>
 *   <li>zero substeps: {@code stepsCommitted == 0}, {@code breakdown == null},
 *       request carried in the accumulator;</li>
 *   <li>full success: {@code stepsCommitted > 0}, {@code breakdown == null};</li>
 *   <li>partial success or immediate breakdown: {@code breakdown != null},
 *       and {@code carriedSeconds == 0} per the accumulator-reset rule.</li>
 * </ul>
 * <p>
 * <strong>Two counters, two scopes.</strong> {@code stepsCommitted} counts
 * the steps committed by <em>this call</em>;
 * {@code snapshot().completedSteps()} counts every step committed since
 * engine construction. The names differ deliberately so that the two cannot
 * be confused at a call site.
 * <p>
 * Invariants enforced here: snapshot non-null, {@code stepsCommitted ≥ 0},
 * {@code carriedSeconds ≥ 0} and finite, and
 * {@code breakdown != null -> carriedSeconds == 0}. That
 * {@code carriedSeconds < integrationStepSeconds} on non-breakdown outcomes is an engine guarantee — this record does not know the step size.
 *
 * @param snapshot       the last committed state, never null
 * @param stepsCommitted steps committed by this call, non-negative
 * @param carriedSeconds accumulator content after this call, non-negative
 * @param breakdown      the breakdown that ended the call, or null if none  occurred
 *
 * @author Eugeniu PLATITA
 */
public record AdvanceResult(
        SimulationSnapshot snapshot,
        long stepsCommitted,
        double carriedSeconds,
        StepResult.NumericalBreakdown breakdown)
{
    public AdvanceResult {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        if (stepsCommitted < 0) {
            throw new IllegalArgumentException("stepsCommitted must be non-negative, was: " + stepsCommitted);
        }
        if (!Double.isFinite(carriedSeconds) || carriedSeconds < 0.0) {
            throw new IllegalArgumentException("carriedSeconds must be finite and non-negative, was: " + carriedSeconds);
        }
        if (breakdown != null && carriedSeconds != 0.0) {
            throw new IllegalArgumentException("carriedSeconds must be zero after breakdown, was: " + carriedSeconds);
        }
    }

    /** Convenience: whether this call ended in numerical breakdown. */
    public boolean brokeDown() {
        return breakdown != null;
    }
}