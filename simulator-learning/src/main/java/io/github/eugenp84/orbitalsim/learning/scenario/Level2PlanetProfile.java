package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;

/**
 * The four physical quantities compared in Level 2.
 * <p>
 * A learner-created planet is constructed from mass and radius; mean density and surface
 * gravitational acceleration are then derived for a spherical body. Reference planets may use
 * authoritative published values for all four quantities.
 *
 * @param massKilograms             mass in kilograms
 * @param radiusMetres              volumetric mean radius in metres
 * @param meanDensityKilogramsPerCubicMetre mean density in kg/m^3
 * @param surfaceGravityMetresPerSecondSquared mean surface gravity in m/s^2
 *
 * @author Eugeniu PLATITA
 */
public record Level2PlanetProfile(
        double massKilograms,
        double radiusMetres,
        double meanDensityKilogramsPerCubicMetre,
        double surfaceGravityMetresPerSecondSquared)
{
    public Level2PlanetProfile
    {
        requirePositiveFinite(massKilograms, "massKilograms");
        requirePositiveFinite(radiusMetres, "radiusMetres");
        requirePositiveFinite(meanDensityKilogramsPerCubicMetre, "meanDensityKilogramsPerCubicMetre");
        requirePositiveFinite(surfaceGravityMetresPerSecondSquared,"surfaceGravityMetresPerSecondSquared");
    }

    /**
     * Derives density and surface gravity from a learner-selected mass and radius.
     *
     * @param massKilograms mass in kilograms
     * @param radiusMetres radius in metres
     * @return complete Level 2 physical profile
     */
    public static Level2PlanetProfile fromMassAndRadius(final double massKilograms, final double radiusMetres)
    {
        requirePositiveFinite(massKilograms, "massKilograms");
        requirePositiveFinite(radiusMetres, "radiusMetres");

        final double volume = (4.0 / 3.0) * Math.PI * radiusMetres * radiusMetres * radiusMetres;
        final double density = massKilograms / volume;
        final double gravity = PhysicalConstants.GRAVITATIONAL_CONSTANT * massKilograms / (radiusMetres * radiusMetres);

        return new Level2PlanetProfile(massKilograms, radiusMetres, density, gravity);
    }

    private static void requirePositiveFinite(final double value, final String name)
    {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}
