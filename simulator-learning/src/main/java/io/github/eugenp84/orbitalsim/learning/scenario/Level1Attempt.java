package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Objects;

/**
 * One assembled, ready-to-run Level 1 attempt, together with the values a presentation layer needs to describe and render it.
 * <p>
 *
 * @param session                      the runnable scenario
 * @param referenceBodyId              the body the orbit is assessed about, for locating it within published snapshots
 * @param learnerBodyId                the learner-controlled body
 * @param orbitalRadiusMetres          initial separation, useful for choosing a rendering scale; positive and finite
 * @param circularSpeedMetresPerSecond the analytic circular speed at that radius — the target the learner is aiming at, and a natural reference for input;
 *                                     positive and finite
 * @param initialSpeedMetresPerSecond  the speed this attempt was created with. positive and finite
 *
 * @author Eugeniu PLATITA
 */
public record Level1Attempt(
        ScenarioSession session,
        HintSource hints,
        String referenceBodyId,
        String learnerBodyId,
        double orbitalRadiusMetres,
        double circularSpeedMetresPerSecond,
        double initialSpeedMetresPerSecond)
{

    public Level1Attempt
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(hints, "hints must not be null");
        requireIdentifier(referenceBodyId, "referenceBodyId");
        requireIdentifier(learnerBodyId, "learnerBodyId");

        if (referenceBodyId.equals(learnerBodyId)) {
            throw new IllegalArgumentException("referenceBodyId and learnerBodyId must identify different bodies");
        }

        requirePositiveFinite(orbitalRadiusMetres, "orbitalRadiusMetres");
        requirePositiveFinite(circularSpeedMetresPerSecond, "circularSpeedMetresPerSecond");
        requirePositiveFinite(initialSpeedMetresPerSecond, "initialSpeedMetresPerSecond");
    }

    /** The chosen speed as a fraction of the circular speed; 1.0 is the target. */
    public double speedFractionOfCircular()
    {
        return initialSpeedMetresPerSecond / circularSpeedMetresPerSecond;
    }

    private static void requireIdentifier(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePositiveFinite(double value, String name)
    {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}