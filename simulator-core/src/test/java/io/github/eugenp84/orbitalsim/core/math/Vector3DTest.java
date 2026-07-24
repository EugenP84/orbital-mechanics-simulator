package io.github.eugenp84.orbitalsim.core.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vector3DTest {

    private static final double EPSILON = 1e-12;

    @Test
    void addCombinesComponents() {
        Vector3D result = new Vector3D(1, 2, 3).add(new Vector3D(4, 5, 6));
        assertEquals(new Vector3D(5, 7, 9), result);
    }

    @Test
    void magnitudeOfUnitAxesIsOne() {
        assertEquals(1.0, new Vector3D(1, 0, 0).magnitude(), EPSILON);
    }

    @Test
    void magnitudeMatchesPythagoras() {
        assertEquals(5.0, new Vector3D(3, 4, 0).magnitude(), EPSILON);
    }

    @Test
    void normalizeProducesUnitVector() {
        Vector3D normalized = new Vector3D(10, 0, 0).normalize();
        assertEquals(1.0, normalized.magnitude(), EPSILON);
    }

    @Test
    void normalizeZeroVectorThrows() {
        assertThrows(ArithmeticException.class, Vector3D.ZERO::normalize);
    }

    @Test
    void dotProductOfPerpendicularVectorsIsZero() {
        double dot = new Vector3D(1, 0, 0).dot(new Vector3D(0, 1, 0));
        assertEquals(0.0, dot, EPSILON);
    }

    @Test
    void operationsDoNotMutateOriginal() {
        Vector3D original = new Vector3D(1, 1, 1);
        original.add(new Vector3D(9, 9, 9));
        assertEquals(new Vector3D(1, 1, 1), original);
    }

    @Test
    void subtractRemovesComponents() {
        Vector3D result = new Vector3D(5, 7, 9).subtract(new Vector3D(4, 5, 6));
        assertEquals(new Vector3D(1, 2, 3), result);
    }

    @Test
    void subtractIsDirectional() {
        // a - b must not equal b - a: guards against sign errors
        Vector3D a = new Vector3D(1, 0, 0);
        Vector3D b = new Vector3D(3, 0, 0);
        assertEquals(new Vector3D(-2, 0, 0), a.subtract(b));
        assertEquals(new Vector3D(2, 0, 0), b.subtract(a));
    }

    @Test
    void multiplyScalesAllComponents() {
        Vector3D result = new Vector3D(1, -2, 3).multiply(2.5);
        assertEquals(new Vector3D(2.5, -5.0, 7.5), result);
    }

    @Test
    void multiplyByZeroYieldsZeroVector() {
        assertEquals(Vector3D.ZERO, new Vector3D(3, 4, 5).multiply(0.0));
    }

    @Test
    void magnitudeSquaredAvoidsSquareRoot() {
        assertEquals(25.0, new Vector3D(3, 4, 0).magnitudeSquared(), EPSILON);
    }

    @Test
    void magnitudeSquaredIsConsistentWithMagnitude() {
        Vector3D v = new Vector3D(1.5, -2.5, 3.5);
        assertEquals(v.magnitude() * v.magnitude(), v.magnitudeSquared(), EPSILON);
    }

    @Test
    void distanceToMatchesPythagoras() {
        Vector3D a = new Vector3D(0, 0, 0);
        Vector3D b = new Vector3D(3, 4, 0);
        assertEquals(5.0, a.distanceTo(b), EPSILON);
    }

    @Test
    void distanceToIsSymmetric() {
        Vector3D a = new Vector3D(1, 2, 3);
        Vector3D b = new Vector3D(4, 6, 8);
        assertEquals(a.distanceTo(b), b.distanceTo(a), EPSILON);
    }
}