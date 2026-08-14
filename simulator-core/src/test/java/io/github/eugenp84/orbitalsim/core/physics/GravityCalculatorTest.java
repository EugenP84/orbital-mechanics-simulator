package io.github.eugenp84.orbitalsim.core.physics;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravityCalculatorTest {

    private static final double RELATIVE_TOLERANCE = 1e-9;

    // --- Physical validation against independently known values ---

    @Test
    void surfaceGravityOfEarthMatchesKnownValue() {
        Vector3D surface = new Vector3D(PhysicalConstants.EARTH_RADIUS, 0, 0);

        Vector3D acceleration = GravityCalculator.accelerationBetween(
                surface, Vector3D.ZERO, PhysicalConstants.EARTH_MASS);

        assertEquals(9.82, acceleration.magnitude(), 0.02);
    }

    @Test
    void forceMagnitudeMatchesEarthSunAttraction() {
        double force = GravityCalculator.forceMagnitude(
                PhysicalConstants.SOLAR_MASS,
                PhysicalConstants.EARTH_MASS,
                PhysicalConstants.ASTRONOMICAL_UNIT);

        assertEquals(3.54e22, force, 0.02e22);
    }

    // --- Newtonian law properties ---------------

    @Test
    void accelerationObeysInverseSquareLaw() {
        double near = GravityCalculator.accelerationBetween(
                new Vector3D(1.0e9, 0, 0), Vector3D.ZERO,
                PhysicalConstants.SOLAR_MASS).magnitude();
        double far = GravityCalculator.accelerationBetween(
                new Vector3D(2.0e9, 0, 0), Vector3D.ZERO,
                PhysicalConstants.SOLAR_MASS).magnitude();

        assertEquals(4.0, near / far, RELATIVE_TOLERANCE);
    }

    @Test
    void accelerationScalesLinearlyWithSourceMass() {
        Vector3D position = new Vector3D(1.0e11, 0, 0);

        double a1 = GravityCalculator.accelerationBetween(
                position, Vector3D.ZERO, 1.0e30).magnitude();
        double a2 = GravityCalculator.accelerationBetween(
                position, Vector3D.ZERO, 2.0e30).magnitude();

        assertEquals(2.0, a2 / a1, RELATIVE_TOLERANCE);
    }

    @Test
    void accelerationMagnitudeMatchesNewtonianFormula() {
        double r = 1.496e11;

        Vector3D acceleration = GravityCalculator.accelerationBetween(
                new Vector3D(r, 0, 0), Vector3D.ZERO, PhysicalConstants.SOLAR_MASS);

        double expected = PhysicalConstants.GRAVITATIONAL_CONSTANT
                * PhysicalConstants.SOLAR_MASS / (r * r);
        assertEquals(expected, acceleration.magnitude(), expected * RELATIVE_TOLERANCE);
    }

    @Test
    void accelerationIsDirectedTowardsTheSource() {
        Vector3D acceleration = GravityCalculator.accelerationBetween(
                new Vector3D(1.0e9, 0, 0), Vector3D.ZERO,
                PhysicalConstants.SOLAR_MASS);

        // Body at +x, source at origin: acceleration must point in -x.
        // A tolerance is used on the transverse components rather than exact comparison:
        // assertEquals(double, double) distinguishes 0.0 from -0.0,
        // which would produce a confusing failure if the sign of the scalar
        // factor ever changed.
        assertTrue(acceleration.x() < 0.0);
        assertEquals(0.0, acceleration.y(), 1e-30);
        assertEquals(0.0, acceleration.z(), 1e-30);
    }

    @Test
    void accelerationIsCorrectForNonAxisAlignedGeometry() {
        // Source along (1,1,1) from the body: guards against component
        // transposition or per-axis sign errors invisible to axis-aligned tests
        double d = 1.0e9;
        Vector3D source = new Vector3D(d, d, d);

        Vector3D acceleration = GravityCalculator.accelerationBetween(
                Vector3D.ZERO, source, PhysicalConstants.SOLAR_MASS);

        double separation = source.magnitude();
        double expected = PhysicalConstants.GRAVITATIONAL_CONSTANT
                * PhysicalConstants.SOLAR_MASS / (separation * separation);

        assertEquals(expected, acceleration.magnitude(), expected * RELATIVE_TOLERANCE);

        // All components equal and positive, directed towards (1,1,1)
        double component = expected / Math.sqrt(3.0);
        assertEquals(component, acceleration.x(), component * RELATIVE_TOLERANCE);
        assertEquals(component, acceleration.y(), component * RELATIVE_TOLERANCE);
        assertEquals(component, acceleration.z(), component * RELATIVE_TOLERANCE);
    }

    @Test
    void forceMagnitudeIsSymmetricInMasses() {
        double f1 = GravityCalculator.forceMagnitude(1e24, 1e30, 1e11);
        double f2 = GravityCalculator.forceMagnitude(1e30, 1e24, 1e11);

        assertEquals(f1, f2, f1 * RELATIVE_TOLERANCE);
    }

    // --------- Contract validation: accelerationBetween ------------

    @Test
    void nullPositionsAreRejected() {
        Vector3D valid = new Vector3D(1e11, 0, 0);

        assertThrows(NullPointerException.class,
                () -> GravityCalculator.accelerationBetween(
                        null, valid, PhysicalConstants.SOLAR_MASS));
        assertThrows(NullPointerException.class,
                () -> GravityCalculator.accelerationBetween(
                        valid, null, PhysicalConstants.SOLAR_MASS));
    }

    @Test
    void coincidentPositionsAreRejected() {
        Vector3D p = new Vector3D(1e11, 2e11, 3e11);

        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(
                        p, p, PhysicalConstants.SOLAR_MASS));
    }

    @Test
    void nonFiniteGeometryIsRejected() {
        Vector3D valid = new Vector3D(1e11, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(
                        new Vector3D(Double.NaN, 0, 0), valid,
                        PhysicalConstants.SOLAR_MASS));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(
                        valid, new Vector3D(Double.POSITIVE_INFINITY, 0, 0),
                        PhysicalConstants.SOLAR_MASS));
    }

    @Test
    void overflowInDerivedSeparationIsRejected() {
        // Both positions hold finite components, but their difference
        // overflows to infinity. This is why ADR-003 requires the derived
        // separation to be finite, not merely the inputs.
        Vector3D position = new Vector3D(-Double.MAX_VALUE, 0, 0);
        Vector3D source = new Vector3D(Double.MAX_VALUE, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(
                        position, source, PhysicalConstants.SOLAR_MASS));
    }

    @Test
    void invalidSourceMassIsRejected() {
        Vector3D a = new Vector3D(1e11, 0, 0);
        Vector3D b = Vector3D.ZERO;

        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(a, b, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(a, b, -1.0e30));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(a, b, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(a, b, Double.POSITIVE_INFINITY));
    }

    @Test
    void nonFiniteComputedAccelerationIsRejected() {
        // Inputs are finite and the derived separation is finite and non-zero,
        // but G·M/r² overflows, exercising the output finiteness guard.
        //
        // The exponent is load-bearing: at 1e-200 the squared magnitude would
        // underflow to exactly 0.0 and trip the coincident-positions branch
        // instead, so this test would then pass for the wrong reason. 1e-100
        // keeps distanceSquared (1e-200) representable and non-zero.
        Vector3D position = new Vector3D(1.0e-100, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.accelerationBetween(
                        position, Vector3D.ZERO, Double.MAX_VALUE));
    }

    // --- Contract validation: forceMagnitude -------

    @Test
    void invalidMassesInForceMagnitudeAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(0.0, 1e30, 1e11));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(-1e24, 1e30, 1e11));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(1e24, Double.NaN, 1e11));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(1e24, Double.POSITIVE_INFINITY, 1e11));
    }

    @Test
    void invalidSeparationInForceMagnitudeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(1e24, 1e30, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(1e24, 1e30, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(1e24, 1e30, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(1e24, 1e30, Double.POSITIVE_INFINITY));
    }

    @Test
    void nonFiniteComputedForceIsRejected() {
        // All inputs valid; the product overflows, exercising the output guard.
        assertThrows(IllegalArgumentException.class,
                () -> GravityCalculator.forceMagnitude(
                        Double.MAX_VALUE, Double.MAX_VALUE, 1.0));
    }
}