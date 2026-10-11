package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * A read-only view of a Level 3 attempt's running perturbation measurement.
 * <p>
 *
 * @author Eugeniu PLATITA
 */
public interface Level3ProgressSource
{
    /**
     * The greatest osculating eccentricity the target planet has reached, relative to the star, since the attempt was constructed.
     * This is a finite-horizon perturbation measure, not a claim about orbital stability.
     */
    double maxEccentricity();
}