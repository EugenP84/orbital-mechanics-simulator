package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Thrown when construction of a scenario attempt is refused because the resulting orbit cannot be assessed reliably at the configured observation cadence.
 * <p>
 * It extends {@link IllegalArgumentException} so existing callers that catch the broader type keep their behaviour; only callers that must distinguish the two catch this narrower type.
 *
 * @author Eugeniu PLATITA
 */
public final class ScenarioAttemptNotAssessableException extends IllegalArgumentException
{
    public ScenarioAttemptNotAssessableException(final String message)
    {
        super(message);
    }
}