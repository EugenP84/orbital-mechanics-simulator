package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Objects;

/**
 * A ready-to-run Level 0 attempt: a valid star–planet system built from the learner's chosen planet
 * parameters — mass, radius, initial orbital distance, and tangential speed.
 * <p>
 * Level 0 introduces the parameter space before any orbital objective is imposed, so the learner's tangential speed is used as given.
 * The planet is not forced onto a circular orbit.
 * The circular speed for the chosen distance is computed by scenario composition and exposed here only as a read-only reference value,
 * to help the learner relate their choice to the stable-orbit task of Level 1.
 *
 * @param session                        the scenario session driving the created system; never null
 * @param referenceBodyId                the star's id; never null or blank
 * @param learnerBodyId                  the created planet's id; never null or blank
 * @param planetMassKilograms            the learner's chosen planet mass; positive and finite
 * @param planetRadiusMetres             the learner's chosen planet radius; positive and finite
 * @param orbitalDistanceMetres          the learner's chosen star–planet distance; positive and finite
 * @param tangentialSpeedMetresPerSecond the learner's chosen initial tangential speed; positive and finite
 * @param circularSpeedMetresPerSecond   a positive, finite circular-speed reference for the chosen distance, supplied by scenario composition;
 *                                       this record stores it but does not itself verify it was correctly derived
 *
 * @author Eugeniu PLATITA
 */
public record Level0Attempt(
        ScenarioSession session,
        String referenceBodyId,
        String learnerBodyId,
        double planetMassKilograms,
        double planetRadiusMetres,
        double orbitalDistanceMetres,
        double tangentialSpeedMetresPerSecond,
        double circularSpeedMetresPerSecond)
{
    public Level0Attempt
    {
        Objects.requireNonNull(session, "session must not be null");
        requireIdentifier(referenceBodyId, "referenceBodyId");
        requireIdentifier(learnerBodyId, "learnerBodyId");
        if (referenceBodyId.equals(learnerBodyId))
        {
            throw new IllegalArgumentException("referenceBodyId and learnerBodyId must differ");
        }
        requirePositiveFinite(planetMassKilograms, "planetMassKilograms");
        requirePositiveFinite(planetRadiusMetres, "planetRadiusMetres");
        requirePositiveFinite(orbitalDistanceMetres, "orbitalDistanceMetres");
        requirePositiveFinite(tangentialSpeedMetresPerSecond, "tangentialSpeedMetresPerSecond");
        requirePositiveFinite(circularSpeedMetresPerSecond, "circularSpeedMetresPerSecond");
    }

    private static void requireIdentifier(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePositiveFinite(final double value, final String name)
    {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}