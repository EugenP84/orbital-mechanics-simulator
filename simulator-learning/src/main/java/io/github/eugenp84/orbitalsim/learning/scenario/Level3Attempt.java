package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Objects;

/**
 * A ready-to-run Level 3 attempt: a three-body gravitational-perturbation experiment built from the learner's chosen perturber mass and initial stellar distance.
 * <p>
 * The attempt exposes the running perturbation measurement through a narrow {@link Level3ProgressSource}.
 * The presentation layer can read the maximum target eccentricity (live or at completion) without depending on or mutating the policy's assessment state.
 *
 * @param session                   the scenario session driving the experiment; never null
 * @param progressSource            read-only source of the running perturbation measurement; never null
 * @param starId                    the star's id; never null or blank
 * @param targetId                  the target planet's id; never null or blank
 * @param perturberId               the perturbing planet's id; never null or blank
 * @param perturberMassKilograms    the configured perturber mass; positive and finite
 * @param perturberDistanceMetres   the configured perturber initial stellar distance; positive and finite
 *
 * @author Eugeniu PLATITA
 */
public record Level3Attempt(
        ScenarioSession session,
        Level3ProgressSource progressSource,
        String starId,
        String targetId,
        String perturberId,
        double perturberMassKilograms,
        double perturberDistanceMetres)
{
    public Level3Attempt
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(progressSource, "progressSource must not be null");
        requireIdentifier(starId, "starId");
        requireIdentifier(targetId, "targetId");
        requireIdentifier(perturberId, "perturberId");
        if (starId.equals(targetId) || starId.equals(perturberId) || targetId.equals(perturberId))
        {
            throw new IllegalArgumentException("starId, targetId and perturberId must be distinct");
        }
        requirePositiveFinite(perturberMassKilograms, "perturberMassKilograms");
        requirePositiveFinite(perturberDistanceMetres, "perturberDistanceMetres");
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
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}