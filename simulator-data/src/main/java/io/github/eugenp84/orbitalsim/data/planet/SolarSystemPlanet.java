package io.github.eugenp84.orbitalsim.data.planet;

import java.util.Objects;

/**
 * One trusted reference planet used by the Level 2 comparison activity.
 * Numeric values are stored in the units published/normalised by the bundled NASA-derived fixture.
 *
 * @author Eugeniu PLATITA
 */
public record SolarSystemPlanet(
        String name,
        String type,
        double massKilograms,
        double meanRadiusKilometres,
        double meanDensityKilogramsPerCubicMetre,
        double meanSurfaceGravityMetresPerSecondSquared,
        double meanDistanceFromSunMillionKilometres,
        double orbitalPeriodDays,
        String summary)
{
    public SolarSystemPlanet
    {
        requireText(name, "name");
        requireText(type, "type");
        requireText(summary, "summary");
        requirePositiveFinite(massKilograms, "massKilograms");
        requirePositiveFinite(meanRadiusKilometres, "meanRadiusKilometres");
        requirePositiveFinite(meanDensityKilogramsPerCubicMetre,"meanDensityKilogramsPerCubicMetre");
        requirePositiveFinite(meanSurfaceGravityMetresPerSecondSquared,"meanSurfaceGravityMetresPerSecondSquared");
        requirePositiveFinite(meanDistanceFromSunMillionKilometres,"meanDistanceFromSunMillionKilometres");
        requirePositiveFinite(orbitalPeriodDays, "orbitalPeriodDays");
    }

    private static void requireText(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePositiveFinite(final double value, final String name) {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}
