package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * A read-only view of a Level 3 attempt's running perturbation measurement.
 * <p>
 * This is the narrow capability the presentation layer is given:
 * it can read the maximum target eccentricity observed so far, but cannot reach the completion policy that accumulates it or mutate
 * its assessment state. The value is meaningful from attempt construction onward — it is seeded with the initial target eccentricity,
 * not an artificial zero — and grows monotonically as the experiment runs, so it can be displayed live as well as at completion  {@code HintSource}).
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