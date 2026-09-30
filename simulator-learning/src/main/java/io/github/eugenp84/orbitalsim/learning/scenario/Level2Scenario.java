package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Objects;

/**
 * Level 2 (Planet Comparison) domain logic.
 * <p>
 * This level is exploratory rather than assessable: it has no simulation lifecycle, pass/fail criterion, completion policy or adaptive hints.
 * It compares the learner-created L0 planet with one real Solar-System planet using four physical quantities.
 *
 * @author Eugeniu PLATITA
 */
public final class Level2Scenario
{
    /**
     * Compares the learner profile with a selected reference profile.
     *
     * @param learner learner-created planet profile
     * @param reference selected real-planet profile
     * @return learner/reference ratios for the four compared quantities
     */
    public Level2Comparison compare(final Level2PlanetProfile learner, final Level2PlanetProfile reference)
    {
        Objects.requireNonNull(learner, "learner must not be null");
        Objects.requireNonNull(reference, "reference must not be null");

        return new Level2Comparison(
                learner.massKilograms() / reference.massKilograms(),
                learner.radiusMetres() / reference.radiusMetres(),
                learner.meanDensityKilogramsPerCubicMetre()
                        / reference.meanDensityKilogramsPerCubicMetre(),
                learner.surfaceGravityMetresPerSecondSquared()
                        / reference.surfaceGravityMetresPerSecondSquared());
    }
}
