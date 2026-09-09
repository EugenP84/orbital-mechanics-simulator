package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Thrown when construction of a scenario attempt is refused because the resulting orbit cannot be assessed reliably at the configured observation cadence.
 * <p>
 * This represents an <em>expected assessment refusal</em> that an application may handle explicitly,
 * for example by reporting it to the learner, rather than a programming or configuration invariant
 * violation, which remains a plain {@link IllegalArgumentException}.
 * That distinction is the one a caller acts on: it catches this type to convert a refusal into feedback,
 * while letting every other {@link IllegalArgumentException} propagate as the defect it is.
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