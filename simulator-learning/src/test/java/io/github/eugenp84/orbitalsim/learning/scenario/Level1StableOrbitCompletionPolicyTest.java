package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.function.Executable;


class Level1StableOrbitCompletionPolicyTest
{
    private static final String STAR = "star-1";
    private static final String PLANET = "planet-1";

    private static final double STAR_MASS = PhysicalConstants.SOLAR_MASS;
    private static final double PLANET_MASS = PhysicalConstants.EARTH_MASS;
    private static final double TOTAL_MASS = STAR_MASS + PLANET_MASS;

    private static final double MU = PhysicalConstants.GRAVITATIONAL_CONSTANT * TOTAL_MASS;
    private static final double AU = PhysicalConstants.ASTRONOMICAL_UNIT;

    private static final int PROGRADE = 1;
    private static final int RETROGRADE = -1;

    /**
     * A cadence that comfortably satisfies the construction sampling check for the orbits used here (≈365 observations per revolution at 1 AU).
     * <p>
     * These tests drive the policy directly with analytically generated states, not through a session, so the cadence's only role is to satisfy construction validation;
     * ! --- the angular spacing of the fed observations is controlled separately by each test !!!
     */
    private static final ScenarioObservationCadence CADENCE =
            new ScenarioObservationCadence(86_400.0, 1);

    private static final Level1Criteria CRITERIA = new Level1Criteria(
            2, // requiredRevolutions
            0.1, // maximumEccentricity
            0.0,  // minimumSurfaceClearanceMetres
            0.01); // maximumRelativeEnergyDrift

    // ------------- Fixtures ------======================================--

    private static CelestialBody star()
    {
        return new CelestialBody(STAR, "Star", CelestialBodyType.STAR,
                STAR_MASS, PhysicalConstants.SOLAR_RADIUS);
    }

    private static CelestialBody planet()
    {
        return new CelestialBody(PLANET, "Planet", CelestialBodyType.PLANET,
                PLANET_MASS, PhysicalConstants.EARTH_RADIUS);
    }

    /**
     * Builds a snapshot in the centre-of-mass frame from a relative state, as Level 1 scenario invariant require:
     * the bodies sit either side of the origin in inverse mass proportion,
     * so total momentum is zero and the total mechanical energy carries no bulk kinetic term.
     */
    private static SimulationSnapshot centreOfMassSnapshot(
            Vector3D relativePosition, Vector3D relativeVelocity)
    {
        double starFraction = -PLANET_MASS / TOTAL_MASS;
        double planetFraction = STAR_MASS / TOTAL_MASS;

        return new SimulationSnapshot(0.0, 0L, List.of(
                new BodySnapshot(star(), new StateVector(relativePosition.multiply(starFraction), relativeVelocity.multiply(starFraction))),
                new BodySnapshot(planet(), new StateVector(relativePosition.multiply(planetFraction), relativeVelocity.multiply(planetFraction)))));
    } // end of centreOfMassSnapshot() -----------------------------------------------------------------

    /**
     * An exact Kepler state at the given true anomaly, in perifocal orientation.
     * Generating states analytically rather than by integration keeps these tests about the policy's logic, independent of any integrator's accuracy.
     *
     * @param direction {@link #PROGRADE} or {@link #RETROGRADE};
     *        reversing the velocity flips the angular momentum while leaving eccentricity and energy unchanged, giving a genuinely retrograde orbit rather than a prograde one visited in reverse index order
     */
    private static SimulationSnapshot orbitAt(double semiMajorAxis, double eccentricity, double trueAnomalyRadians, int direction)
    {
        double p = semiMajorAxis * (1.0 - eccentricity * eccentricity);
        double radius = p / (1.0 + eccentricity * Math.cos(trueAnomalyRadians));

        double speedScale = Math.sqrt(MU / p);

        Vector3D position = new Vector3D(radius * Math.cos(trueAnomalyRadians),radius * Math.sin(trueAnomalyRadians),0.0);
        Vector3D progradeVelocity = new Vector3D(-speedScale * Math.sin(trueAnomalyRadians), speedScale * (eccentricity + Math.cos(trueAnomalyRadians)),0.0);

        return centreOfMassSnapshot(position, progradeVelocity.multiply(direction));
    }//--------OK !!! -----------------------------==================================

    private static SimulationSnapshot orbitAt(double semiMajorAxis, double eccentricity, double trueAnomalyRadians)
    {
        return orbitAt(semiMajorAxis, eccentricity, trueAnomalyRadians, PROGRADE);
    }

