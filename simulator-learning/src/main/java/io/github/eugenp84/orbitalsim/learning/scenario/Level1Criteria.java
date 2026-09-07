package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Calibrated acceptance thresholds for the Level 1 stable-orbit assessment (ADR-008 p.6).
 * <p>
 * Grouped because they are adjacent same-typed doubles edited together during calibration, where a transposed pair would compile silently, and because
 * ADR-008-p6 requires the calibrated numbers to have one documented home rather than appearing as unexplained constants.
 * <p>
 *
 * @param requiredRevolutions complete revolutions the learner must sustain; positive
 * @param maximumEccentricity largest eccentricity accepted at the completion  checkpoint; finite, in [0, 1).
 *                            This is the passing criterion rather than an input limit: a more eccentric bound orbit may be attempted, receives guidance from the hint system,
 *                            and fails at the checkpoint — provided it remains assessable at the configured observation cadence, since a near-parabolic orbit may be rejected before starting
 * @param minimumSurfaceClearanceMetres smallest permitted gap between the two bodies' surfaces; finite, non-negative
 * @param maximumRelativeEnergyDrift largest permitted {@code |E − E₀| / |E₀|} in total mechanical energy before the attempt stops being assessable;
 *        finite, non-negative.
 *
 * @author Eugeniu PLATITA
 */
public record Level1Criteria(
        int requiredRevolutions,
        double maximumEccentricity,
        double minimumSurfaceClearanceMetres,
        double maximumRelativeEnergyDrift)
{

    public Level1Criteria
    {
        if (requiredRevolutions <= 0) {
            throw new IllegalArgumentException("requiredRevolutions must be positive, was: " + requiredRevolutions);
        }
        if (!Double.isFinite(maximumEccentricity) || maximumEccentricity < 0.0 || maximumEccentricity >= 1.0)
        {
            throw new IllegalArgumentException("maximumEccentricity must be finite and within [0, 1), was: " + maximumEccentricity);
        }

        if (!Double.isFinite(minimumSurfaceClearanceMetres) || minimumSurfaceClearanceMetres < 0.0) {
            throw new IllegalArgumentException("minimumSurfaceClearanceMetres must be finite and non-negative, was: " + minimumSurfaceClearanceMetres);
        }
        if (!Double.isFinite(maximumRelativeEnergyDrift) || maximumRelativeEnergyDrift < 0.0) {
            throw new IllegalArgumentException("maximumRelativeEnergyDrift must be finite and non-negative, was: " + maximumRelativeEnergyDrift);
        }
    } //---------- end of Level1Criteria -------------------==================================
}