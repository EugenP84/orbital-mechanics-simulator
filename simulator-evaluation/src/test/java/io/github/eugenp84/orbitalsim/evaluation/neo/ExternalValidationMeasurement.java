package io.github.eugenp84.orbitalsim.evaluation.neo;

/**
 * Descriptive discrepancy between one simulated encounter measurement and the independent JPL CAD reference.
 * <p>
 * No field represents a pass/fail threshold. The differences are evidence for numerical and model evaluation only.
 *
 * @param encounter simulated sampled-minimum measurement
 * @param signedDistanceDifferenceMetres simulated minus JPL nominal distance
 * @param absoluteDistanceDifferenceMetres absolute distance discrepancy
 * @param signedEpochDifferenceSeconds simulated minus JPL close-approach epoch
 * @param signedRelativeSpeedDifferenceMetresPerSecond simulated minus JPL speed
 *
 * @author Eugeniu PLATITA
 */
public record ExternalValidationMeasurement(
        EncounterMeasurement encounter,
        double signedDistanceDifferenceMetres,
        double absoluteDistanceDifferenceMetres,
        double signedEpochDifferenceSeconds,
        double signedRelativeSpeedDifferenceMetresPerSecond) {
}
