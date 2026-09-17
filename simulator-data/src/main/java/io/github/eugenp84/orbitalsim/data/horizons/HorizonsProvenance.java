package io.github.eugenp84.orbitalsim.data.horizons;

import java.util.Objects;

/**
 * Immutable provenance for the JPL Horizons vectors used by the canonical Apophis 2029 experiment.
 *
 * @param apiVersion      Horizons API version reported by the acquisition
 * @param apophisSolution JPL orbit-solution identifier reported for Apophis
 * @param apophisTarget   Horizons target description for Apophis
 * @param center           common coordinate centre
 * @param earthTarget      Horizons target description for Earth
 * @param outputType       vector-output correction/type description
 * @param positionUnit     source position unit
 * @param referenceFrame   coordinate frame reported by Horizons
 * @param system           authoritative source system
 * @param timeScale        epoch time scale
 * @param velocityUnit     source velocity unit
 *
 * @author Eugeniu PLATITA
 */
public record HorizonsProvenance(
        String apiVersion,
        String apophisSolution,
        String apophisTarget,
        String center,
        String earthTarget,
        String outputType,
        String positionUnit,
        String referenceFrame,
        String system,
        String timeScale,
        String velocityUnit) {

    /**
     * Validates that every provenance field is present and non-blank.
     */
    public HorizonsProvenance {
        requireText(apiVersion, "apiVersion");
        requireText(apophisSolution, "apophisSolution");
        requireText(apophisTarget, "apophisTarget");
        requireText(center, "center");
        requireText(earthTarget, "earthTarget");
        requireText(outputType, "outputType");
        requireText(positionUnit, "positionUnit");
        requireText(referenceFrame, "referenceFrame");
        requireText(system, "system");
        requireText(timeScale, "timeScale");
        requireText(velocityUnit, "velocityUnit");
    }

    /**
     * Rejects missing or blank provenance text at the data boundary.
     *
     * @param value field value
     * @param name  field name used in the failure message
     */
    private static void requireText(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
