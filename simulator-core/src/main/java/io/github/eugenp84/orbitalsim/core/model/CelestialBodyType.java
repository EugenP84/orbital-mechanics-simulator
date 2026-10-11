package io.github.eugenp84.orbitalsim.core.model;

/**
 * Astronomical classification of a simulated celestial body.
 * <p>
 * Classification is representational only: all body types participate in gravitational simulation identically.
 * Additional passive classifications (e.g. MOON, COMET) may be added as needed.
 *
 * @author Eugeniu PLATITA
 */
public enum CelestialBodyType {
    STAR,
    PLANET,
    ASTEROID
}