    /** A circular state at 1 AU whose relative speed is scaled by a factor. */
    private static SimulationSnapshot circularWithScaledSpeed(double factor)
    {
        double speed = Math.sqrt(MU / AU) * factor;
        return centreOfMassSnapshot(new Vector3D(AU, 0, 0), new Vector3D(0, speed, 0));
    }

    private static ScenarioObservation observation(SimulationSnapshot snapshot, int index)
    {
        // The policy does not read elapsed time!!!
        // a monotonic value is supplied only to satisfy ScenarioObservation's own contract, and kept monotonic so the sequence is one a session could legitimately produce.
        return new ScenarioObservation(snapshot, index * CADENCE.intervalSeconds());
    }

    private static Level1StableOrbitCompletionPolicy policyFor(SimulationSnapshot initial) {
        return new Level1StableOrbitCompletionPolicy(
                initial, STAR, PLANET, MU, CADENCE, CRITERIA);
    }

    /**
     * Feeds states around the orbit at a fixed true-anomaly step over the given inclusive step range, returning the first terminal evaluation or CONTINUE
     * if none occurred. The range is explicit so successive calls can continue a sequence rather than restarting it.
     */
    private static ScenarioEvaluation feedOrbit(
            Level1StableOrbitCompletionPolicy policy,
            double semiMajorAxis, double eccentricity,
            double stepDegrees, int firstStep, int lastStep)
    {

        for (int i = firstStep; i <= lastStep; i++)
        {
            ScenarioEvaluation evaluation = policy.evaluate(observation(orbitAt(semiMajorAxis, eccentricity, Math.toRadians(i * stepDegrees)), i));
            if (evaluation != ScenarioEvaluation.CONTINUE)
            {
                return evaluation;
            }
        }//end for loop=============================
        return ScenarioEvaluation.CONTINUE;
    }

    /**
     * Asserts a configuration or argument defect: it fails as an {@link IllegalArgumentException},
     * but is specifically not the assessability refusal, which the controller layer catches.
     * This tests the semantic boundary the production code relies on.
     */
    private static void assertConfigurationFailure(final Executable action)
    {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertFalse(exception instanceof ScenarioAttemptNotAssessableException);
    }

    // ----------------- Construction ------------------------------------------------------------



