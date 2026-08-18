package io.github.eugenp84.orbitalsim.core.physics;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnergyCalculatorTest {

    private static final double RELATIVE_TOLERANCE = 1e-9;

    /** A body on the x-axis at {@code x}, moving in +y at {@code vy}. */
    private static StateVector at(double x, double vy) {
        return new StateVector(new Vector3D(x, 0, 0), new Vector3D(0, vy, 0));
    }

    /** A stationary body at an arbitrary position. */
    private static StateVector restingAt(double x, double y, double z) {
        return new StateVector(new Vector3D(x, y, z), Vector3D.ZERO);
    }

    // --- Kinetic energy ---

    @Test
    void kineticEnergyMatchesHalfMVSquared() {
        // speed = 5, so K = ½ · 2 · 25 = 25 J
        assertEquals(25.0,
                EnergyCalculator.kineticEnergy(2.0, new Vector3D(3, 4, 0)),
                1e-12);
    }

    @Test
    void kineticEnergyIsZeroForStationaryBody() {
        assertEquals(0.0, EnergyCalculator.kineticEnergy(1e24, Vector3D.ZERO));
    }

    @Test
    void kineticEnergyScalesWithVelocitySquared() {
        double slow = EnergyCalculator.kineticEnergy(1e24, new Vector3D(0, 1e3, 0));
        double fast = EnergyCalculator.kineticEnergy(1e24, new Vector3D(0, 2e3, 0));

        assertEquals(4.0, fast / slow, RELATIVE_TOLERANCE);
    }

    // --- Potential energy ---

    @Test
    void potentialEnergyMatchesNewtonianFormula() {
        double massA = 2.0e24;
        double massB = 3.0e24;
        double distance = 4.0e7;

        double expected = -PhysicalConstants.GRAVITATIONAL_CONSTANT
                * massA * massB / distance;

        assertEquals(expected,
                EnergyCalculator.potentialEnergy(massA, massB, distance),
                Math.abs(expected) * RELATIVE_TOLERANCE);
    }

    @Test
    void potentialEnergyIsNegative() {
        double energy = EnergyCalculator.potentialEnergy(
                PhysicalConstants.SOLAR_MASS,
                PhysicalConstants.EARTH_MASS,
                PhysicalConstants.ASTRONOMICAL_UNIT);

        assertTrue(energy < 0.0);
    }

    @Test
    void potentialEnergyApproachesZeroWithSeparation() {
        double near = EnergyCalculator.potentialEnergy(1e30, 1e24, 1e11);
        double far = EnergyCalculator.potentialEnergy(1e30, 1e24, 2e11);

        assertTrue(far > near);                             // less negative
        assertEquals(0.5, far / near, RELATIVE_TOLERANCE);  // U ∝ 1/r
    }

    @Test
    void potentialEnergyIsSymmetricInMasses() {
        double u1 = EnergyCalculator.potentialEnergy(1e24, 1e30, 1e11);
        double u2 = EnergyCalculator.potentialEnergy(1e30, 1e24, 1e11);

        assertEquals(u1, u2, Math.abs(u1) * RELATIVE_TOLERANCE);
    }

    // --- System totals ---

    @Test
    void totalKineticEnergySumsAllBodies() {
        double[] masses = {2.0, 3.0};
        StateVector[] states = {
                new StateVector(Vector3D.ZERO, new Vector3D(3, 4, 0)),             // ½·2·25 = 25
                new StateVector(new Vector3D(1e11, 0, 0), new Vector3D(0, 2, 0))   // ½·3·4  = 6
        };

        assertEquals(31.0,
                EnergyCalculator.totalKineticEnergy(masses, states),
                RELATIVE_TOLERANCE);
    }

    @Test
    void totalPotentialEnergyCountsEachPairOnce() {
        // Distinct masses and a non-collinear, unequally spaced geometry: every
        // pair term differs, so any mis-pairing or double-counting changes the
        // total. The expected value is computed from the raw formula rather
        // than by calling potentialEnergy(), so an error inside that method
        // cannot cancel out on both sides of the assertion.
        double m0 = 1.0e24;
        double m1 = 2.0e24;
        double m2 = 3.0e24;
        double[] masses = {m0, m1, m2};

        StateVector[] states = {
                restingAt(0, 0, 0),
                restingAt(1.0e8, 0, 0),
                restingAt(0, 2.0e8, 0)
        };

        double r01 = 1.0e8;
        double r02 = 2.0e8;
        double r12 = Math.sqrt(1.0e8 * 1.0e8 + 2.0e8 * 2.0e8);

        double g = PhysicalConstants.GRAVITATIONAL_CONSTANT;
        double expected = -g * (m0 * m1 / r01 + m0 * m2 / r02 + m1 * m2 / r12);

        assertEquals(expected,
                EnergyCalculator.totalPotentialEnergy(masses, states),
                Math.abs(expected) * RELATIVE_TOLERANCE);
    }

    @Test
    void totalPotentialEnergyIsNotDoubleCounted() {
        // Two bodies: the total must equal the single pair value, guarding
        // against summation over ordered rather than unique pairs
        double m1 = 1e30;
        double m2 = 1e24;
        double r = 1e11;
        double[] masses = {m1, m2};
        StateVector[] states = {at(0, 0), at(r, 0)};

        double pair = EnergyCalculator.potentialEnergy(m1, m2, r);

        assertEquals(pair,
                EnergyCalculator.totalPotentialEnergy(masses, states),
                Math.abs(pair) * RELATIVE_TOLERANCE);
    }

    @Test
    void totalPotentialEnergyOfSingleBodyIsZero() {
        assertEquals(0.0, EnergyCalculator.totalPotentialEnergy(
                new double[]{1e30}, new StateVector[]{at(0, 0)}));
    }

    @Test
    void emptySystemHasZeroEnergy() {
        double[] masses = {};
        StateVector[] states = {};

        assertEquals(0.0, EnergyCalculator.totalKineticEnergy(masses, states));
        assertEquals(0.0, EnergyCalculator.totalPotentialEnergy(masses, states));
        assertEquals(0.0, EnergyCalculator.totalMechanicalEnergy(masses, states));
    }

    @Test
    void totalMechanicalEnergyIsKineticPlusPotential() {
        double[] masses = {PhysicalConstants.SOLAR_MASS, PhysicalConstants.EARTH_MASS};
        StateVector[] states = {at(0, 0), at(PhysicalConstants.ASTRONOMICAL_UNIT, 2.978e4)};

        double expected = EnergyCalculator.totalKineticEnergy(masses, states)
                + EnergyCalculator.totalPotentialEnergy(masses, states);

        assertEquals(expected,
                EnergyCalculator.totalMechanicalEnergy(masses, states),
                Math.abs(expected) * RELATIVE_TOLERANCE);
    }

    @Test
    void circularOrbitHasNegativeTotalEnergy() {
        // Test-particle circular orbit: the Sun is held stationary, so only the
        // orbiting body contributes kinetic energy. With v² = GM/r this gives
        // E = ½·GMm/r − GMm/r = −½·GMm/r, i.e. exactly U/2 for this
        // configuration. (A true two-body circular orbit would have both bodies
        // orbiting the barycentre; that is not what is constructed here.)
        double r = PhysicalConstants.ASTRONOMICAL_UNIT;
        double v = Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT
                * PhysicalConstants.SOLAR_MASS / r);

        double[] masses = {PhysicalConstants.SOLAR_MASS, PhysicalConstants.EARTH_MASS};
        StateVector[] states = {at(0, 0), at(r, v)};

        double total = EnergyCalculator.totalMechanicalEnergy(masses, states);
        double potential = EnergyCalculator.totalPotentialEnergy(masses, states);

        assertTrue(total < 0.0);
        assertEquals(potential / 2.0, total, Math.abs(potential) * RELATIVE_TOLERANCE);
    }

    // --- Contract validation: single-quantity methods ---

    @Test
    void invalidMassesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.kineticEnergy(0.0, Vector3D.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.kineticEnergy(Double.NaN, Vector3D.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(-1e24, 1e30, 1e11));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(1e24, Double.POSITIVE_INFINITY, 1e11));
    }

    @Test
    void invalidSeparationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(1e24, 1e30, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(1e24, 1e30, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(1e24, 1e30, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(1e24, 1e30, Double.POSITIVE_INFINITY));
    }

    // --- Contract validation: system-level methods ---

    @Test
    void singleBodyInvalidMassIsRejected() {
        // The pair loop never executes for one body, so the mass must be
        // validated at system level rather than only inside potentialEnergy
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalPotentialEnergy(
                        new double[]{Double.NaN}, new StateVector[]{at(0, 0)}));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalPotentialEnergy(
                        new double[]{-1e24}, new StateVector[]{at(0, 0)}));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalPotentialEnergy(
                        new double[]{0.0}, new StateVector[]{at(0, 0)}));
    }

    @Test
    void invalidMassIsRejectedAtEverySystemLevelMethod() {
        double[] masses = {1e30, Double.NaN};
        StateVector[] states = {at(0, 0), at(1e11, 0)};

        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalKineticEnergy(masses, states));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalPotentialEnergy(masses, states));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalMechanicalEnergy(masses, states));
    }

    @Test
    void coincidentBodiesAreRejected() {
        double[] masses = {1e30, 1e24};
        StateVector same = at(1e11, 0);

        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalPotentialEnergy(
                        masses, new StateVector[]{same, same}));
    }

    @Test
    void mismatchedInputLengthsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalKineticEnergy(
                        new double[]{1e30, 1e24}, new StateVector[]{at(0, 0)}));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalPotentialEnergy(
                        new double[]{1e30}, new StateVector[]{at(0, 0), at(1e11, 0)}));
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.totalMechanicalEnergy(
                        new double[]{1e30, 1e24}, new StateVector[]{at(0, 0)}));
    }

    @Test
    void nullInputsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> EnergyCalculator.kineticEnergy(1e24, null));

        // One representative case per public system-level method
        assertThrows(NullPointerException.class,
                () -> EnergyCalculator.totalKineticEnergy(null, new StateVector[]{at(0, 0)}));
        assertThrows(NullPointerException.class,
                () -> EnergyCalculator.totalPotentialEnergy(
                        null, new StateVector[]{at(0, 0)}));
        assertThrows(NullPointerException.class,
                () -> EnergyCalculator.totalMechanicalEnergy(new double[]{1e24}, null));
        assertThrows(NullPointerException.class,
                () -> EnergyCalculator.totalKineticEnergy(
                        new double[]{1e24}, new StateVector[]{null}));
    }

    // --- Contract validation: output finiteness guards ---

    @Test
    void nonFiniteKineticEnergyIsRejected() {
        // Components are finite, but magnitudeSquared and the mass product
        // overflow, exercising the output guard
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.kineticEnergy(
                        Double.MAX_VALUE, new Vector3D(1e200, 0, 0)));
    }

    @Test
    void nonFinitePotentialEnergyIsRejected() {
        // All inputs valid; the mass product overflows before division
        assertThrows(IllegalArgumentException.class,
                () -> EnergyCalculator.potentialEnergy(
                        Double.MAX_VALUE, Double.MAX_VALUE, 1.0));
    }
}