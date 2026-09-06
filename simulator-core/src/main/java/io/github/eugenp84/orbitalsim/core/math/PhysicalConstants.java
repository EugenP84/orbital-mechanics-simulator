package io.github.eugenp84.orbitalsim.core.math;

/**
 * Physical and astronomical constants used throughout the simulation.
 * All values are expressed in canonical SI units (metres, kilograms, seconds).
 * Every quantity entering the simulation core is expected to be converted to these units at the system boundary (factory or data layer);
 * no other unit system appears inside simulator-core.
 * Values follow CODATA and IAU recommendations.
 *
 * @author Eugeniu PLATITA
 */
public final class PhysicalConstants
{

    /** Gravitational constant G, in m³ kg⁻¹ s⁻². */
    public static final double GRAVITATIONAL_CONSTANT = 6.674_30e-11;

    /** One astronomical unit, in metres (IAU 2012 exact definition). */
    public static final double ASTRONOMICAL_UNIT = 1.495_978_707e11;

    /** Solar mass, in kilograms. */
    public static final double SOLAR_MASS = 1.988_92e30;

    /** Earth mass, in kilograms. */
    public static final double EARTH_MASS = 5.972_2e24;

    /** Mean Earth radius, in metres. */
    public static final double EARTH_RADIUS = 6.371_000e6;

    /** Solar radius, in metres. */
    public static final double SOLAR_RADIUS = 6.957e8;

    private PhysicalConstants() {
        // Static constants holder — not instantiable !!! --------
    }
}