    @Test
    void constructionValidatesItsArguments() {
        SimulationSnapshot initial = orbitAt(AU, 0.0, 0.0);

        assertThrows(NullPointerException.class, () -> new Level1StableOrbitCompletionPolicy(
                null, STAR, PLANET, MU, CADENCE, CRITERIA));
        assertThrows(NullPointerException.class, () -> new Level1StableOrbitCompletionPolicy(
                initial, null, PLANET, MU, CADENCE, CRITERIA));
        assertThrows(NullPointerException.class, () -> new Level1StableOrbitCompletionPolicy(
                initial, STAR, PLANET, MU, null, CRITERIA));
        assertThrows(NullPointerException.class, () -> new Level1StableOrbitCompletionPolicy(
                initial, STAR, PLANET, MU, CADENCE, null));

        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(
                initial, "  ", PLANET, MU, CADENCE, CRITERIA));
        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(
                initial, STAR, STAR, MU, CADENCE, CRITERIA));
        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(
                initial, STAR, PLANET, 0.0, CADENCE, CRITERIA));
        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(
                initial, STAR, PLANET, Double.NaN, CADENCE, CRITERIA));
    }

    @Test
    void constructionRejectsAnUnresolvableBody() {
        SimulationSnapshot initial = orbitAt(AU, 0.0, 0.0);

        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(
                initial, "no-such-star", PLANET, MU, CADENCE, CRITERIA));
        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(
                initial, STAR, "no-such-planet", MU, CADENCE, CRITERIA));
    }

    @Test
    void constructionRejectsACadenceTooCoarseToCountRevolutions() {
        // ~1.58 observations per revolution at 1 AU: below the ambiguity
        // boundary of 2, so unwrapping could not distinguish forward motion
        // from reversal and the attempt was never assessable.
        ScenarioObservationCadence coarse = new ScenarioObservationCadence(1.0e7, 2);

        assertThrowsExactly(ScenarioAttemptNotAssessableException.class,
                () -> new Level1StableOrbitCompletionPolicy(
                        orbitAt(AU, 0.0, 0.0), STAR, PLANET, MU, coarse, CRITERIA));
    }

    @Test
    void samplingRequirementDependsOnTheActualInitialEccentricity() {
        // 4 samples per revolution: above the circular bound of 2, below the 6.93 an e = 0.5 orbit needs.
        // A regression that hard-coded the floor at 2, or derived it from the passing criterion instead of the learner's own orbit, should accept the second case.
        double circularPeriod = 2.0 * Math.PI * AU * Math.sqrt(AU / MU);
        ScenarioObservationCadence fourPerOrbit = new ScenarioObservationCadence(circularPeriod / 4.0, 1);

        assertDoesNotThrow(() -> new Level1StableOrbitCompletionPolicy(orbitAt(AU, 0.0, 0.0), STAR, PLANET, MU, fourPerOrbit, CRITERIA));

        assertThrowsExactly(ScenarioAttemptNotAssessableException.class,
                () -> new Level1StableOrbitCompletionPolicy(
                        orbitAt(AU, 0.5, 0.0), STAR, PLANET, MU, fourPerOrbit, CRITERIA));
    }

    @Test
    void constructionRejectsAStateWithNoDefinedAzimuth() {
        // On the reference-plane normal the projection vanishes, so there is no angular coordinate from which to count revolutions.
        SimulationSnapshot onTheNormal = centreOfMassSnapshot(new Vector3D(0, 0, AU), new Vector3D(Math.sqrt(MU / AU), 0, 0));

        assertConfigurationFailure(() -> new Level1StableOrbitCompletionPolicy(onTheNormal, STAR, PLANET, MU, CADENCE, CRITERIA));
    }

    @Test
    void anUnboundStartIsAcceptedAtConstructionBecauseItHasNoPeriodToSample() {
        // The deliberate asymmetry: an unbound attempt is decided by the escape test alone, it needs no revolution counting and is allowed to start, then fails on its first observation.
        SimulationSnapshot hyperbolic = circularWithScaledSpeed(1.5);

        Level1StableOrbitCompletionPolicy policy = assertDoesNotThrow(
                () -> policyFor(hyperbolic));

        assertEquals(ScenarioEvaluation.FAIL, policy.evaluate(observation(hyperbolic, 1)));
    }

    // ---======================= COMPLETION ==========================================---

    @Test
    void aCircularOrbitCompletesAfterTheRequiredRevolutions()
    {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        // 10-degree steps; two revolutions is 72 steps, so 75 gives margin without relying on an exact floating-point landing on 4pi.
        assertEquals(ScenarioEvaluation.COMPLETE, feedOrbit(policy, AU, 0.0, 10.0, 1, 75));
        assertTrue(policy.completedRevolutions() >= 2.0);
    }

    @Test
    void aCircularOrbitDoesNotCompleteBeforeTheRequiredRevolutions() {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        // 71 steps of 10 degrees is 710 degrees, just short of two revolutions
        assertEquals(ScenarioEvaluation.CONTINUE, feedOrbit(policy, AU, 0.0, 10.0, 1, 71));
        assertTrue(policy.completedRevolutions() < 2.0);
    }

    @Test
    void anOverEccentricOrbitRunsToTheCheckpointAndOnlyThenFails() {
        // e = 0.3 exceeds the 0.1 passing criterion, but eccentricity is summative.
        // the attempt runs, and is failed at the checkpoint!
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.3, 0.0));

        assertEquals(ScenarioEvaluation.CONTINUE, feedOrbit(policy, AU, 0.3, 10.0, 1, 71),
                "an over-eccentric orbit must not fail before the checkpoint");

        assertEquals(ScenarioEvaluation.FAIL, feedOrbit(policy, AU, 0.3, 10.0, 72, 75),
                "and must fail once the revolution target is reached");
    }

    @Test
    void anEccentricButAcceptableOrbitCompletes() {
        // e = 0.05 is within the passing criterion, so a slightly elliptical orbit is a valid answer!!!
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.05, 0.0));

        assertEquals(ScenarioEvaluation.COMPLETE, feedOrbit(policy, AU, 0.05, 10.0, 1, 75));
    }

    // ---============== Learner-facing failures ============================================---

    @Test
    void aClearanceViolationFailsImmediately() {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        // Separation below the sum of the two radii: the surfaces have met.
        SimulationSnapshot impact = centreOfMassSnapshot(
                new Vector3D(PhysicalConstants.SOLAR_RADIUS * 0.5, 0, 0),
                new Vector3D(0, 1.0e4, 0));

        assertEquals(ScenarioEvaluation.FAIL, policy.evaluate(observation(impact, 1)));
    }

    @Test
    void aSubsequentlyUnboundOrbitFails() {
        // Deliberately tolerant of drift, to isolate the escape branch from integrity check that would otherwise intercept this state.
        Level1Criteria tolerantOfDrift = new Level1Criteria(2, 0.1, 0.0, 10.0);
        Level1StableOrbitCompletionPolicy policy = new Level1StableOrbitCompletionPolicy(
                orbitAt(AU, 0.0, 0.0), STAR, PLANET, MU, CADENCE, tolerantOfDrift);

        assertEquals(ScenarioEvaluation.FAIL,
                policy.evaluate(observation(circularWithScaledSpeed(1.5), 1)));
    }

    // ---==================== Assessment integrity =================================---

    @Test
    void excessiveEnergyDriftMakesTheAttemptUnassessableRatherThanFailed()
    {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        // Relative speed x1.2: still bound, but ~44% energy drift against a
        // 1% threshold. This is evidence about the integrator, not the learner.
        assertEquals(ScenarioEvaluation.ASSESSMENT_INVALID,
                policy.evaluate(observation(circularWithScaledSpeed(1.2), 1)));
    }// --------- OK ! --------------------------------------------

    @Test
    void integrityIsCheckedBeforeTheEscapeInference() {
        // The ordering proof. Relative speed x1.5 makes the state BOTH excessively drifted (~125%) AND unbound.
        // If the escape test ran first the learner should be told their orbit escaped, on the strength of an energy the integrity check has not vouched for.
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        assertEquals(ScenarioEvaluation.ASSESSMENT_INVALID,
                policy.evaluate(observation(circularWithScaledSpeed(1.5), 1)),
                "a drifted state must not yield a learner-facing escape verdict");
    }

    @Test
    void clearanceIsCheckedBeforeIntegrity() {
        // Near an impact the fixed timestep struggles and drift spikes, so an
        // integrity-first order would report a genuine crash as unassessable.
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        SimulationSnapshot crashing = centreOfMassSnapshot(
                new Vector3D(PhysicalConstants.SOLAR_RADIUS * 0.5, 0, 0),
                new Vector3D(0, 3.0e5, 0));

        assertEquals(ScenarioEvaluation.FAIL, policy.evaluate(observation(crashing, 1)),
                "a collision is a learner outcome, not an assessment failure");
    }

    @Test
    void lossOfTheReferencePlaneAzimuthMakesAssessmentInvalid() {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        // Covers the runtime branch where the observable used for revolution counting ceases to exist.
        SimulationSnapshot undefinedAzimuth = centreOfMassSnapshot(
                new Vector3D(0, 0, AU), new Vector3D(Math.sqrt(MU / AU), 0, 0));

        assertEquals(ScenarioEvaluation.ASSESSMENT_INVALID,
                policy.evaluate(observation(undefinedAzimuth, 1)));
    }

    // --------------- Revolution accounting --------------------------------------

    @Test
    void aRetrogradeOrbitCountsAsProgress() {
        // A genuinely retrograde orbit = the velocity is reversed, so angular momentum points the other way while eccentricity and energy are
        // unchanged.
        // Direction is irrelevant to "has the learner sustained an orbit".
        Level1StableOrbitCompletionPolicy policy =
                policyFor(orbitAt(AU, 0.0, 0.0, RETROGRADE));

        for (int i = 1; i <= 75; i++) {
            policy.evaluate(observation(
                    orbitAt(AU, 0.0, Math.toRadians(-i * 10.0), RETROGRADE), i));
        }

        assertTrue(policy.accumulatedAngularDisplacement() < 0.0, "displacement is signed");
        assertTrue(policy.completedRevolutions() >= 2.0, "magnitude counts as progress");
    }

    @Test
    void aReversalCancelsRatherThanAccumulating() {
        // Signed accumulation, so a body swinging back and forth never reaches the revolution target by oscillating.
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        int index = 1;
        for (int i = 1; i <= 18; i++) {          // forward 180 degrees
            policy.evaluate(observation(orbitAt(AU, 0.0, Math.toRadians(i * 10.0)), index++));
        }
        for (int i = 17; i >= 0; i--) {          // and back again
            policy.evaluate(observation(orbitAt(AU, 0.0, Math.toRadians(i * 10.0)), index++));
        }

        assertEquals(0.0, policy.accumulatedAngularDisplacement(), 1e-9);
        assertEquals(0.0, policy.completedRevolutions(), 1e-9);
    }

    @Test
    void anUnstartedPolicyHasNoAccumulatedProgress() {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));

        assertEquals(0.0, policy.accumulatedAngularDisplacement());
        assertEquals(0.0, policy.completedRevolutions());
    }

    @Test
    void evaluateRejectsANullObservation()
    {
        Level1StableOrbitCompletionPolicy policy = policyFor(orbitAt(AU, 0.0, 0.0));
        assertThrows(NullPointerException.class, () -> policy.evaluate(null));
    }


}