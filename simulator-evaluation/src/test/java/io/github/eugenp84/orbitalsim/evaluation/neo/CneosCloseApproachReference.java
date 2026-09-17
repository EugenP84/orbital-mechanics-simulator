package io.github.eugenp84.orbitalsim.evaluation.neo;

import java.util.Objects;

/**
 * Validated SI-normalised external reference for the 2029 Apophis Earth close approach.
 * <p>
 * This type belongs only to the evaluation layer. Its values must not be used to initialise, steer, stop or otherwise tune the simulated trajectory.
 *
 * @param source source system name
 * @param apiVersion CAD API version
 * @param designation small-body designation
 * @param orbitId JPL orbit solution identifier used by CAD
 * @param approachBody close-approach body
 * @param epochJdTdb nominal close-approach epoch in Julian Day TDB
 * @param nominalDistanceMetres nominal centre-to-centre distance in metres
 * @param minimum3SigmaDistanceMetres minimum 3-sigma distance in metres
 * @param maximum3SigmaDistanceMetres maximum 3-sigma distance in metres
 * @param relativeSpeedMetresPerSecond relative speed at close approach in m/s
 * @param timeUncertainty3Sigma formatted 3-sigma time uncertainty
 *
 * @author Eugeniu PLATITA
 */
public record CneosCloseApproachReference(
        String source,
        String apiVersion,
        String designation,
        String orbitId,
        String approachBody,
        double epochJdTdb,
        double nominalDistanceMetres,
        double minimum3SigmaDistanceMetres,
        double maximum3SigmaDistanceMetres,
        double relativeSpeedMetresPerSecond,
        String timeUncertainty3Sigma) {

    /**
     * Validates the external reference without interpreting it as a pass/fail criterion for the simulator.
     */
    public CneosCloseApproachReference
    {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(apiVersion, "apiVersion must not be null");
        Objects.requireNonNull(designation, "designation must not be null");
        Objects.requireNonNull(orbitId, "orbitId must not be null");
        Objects.requireNonNull(approachBody, "approachBody must not be null");
        Objects.requireNonNull(timeUncertainty3Sigma, "timeUncertainty3Sigma must not be null");
        requirePositiveFinite(epochJdTdb, "epochJdTdb");
        requirePositiveFinite(nominalDistanceMetres, "nominalDistanceMetres");
        requirePositiveFinite(minimum3SigmaDistanceMetres, "minimum3SigmaDistanceMetres");
        requirePositiveFinite(maximum3SigmaDistanceMetres, "maximum3SigmaDistanceMetres");
        requirePositiveFinite(relativeSpeedMetresPerSecond, "relativeSpeedMetresPerSecond");
        if (minimum3SigmaDistanceMetres > nominalDistanceMetres || nominalDistanceMetres > maximum3SigmaDistanceMetres)
        {
            throw new IllegalArgumentException("3-sigma distance interval must contain nominal distance");
        }
    }

    /**
     * Requires a finite, strictly positive value.
     *
     * @param value value to validate
     * @param name parameter name for diagnostics
     */
    private static void requirePositiveFinite(final double value, final String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
