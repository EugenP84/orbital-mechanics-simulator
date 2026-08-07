package io.github.eugenp84.orbitalsim.core.model;

/**
 * Immutable identity and physical properties of a celestial body.
 * <p>
 * A celestial body definition carries no simulation state; its
 * time-dependent position and velocity are held by a
 * {@link SimulationBody} (see ADR-002, definition–state separation).
 * All quantities are in canonical SI units.
 *
 * @param id     unique identifier, never null or blank
 * @param name   display name, never null or blank
 * @param type   astronomical classification, never null
 * @param mass   mass in kilograms, strictly positive
 * @param radius mean radius in metres, strictly positive
 */
public record CelestialBody(
        String id,
        String name,
        CelestialBodyType type,
        double mass,
        double radius
) {

    public CelestialBody {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Body id must not be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Body name must not be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Body type must not be null");
        }
        if (mass <= 0.0 || !Double.isFinite(mass)) {
            throw new IllegalArgumentException("Mass must be positive and finite, was: " + mass);
        }
        if (radius <= 0.0 || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("Radius must be positive and finite, was: " + radius);
        }
    }
}