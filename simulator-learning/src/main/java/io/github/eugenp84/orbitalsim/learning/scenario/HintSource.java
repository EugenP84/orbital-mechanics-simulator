package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Optional;

/**
 * A read-only view of the current learner guidance for an attempt.
 * <p>
 * This is the narrow capability the presentation layer is given:
 * it can read the latest hint, but cannot reach the simulation engine,
 * the hint engine's wiring, or the listener registration behind it.
 * The hint is empty until enough state has been observed to infer one.
 *
 * @author Eugeniu PLATITA
 */
public interface HintSource
{
    /** The current hint or empty if none has been inferred yet. */
    Optional<Hint> latestHint();
}