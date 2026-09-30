package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Dimensionless ratios of the learner-created planet to one selected reference planet.
 * A value of 1.0 means equality; values above/below 1.0 mean the learner value is larger/smaller respectively.
 *
 * @author Eugeniu PLATITA
 */
public record Level2Comparison(
        double massRatio,
        double radiusRatio,
        double densityRatio,
        double surfaceGravityRatio)
{
}
