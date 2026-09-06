package io.github.eugenp84.orbitalsim.core.orbital;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalStateCalculatorTest
{

    private static final double MU_SUN =
            PhysicalConstants.GRAVITATIONAL_CONSTANT * PhysicalConstants.SOLAR_MASS;
    private static final double AU = PhysicalConstants.ASTRONOMICAL_UNIT;

    // ---============= Analytically known orbits ------------------------------

    @Test
    void circularOrbitHasZeroEccentricityAndSemiMajorAxisEqualToRadius() {
        double speed = Math.sqrt(MU_SUN / AU);
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(AU, 0, 0), new Vector3D(0, speed, 0), MU_SUN);

        assertEquals(0.0, a.eccentricity(), 1e-12);
        assertEquals(-MU_SUN / (2.0 * AU), a.specificOrbitalEnergy(),
                Math.abs(MU_SUN / (2.0 * AU)) * 1e-12);
        assertTrue(a.semiMajorAxis().isPresent());
        assertEquals(AU, a.semiMajorAxis().getAsDouble(), AU * 1e-9);
        assertEquals(AU * speed, a.specificAngularMomentum().z(), AU * speed * 1e-12);
        assertFalse(a.isRadial());
    }

    @Test
    void ellipticalOrbitIsBoundAndSatisfiesVisViva()
    {
        double speed = 0.9 * Math.sqrt(MU_SUN / AU);
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(AU, 0, 0), new Vector3D(0, speed, 0), MU_SUN);

        assertTrue(a.specificOrbitalEnergy() < 0.0);
        assertTrue(a.eccentricity() > 0.0 && a.eccentricity() < 1.0,
                "expected an ellipse, got e=" + a.eccentricity());
        assertTrue(a.semiMajorAxis().isPresent());
        assertTrue(a.semiMajorAxis().getAsDouble() > 0.0);

        // Cross-checks energy and semi-major axis against each other, so a sign
        // or factor error in either would not pass unnoticed.
        double semiMajorAxis = a.semiMajorAxis().getAsDouble();
        assertEquals(-MU_SUN / (2.0 * semiMajorAxis), a.specificOrbitalEnergy(),
                Math.abs(a.specificOrbitalEnergy()) * 1e-12);
    }

    @Test
    void hyperbolicOrbitHasPositiveEnergyAndNegativeSemiMajorAxis()
    {
        double speed = 1.5 * Math.sqrt(MU_SUN / AU);
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(AU, 0, 0), new Vector3D(0, speed, 0), MU_SUN);

        assertTrue(a.specificOrbitalEnergy() > 0.0);
        assertTrue(a.eccentricity() > 1.0, "expected a hyperbola, got e=" + a.eccentricity());
        assertTrue(a.semiMajorAxis().isPresent());
        assertTrue(a.semiMajorAxis().getAsDouble() < 0.0,
                "hyperbolic semi-major axis is conventionally negative");
    }

    @Test
    void parabolicBoundaryHasNoSemiMajorAxis()
    {
        // Exactly-representable values, because sqrt(2*mu/r) at astronomical scale does not reliably land on exactly zero energy.
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(1.0, 0, 0), new Vector3D(0, 2.0, 0), 2.0);

        assertEquals(0.0, a.specificOrbitalEnergy());
        assertEquals(1.0, a.eccentricity(), 1e-15);
        assertTrue(a.semiMajorAxis().isEmpty());
    }

    //-------------------------------------------------------------------------------------------
    @Test
    void aNearParabolicOrbitStillHasAFiniteSemiMajorAxis() {
        // Guards the boundary from being widened into a tolerance: nearly parabolic is not parabolic and does have a semi-major axis.
        double speed = Math.sqrt(2.0 * MU_SUN / AU) * (1.0 - 1.0e-9);
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(AU, 0, 0), new Vector3D(0, speed, 0), MU_SUN);

        assertTrue(a.semiMajorAxis().isPresent());
        assertTrue(a.semiMajorAxis().getAsDouble() > AU);
    }

    @Test
    void aNonRepresentableSemiMajorAxisIsRejectedRatherThanReportedAsParabolic() {
        // Absence must keep one meaning. Here the energy is small but clearly
        // non-zero while a overflows, so reporting "parabolic" would be false.
        double hugeMu = 1.0e308;
        double radius = 1.0e300;
        double speed = Math.sqrt(2.0 * (hugeMu / radius) * (1.0 + 1.0e-12));

        assertThrows(IllegalArgumentException.class, () -> OrbitalStateCalculator.analyse(
                new Vector3D(radius, 0, 0), new Vector3D(0, speed, 0), hugeMu));
    }

    // --- Radial degeneracy ---

    @Test
    void radialMotionIsBoundDespiteUnitEccentricity()
    {
        // The reason L1 must test escape by energy: a learner giving zero
        // tangential velocity gets e = 1, which an "e >= 1" rule would call
        // escape while the planet is in fact falling inward.
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(AU, 0, 0), new Vector3D(1.0e3, 0, 0), MU_SUN);

        assertTrue(a.isRadial());
        assertEquals(0.0, a.specificAngularMomentum().x());
        assertEquals(0.0, a.specificAngularMomentum().y());
        assertEquals(0.0, a.specificAngularMomentum().z());
        assertEquals(1.0, a.eccentricity(), 1e-12);
        assertTrue(a.specificOrbitalEnergy() < 0.0);
        //assertTrue(a.isRadial());
    }

    @Test
    void aBodyAtRestIsStillAnalysable()
    {
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(AU, 0, 0), Vector3D.ZERO, MU_SUN);

        assertTrue(a.isRadial());
        assertEquals(1.0, a.eccentricity(), 1e-12);
        assertEquals(-MU_SUN / AU, a.specificOrbitalEnergy(), Math.abs(MU_SUN / AU) * 1e-12);
    }

    // --- Frame conventions ---

    @Test
    void relativeStateIsUnchangedByACommonTranslationAndVelocityBoost() {
        // Verifies the ADR-009 §3 consequence. Note the limit of what it shows:
        // the calculator never sees absolute states, so this tests the
        // relative-state convention rather than the orbital formulas.
        Vector3D bodyPosition = new Vector3D(AU, 0.3 * AU, 0.1 * AU);
        Vector3D referencePosition = new Vector3D(0.2 * AU, -0.1 * AU, 0.0);
        Vector3D bodyVelocity = new Vector3D(-5.0e3, 2.9e4, 1.0e3);
        Vector3D referenceVelocity = new Vector3D(1.0e2, -2.0e2, 0.0);

        Vector3D positionOffset = new Vector3D(7.0e11, -3.0e11, 2.0e11);
        Vector3D velocityBoost = new Vector3D(1.2e4, -8.0e3, 4.0e3);

        Vector3D relativePosition = bodyPosition.subtract(referencePosition);
        Vector3D relativeVelocity = bodyVelocity.subtract(referenceVelocity);
        Vector3D shiftedRelativePosition = bodyPosition.add(positionOffset).subtract(referencePosition.add(positionOffset));
        Vector3D boostedRelativeVelocity = bodyVelocity.add(velocityBoost).subtract(referenceVelocity.add(velocityBoost));

        assertEquals(relativePosition, shiftedRelativePosition);
        assertEquals(relativeVelocity, boostedRelativeVelocity);
        assertEquals(
                OrbitalStateCalculator.analyse(relativePosition, relativeVelocity, MU_SUN),
                OrbitalStateCalculator.analyse(
                        shiftedRelativePosition, boostedRelativeVelocity, MU_SUN));
    }

    // -======================-- Reference-plane azimuth --------------------------

    @Test
    void azimuthIsNormalisedToTheRangeZeroToTwoPi() {
        double speed = Math.sqrt(MU_SUN / AU);

        assertEquals(0.0, azimuthAt(AU, 0.0, speed), 1e-12);
        assertEquals(Math.PI / 2, azimuthAt(0.0, AU, speed), 1e-12);
        assertEquals(Math.PI, azimuthAt(-AU, 0.0, speed), 1e-12);
        // atan2 gives -pi/2 here; the contract requires the normalised value.
        assertEquals(3 * Math.PI / 2, azimuthAt(0.0, -AU, speed), 1e-12);
    }

    private static double azimuthAt(double x, double y, double speed) {
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(x, y, 0), new Vector3D(0, 0, speed), MU_SUN);
        assertTrue(a.referencePlaneAzimuth().isPresent());


        return a.referencePlaneAzimuth().getAsDouble();
    }

    @Test
    void azimuthIsAbsentOnTheReferencePlaneNormal() {
        // Without the guard, Math.atan2(0, 0) would silently supply zero here.
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(0, 0, AU), new Vector3D(1.0e4, 0, 0), MU_SUN);

        assertTrue(a.referencePlaneAzimuth().isEmpty());
    }

    @Test
    void azimuthSurvivesTheCircularCaseWhereTrueAnomalyWouldNot() {
        // The reason the azimuth exists at all: L1 targets e = 0, where the
        // periapsis reference disappears and true anomaly is undefined.
        double speed = Math.sqrt(MU_SUN / AU);
        OrbitalStateAnalysis a = OrbitalStateCalculator.analyse(
                new Vector3D(0.0, AU, 0), new Vector3D(-speed, 0, 0), MU_SUN);

        assertEquals(0.0, a.eccentricity(), 1e-12);
        assertTrue(a.referencePlaneAzimuth().isPresent());
        assertEquals(Math.PI / 2, a.referencePlaneAzimuth().getAsDouble(), 1e-12);
    }

    // --- Validation ---

    @Test
    void inputsAreValidated() {
        Vector3D r = new Vector3D(AU, 0, 0);
        Vector3D v = new Vector3D(0, 3.0e4, 0);

        assertThrows(NullPointerException.class,
                () -> OrbitalStateCalculator.analyse(null, v, MU_SUN));
        assertThrows(NullPointerException.class,
                () -> OrbitalStateCalculator.analyse(r, null, MU_SUN));

        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(Vector3D.ZERO, v, MU_SUN),
                "coincident bodies have no defined orbit");

        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(r, v, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(r, v, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(r, v, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(r, v, Double.POSITIVE_INFINITY));

        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(new Vector3D(Double.NaN, 0, 0), v, MU_SUN));
        assertThrows(IllegalArgumentException.class,
                () -> OrbitalStateCalculator.analyse(
                        r, new Vector3D(0, Double.POSITIVE_INFINITY, 0), MU_SUN));
    }

    @Test
    void theAnalysisRecordEnforcesItsOwnInvariants() {
        // The record is public, so it must be valid without the calculator.
        Vector3D h = new Vector3D(0, 0, 1.0);
        Vector3D e = new Vector3D(0.1, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> new OrbitalStateAnalysis(
                new Vector3D(Double.NaN, 0, 0), -1.0, e, 0.1,
                OptionalDouble.of(1.0), OptionalDouble.of(0.0)));
        assertThrows(IllegalArgumentException.class, () -> new OrbitalStateAnalysis(
                h, -1.0, new Vector3D(0, Double.POSITIVE_INFINITY, 0), 0.1,
                OptionalDouble.of(1.0), OptionalDouble.of(0.0)));
        assertThrows(IllegalArgumentException.class, () -> new OrbitalStateAnalysis(
                h, -1.0, e, 0.1,
                OptionalDouble.of(Double.POSITIVE_INFINITY), OptionalDouble.of(0.0)));
        assertThrows(IllegalArgumentException.class, () -> new OrbitalStateAnalysis(
                h, -1.0, e, 0.1, OptionalDouble.of(1.0), OptionalDouble.of(-0.1)));
        assertThrows(IllegalArgumentException.class, () -> new OrbitalStateAnalysis(
                h, -1.0, e, 0.1, OptionalDouble.of(1.0), OptionalDouble.of(2.0 * Math.PI)));
        assertThrows(IllegalArgumentException.class, () -> new OrbitalStateAnalysis(
                h, -1.0, e, -0.1, OptionalDouble.of(1.0), OptionalDouble.of(0.0)));
    }
}