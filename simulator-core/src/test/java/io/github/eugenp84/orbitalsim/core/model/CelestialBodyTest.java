package io.github.eugenp84.orbitalsim.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CelestialBodyTest {

    @Test
    void validBodyIsConstructed() {
        CelestialBody sun = new CelestialBody(
                "sun", "Sun", CelestialBodyType.STAR, 1.9889e30, 6.957e8);

        assertEquals("sun", sun.id());
        assertEquals("Sun", sun.name());
        assertEquals(CelestialBodyType.STAR, sun.type());
        assertEquals(1.9889e30, sun.mass());
        assertEquals(6.957e8, sun.radius());
    }

    @Test
    void nullIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody(null, "Sun", CelestialBodyType.STAR, 1e30, 1e8));
    }

    @Test
    void blankIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("  ", "Sun", CelestialBodyType.STAR, 1e30, 1e8));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "", CelestialBodyType.STAR, 1e30, 1e8));
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", null, 1e30, 1e8));
    }

    @Test
    void nonPositiveMassIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", CelestialBodyType.STAR, 0.0, 1e8));
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", CelestialBodyType.STAR, -5.0, 1e8));
    }

    @Test
    void nonFiniteMassIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", CelestialBodyType.STAR, Double.NaN, 1e8));
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", CelestialBodyType.STAR, Double.POSITIVE_INFINITY, 1e8));
    }

    @Test
    void nonPositiveRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", CelestialBodyType.STAR, 1e30, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialBody("sun", "Sun", CelestialBodyType.STAR, 1e30, -1.0));
    }

    @Test
    void recordsWithSameComponentsAreEqual() {
        // Value semantics: definitions can be compared and safely shared
        CelestialBody a = new CelestialBody("earth", "Earth", CelestialBodyType.PLANET, 5.9722e24, 6.371e6);
        CelestialBody b = new CelestialBody("earth", "Earth", CelestialBodyType.PLANET, 5.9722e24, 6.371e6);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}