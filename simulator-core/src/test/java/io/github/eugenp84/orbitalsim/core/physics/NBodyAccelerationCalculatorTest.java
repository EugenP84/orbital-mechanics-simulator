package io.github.eugenp84.orbitalsim.core.physics;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NBodyAccelerationCalculatorTest {

    private static final double RELATIVE_TOLERANCE = 1e-9;

    private static StateVector restingAt(double x, double y, double z) {
        return new StateVector(new Vector3D(x, y, z), Vector3D.ZERO);
    }

    private static void assertVectorEquals(Vector3D expected, Vector3D actual, double scale) {
        double tolerance = scale * RELATIVE_TOLERANCE;
        assertEquals(expected.x(), actual.x(), tolerance, "x component");
        assertEquals(expected.y(), actual.y(), tolerance, "y component");
        assertEquals(expected.z(), actual.z(), tolerance, "z component");
    }

    // --- Boundary configurations ---

    @Test
    void emptyConfigurationProducesEmptyResult() {
        NBodyAccelerationCalculator calculator =
                new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{});

        assertEquals(0, calculator.accelerationsAt(new StateVector[]{}).length);
    }

    @Test
    void singleBodyExperiencesNoAcceleration() {
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS)});

        Vector3D[] accelerations = calculator.accelerationsAt(
                new StateVector[]{restingAt(0, 0, 0)});

        assertEquals(1, accelerations.length);
        assertEquals(Vector3D.ZERO, accelerations[0]);
    }

    @Test
    void configurationWithNoContributingSourcesProducesZeroAccelerations() {
        // A valid boundary configuration of the API: every body is configured
        // not to act as a source, so no body has anything to be attracted by
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.nonContributing(1e30),
                        GravitationalBodyConfig.nonContributing(1e24)});

        Vector3D[] accelerations = calculator.accelerationsAt(new StateVector[]{
                restingAt(0, 0, 0),
                restingAt(1e11, 0, 0)});

        assertEquals(Vector3D.ZERO, accelerations[0]);
        assertEquals(Vector3D.ZERO, accelerations[1]);
    }

    // --- Fully coupled systems ---

    @Test
    void twoBodyAccelerationsMatchDirectPairwiseCalculation() {
        double m0 = PhysicalConstants.SOLAR_MASS;
        double m1 = PhysicalConstants.EARTH_MASS;
        StateVector s0 = restingAt(0, 0, 0);
        StateVector s1 = restingAt(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0);

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(m0),
                        GravitationalBodyConfig.contributing(m1)});

        Vector3D[] accelerations = calculator.accelerationsAt(new StateVector[]{s0, s1});

        Vector3D expected0 = GravityCalculator.accelerationBetween(
                s0.position(), s1.position(), m1);
        Vector3D expected1 = GravityCalculator.accelerationBetween(
                s1.position(), s0.position(), m0);

        assertVectorEquals(expected0, accelerations[0], expected0.magnitude());
        assertVectorEquals(expected1, accelerations[1], expected1.magnitude());

        // Bodies are attracted towards each other along the x-axis
        assertTrue(accelerations[0].x() > 0.0);
        assertTrue(accelerations[1].x() < 0.0);
    }

    @Test
    void fullyCoupledTwoBodySystemProducesEqualAndOppositePairForces() {
        // Newton's third law holds for a fully coupled configuration. This is
        // a property of that configuration, not of the calculator in general:
        // contributesGravity = false deliberately permits asymmetric models.
        double m0 = 1.0e30;
        double m1 = 5.0e24;

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(m0),
                        GravitationalBodyConfig.contributing(m1)});

        Vector3D[] accelerations = calculator.accelerationsAt(new StateVector[]{
                restingAt(0, 0, 0),
                restingAt(3e10, 4e10, 0)});

        Vector3D force0 = accelerations[0].multiply(m0);
        Vector3D force1 = accelerations[1].multiply(m1);
        Vector3D residual = force0.add(force1);

        // Exact cancellation is not expected in IEEE 754 arithmetic
        // (ADR-004, amendment to ADR-003)
        assertVectorEquals(Vector3D.ZERO, residual, force0.magnitude());
    }

    @Test
    void threeBodyAccelerationIsTheSumOfPairwiseContributions() {
        // Verifies composition. The pairwise physics itself is verified by
        // GravityCalculatorTest and is not re-derived here.
        double m0 = 1.0e30;
        double m1 = 2.0e29;
        double m2 = 5.0e24;

        StateVector s0 = restingAt(0, 0, 0);
        StateVector s1 = restingAt(1.0e11, 0, 0);
        StateVector s2 = restingAt(0, 2.0e11, 0);

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(m0),
                        GravitationalBodyConfig.contributing(m1),
                        GravitationalBodyConfig.contributing(m2)});

        Vector3D[] accelerations = calculator.accelerationsAt(
                new StateVector[]{s0, s1, s2});

        Vector3D expected0 = GravityCalculator
                .accelerationBetween(s0.position(), s1.position(), m1)
                .add(GravityCalculator.accelerationBetween(s0.position(), s2.position(), m2));

        assertVectorEquals(expected0, accelerations[0], expected0.magnitude());
    }

    // --- Restricted (asymmetric) configuration ---

    @Test
    void nonContributingBodyStillReceivesGravity() {
        double sunMass = PhysicalConstants.SOLAR_MASS;
        double earthMass = PhysicalConstants.EARTH_MASS;
        double neoMass = 1.0e12;

        StateVector sun = restingAt(0, 0, 0);
        StateVector earth = restingAt(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0);
        StateVector neo = restingAt(1.2 * PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0);

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(sunMass),
                        GravitationalBodyConfig.contributing(earthMass),
                        GravitationalBodyConfig.nonContributing(neoMass)});

        Vector3D[] accelerations = calculator.accelerationsAt(
                new StateVector[]{sun, earth, neo});

        Vector3D expectedNeo = GravityCalculator
                .accelerationBetween(neo.position(), sun.position(), sunMass)
                .add(GravityCalculator.accelerationBetween(
                        neo.position(), earth.position(), earthMass));

        assertVectorEquals(expectedNeo, accelerations[2], expectedNeo.magnitude());
        assertTrue(accelerations[2].magnitude() > 0.0);
    }

    @Test
    void nonContributingBodyExertsNoGravityOnOthers() {
        double sunMass = PhysicalConstants.SOLAR_MASS;
        double earthMass = PhysicalConstants.EARTH_MASS;
        double neoMass = 1.0e12;

        StateVector sun = restingAt(0, 0, 0);
        StateVector earth = restingAt(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0);
        StateVector neo = restingAt(1.2 * PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0);
        StateVector[] states = {sun, earth, neo};

        NBodyAccelerationCalculator restricted = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(sunMass),
                        GravitationalBodyConfig.contributing(earthMass),
                        GravitationalBodyConfig.nonContributing(neoMass)});

        Vector3D[] accelerations = restricted.accelerationsAt(states);

        // Sun and Earth feel only each other
        Vector3D expectedSun = GravityCalculator.accelerationBetween(
                sun.position(), earth.position(), earthMass);
        Vector3D expectedEarth = GravityCalculator.accelerationBetween(
                earth.position(), sun.position(), sunMass);

        assertVectorEquals(expectedSun, accelerations[0], expectedSun.magnitude());
        assertVectorEquals(expectedEarth, accelerations[1], expectedEarth.magnitude());
    }

    @Test
    void suppressingContributionChangesOtherBodiesAccelerations() {
        // The same geometry with and without the third body contributing:
        // the primaries' accelerations must differ, confirming the flag is
        // actually applied rather than incidentally negligible
        double m0 = 1.0e30;
        double m1 = 1.0e29;
        double m2 = 5.0e28;

        StateVector[] states = {
                restingAt(0, 0, 0),
                restingAt(1.0e11, 0, 0),
                restingAt(0, 1.0e11, 0)};

        Vector3D coupled = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(m0),
                        GravitationalBodyConfig.contributing(m1),
                        GravitationalBodyConfig.contributing(m2)})
                .accelerationsAt(states)[0];

        Vector3D restricted = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(m0),
                        GravitationalBodyConfig.contributing(m1),
                        GravitationalBodyConfig.nonContributing(m2)})
                .accelerationsAt(states)[0];

        assertNotEquals(coupled, restricted);
        assertTrue(coupled.magnitude() > restricted.magnitude());
    }

    // --- Arbitrary trial-state evaluation ---

    @Test
    void sameCalculatorEvaluatesIndependentlyAtDifferentStates() {
        // The property RK4 depends on: a fixed physical configuration
        // evaluated at arbitrary supplied states, retaining nothing between
        // calls (ADR-004)
        double m0 = PhysicalConstants.SOLAR_MASS;
        double m1 = PhysicalConstants.EARTH_MASS;

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(m0),
                        GravitationalBodyConfig.contributing(m1)});

        StateVector[] near = {restingAt(0, 0, 0), restingAt(1.0e11, 0, 0)};
        StateVector[] far = {restingAt(0, 0, 0), restingAt(2.0e11, 0, 0)};

        Vector3D nearAcceleration = calculator.accelerationsAt(near)[1];
        Vector3D farAcceleration = calculator.accelerationsAt(far)[1];

        // Inverse-square: doubling separation quarters the acceleration
        assertEquals(4.0,
                nearAcceleration.magnitude() / farAcceleration.magnitude(),
                RELATIVE_TOLERANCE);

        // Re-evaluating the first state reproduces the first result
        assertVectorEquals(nearAcceleration,
                calculator.accelerationsAt(near)[1],
                nearAcceleration.magnitude());
    }

    @Test
    void accelerationDependsOnPositionButNotVelocity() {
        // ADR-005 §4 requires acceleration to depend on position only. Exact
        // equality is asserted because the position inputs are bit-identical
        // between the two calls, so the computed results must be bit-identical
        // unless velocity were read.
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30),
                        GravitationalBodyConfig.contributing(1e24)});

        StateVector[] resting = {
                new StateVector(new Vector3D(0, 0, 0), Vector3D.ZERO),
                new StateVector(new Vector3D(1e11, 0, 0), Vector3D.ZERO)};

        StateVector[] moving = {
                new StateVector(new Vector3D(0, 0, 0), new Vector3D(1e6, -2e6, 3e6)),
                new StateVector(new Vector3D(1e11, 0, 0), new Vector3D(-4e6, 5e6, -6e6))};

        Vector3D[] atRest = calculator.accelerationsAt(resting);
        Vector3D[] inMotion = calculator.accelerationsAt(moving);

        assertEquals(atRest[0], inMotion[0]);
        assertEquals(atRest[1], inMotion[1]);
    }

    @Test
    void eachCallReturnsAFreshArray() {
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30),
                        GravitationalBodyConfig.contributing(1e24)});

        StateVector[] states = {restingAt(0, 0, 0), restingAt(1e11, 0, 0)};

        assertNotSame(calculator.accelerationsAt(states),
                calculator.accelerationsAt(states));
    }

    // --- Configuration immutability ---

    @Test
    void callerMutationOfConfigurationArrayDoesNotAffectCalculator() {
        GravitationalBodyConfig[] configs = {
                GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS)};

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(configs);

        StateVector[] states = {
                restingAt(0, 0, 0),
                restingAt(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0)};

        Vector3D before = calculator.accelerationsAt(states)[1];

        configs[0] = GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS * 1000);

        assertVectorEquals(before, calculator.accelerationsAt(states)[1], before.magnitude());
    }

    // --- AccelerationModel contract conformance ---

    @Test
    void implementsAccelerationModel() {
        assertInstanceOf(AccelerationModel.class, new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30)}));
    }

    @Test
    void returnedAccelerationsAreFinite() {
        // The AccelerationModel contract requires finite components throughout
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                        GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS)});

        Vector3D[] accelerations = calculator.accelerationsAt(new StateVector[]{
                restingAt(0, 0, 0),
                restingAt(PhysicalConstants.ASTRONOMICAL_UNIT, 0, 0)});

        for (Vector3D acceleration : accelerations) {
            assertTrue(Double.isFinite(acceleration.x()));
            assertTrue(Double.isFinite(acceleration.y()));
            assertTrue(Double.isFinite(acceleration.z()));
        }
    }

    @Test
    void extremeConfigurationsAreRejectedRatherThanReturningNonFiniteValues() {
        // The AccelerationModel contract requires finite returned components.
        // An extreme but individually representable configuration must be
        // rejected somewhere in the chain rather than yielding Infinity.
        //
        // In this configuration GravityCalculator rejects the contribution
        // first: it computes the unit-vector scale factor as
        // accelerationMagnitude / distance, i.e. G·M/r³, which overflows
        // before the contribution G·M/r² does. The exception therefore
        // originates from the pairwise guard, not from the accumulated-total
        // guard in this class (see requireFiniteTotal).
        double extremeMass = 1.0e300;

        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1.0e24),
                        GravitationalBodyConfig.contributing(extremeMass)});

        StateVector[] states = {
                restingAt(0, 0, 0),
                restingAt(8.2e-10, 0, 0)};

        assertThrows(IllegalArgumentException.class,
                () -> calculator.accelerationsAt(states));
    }

    @Test
    void nullConfigurationIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new NBodyAccelerationCalculator(null));
    }

    @Test
    void nullConfigurationElementIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30), null}));
    }

    @Test
    void invalidConfigurationMassIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GravitationalBodyConfig.contributing(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> GravitationalBodyConfig.contributing(-1e24));
        assertThrows(IllegalArgumentException.class,
                () -> GravitationalBodyConfig.nonContributing(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> GravitationalBodyConfig.nonContributing(Double.POSITIVE_INFINITY));
    }

    @Test
    void mismatchedStateCountIsRejected() {
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30),
                        GravitationalBodyConfig.contributing(1e24)});

        assertThrows(IllegalArgumentException.class,
                () -> calculator.accelerationsAt(new StateVector[]{restingAt(0, 0, 0)}));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.accelerationsAt(new StateVector[]{
                        restingAt(0, 0, 0), restingAt(1e11, 0, 0), restingAt(2e11, 0, 0)}));
    }

    @Test
    void nullStatesAreRejected() {
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30),
                        GravitationalBodyConfig.contributing(1e24)});

        assertThrows(NullPointerException.class,
                () -> calculator.accelerationsAt(null));
        assertThrows(NullPointerException.class,
                () -> calculator.accelerationsAt(new StateVector[]{restingAt(0, 0, 0), null}));
    }

    @Test
    void coincidentContributingBodiesAreRejected() {
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(1e30),
                        GravitationalBodyConfig.contributing(1e24)});

        StateVector same = restingAt(1e11, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> calculator.accelerationsAt(new StateVector[]{same, same}));
    }

    @Test
    void coincidentNonContributingBodiesAreNotEvaluated() {
        // Neither body is a source, so their mutual geometry is never passed
        // to GravityCalculator and no exception arises — matching the
        // documented contract for accelerationsAt
        NBodyAccelerationCalculator calculator = new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.nonContributing(1e30),
                        GravitationalBodyConfig.nonContributing(1e24)});

        StateVector same = restingAt(1e11, 0, 0);
        Vector3D[] accelerations = calculator.accelerationsAt(new StateVector[]{same, same});

        assertEquals(Vector3D.ZERO, accelerations[0]);
        assertEquals(Vector3D.ZERO, accelerations[1]);
    }
}