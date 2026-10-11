package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Optional;

/**
 * A read-only view of the current learner guidance for an attempt.
 * <p>
 *
 * @author Eugeniu PLATITA
 */
public interface HintSource
{
    /** The current hint or empty if none has been inferred yet. */
    Optional<Hint> latestHint();
}