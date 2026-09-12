package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Objects;

/**
 * One piece of learner-facing guidance: the {@link HintCategory} the inference produced, and the message explaining it.
 * Immutable; safe to retain and to hand to a presentation layer.
 *
 * @param category the inference outcome; never null
 * @param message  the learner-facing explanation; never null or blank
 *
 * @author Eugeniu PLATITA
 */
public record Hint(HintCategory category, String message)
{
    public Hint
    {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank())
        {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}