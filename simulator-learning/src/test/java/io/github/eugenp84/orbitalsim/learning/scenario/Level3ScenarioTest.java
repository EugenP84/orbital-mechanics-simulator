package io.github.eugenp84.orbitalsim.learning.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.eugenp84.orbitalsim.core.integrator.VelocityVerletIntegrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateCalculator;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationEngine;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.eugenp84.orbitalsim.core.simulation.AdvanceResult;

/**
 * Headless tests for {@link Level3Scenario}, exercised through the real {@link ScenarioSession}. The
 * two full-horizon tests run the production six-year experiment end to end; the rest are cheap.
 * Collision failures are exercised by evaluating the policy against contrived overlapping snapshots,
 * because the calibrated learner envelope never approaches surface contact.
 *
 * @author Eugeniu PLATITA
 */
class Level3ScenarioTest
{
    private static final double JUPITER_MASS = 317.8 * PhysicalConstants.EARTH_MASS;
    private static final double AU = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double SIX_YEARS_SECONDS = 6.0 * 365.25 * 86_400.0;

    private static final double WEAK_MASS = 0.3 * JUPITER_MASS;
    private static final double WEAK_DISTANCE = 2.0 * AU;
    private static final double STRONG_MASS = 3.0 * JUPITER_MASS;
    private static final double STRONG_DISTANCE = 1.3 * AU;

    private final Level3Scenario scenario = new Level3Scenario();

    /** Drives a session in whole observation intervals until it terminates or the guard is reached. */
    private static ScenarioLifecycleState runToTermination(final ScenarioSession session)
    {
        session.start();
        final double interval = session.observationIntervalSeconds();
        // A generous guard: enough intervals to reach the six-year horizon plus margin.
        final long maxObservations = Math.round(SIX_YEARS_SECONDS / interval) + 16;
        for (long i = 0; i < maxObservations; i++)
        {
            if (session.state() != ScenarioLifecycleState.RUNNING)
            {
                return session.state();
            }
            session.advanceBy(interval);
        }
        return session.state();
    }

    @Test
    void aWeakEnvelopeAttemptCompletesThroughTheRealSessionAtTheHorizon()
    {
        final Level3Attempt attempt = scenario.createAttempt(WEAK_MASS, WEAK_DISTANCE);
        assertEquals(ScenarioLifecycleState.NOT_STARTED, attempt.session().state());

        final ScenarioLifecycleState terminal = runToTermination(attempt.session());

        assertEquals(ScenarioLifecycleState.COMPLETED, terminal);
        assertEquals(java.util.Optional.of(ScenarioTerminationReason.POLICY_COMPLETION),
                attempt.session().terminationReason());
    }

    @Test
    void aStrongerPerturberProducesAGreaterMaximumTargetEccentricity()
    {
        // The core learning claim, asserted as a relationship (not calibration golden values): a more
        // massive, closer perturber perturbs the target's orbit more strongly over the same horizon.
        final Level3Attempt weak = scenario.createAttempt(WEAK_MASS, WEAK_DISTANCE);
        final Level3Attempt strong = scenario.createAttempt(STRONG_MASS, STRONG_DISTANCE);

        assertEquals(ScenarioLifecycleState.COMPLETED, runToTermination(weak.session()));
        assertEquals(ScenarioLifecycleState.COMPLETED, runToTermination(strong.session()));

        assertTrue(strong.progressSource().maxEccentricity() > weak.progressSource().maxEccentricity());
    }

    @Test
    void theTargetOrbitIsMeasurablyPerturbedRelativeToAnUnperturbedReference()
    {
        // Observable-physics claim, narrowly worded: with the perturber present the target's orbit is
        // perturbed beyond the near-zero eccentricity of the same star-target pair run WITHOUT a
        // perturber. This does not independently assert that every gravitational body contributes.
        final Level3Attempt perturbed = scenario.createAttempt(STRONG_MASS, STRONG_DISTANCE);
        assertEquals(ScenarioLifecycleState.COMPLETED, runToTermination(perturbed.session()));

        final double referenceMaxE = unperturbedReferenceMaxEccentricity();

        assertTrue(perturbed.progressSource().maxEccentricity() > referenceMaxE + 1.0e-3);
    }

    @Test
    void maxEccentricityIsSeededToTheInitialValueBeforeAnyObservation()
    {
        final Level3Attempt attempt = scenario.createAttempt(STRONG_MASS, STRONG_DISTANCE);
        assertEquals(ScenarioLifecycleState.NOT_STARTED, attempt.session().state());

        // The contract: before start(), maxEccentricity() equals the initial osculating eccentricity
        // derived from the installed star/target state — not an artificial 0.0. Both sides use the
        // same immutable snapshot and the same deterministic calculation, so they are bit-identical.
        final SimulationSnapshot snapshot = attempt.session().snapshot();
        final double expected = referenceEccentricity(snapshot, attempt.starId(),
                attempt.targetId(), earthSunMu());
        assertEquals(expected, attempt.progressSource().maxEccentricity());
    }

    @Test
    void maxEccentricityNeverDecreasesAcrossSuccessiveObservations()
    {
        final Level3Attempt attempt = scenario.createAttempt(STRONG_MASS, STRONG_DISTANCE);
        final ScenarioSession session = attempt.session();
        session.start();
        final double interval = session.observationIntervalSeconds();

        double previous = attempt.progressSource().maxEccentricity();
        // Several successive observation boundaries: e_max,n = max(e_max,n-1, e_n) must not decrease.
        for (int i = 0; i < 50 && session.state() == ScenarioLifecycleState.RUNNING; i++)
        {
            session.advanceBy(interval);
            final double current = attempt.progressSource().maxEccentricity();
            assertTrue(current >= previous);
            previous = current;
        }
    }

    @Test
    void aFreshAttemptExposesItsThreeConfiguredBodies()
    {
        final Level3Attempt attempt = scenario.createAttempt(STRONG_MASS, STRONG_DISTANCE);
        final SimulationSnapshot snapshot = attempt.session().snapshot();

        assertTrue(containsBody(snapshot, attempt.starId()));
        assertTrue(containsBody(snapshot, attempt.targetId()));
        assertTrue(containsBody(snapshot, attempt.perturberId()));
        assertEquals(3, snapshot.bodies().size());
    }

    @Test
    void createAttemptRejectsMassOrDistanceOutsideTheEnvelope()
    {
        final double belowMass = 0.29 * JUPITER_MASS;
        final double aboveMass = 3.01 * JUPITER_MASS;
        final double belowDist = 1.29 * AU;
        final double aboveDist = 2.01 * AU;

        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(belowMass, 1.5 * AU));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(aboveMass, 1.5 * AU));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(JUPITER_MASS, belowDist));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(JUPITER_MASS, aboveDist));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(Double.NaN, 1.5 * AU));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(JUPITER_MASS, Double.POSITIVE_INFINITY));
    }

    @Test
    void createAttemptAcceptsTheEnvelopeBounds()
    {
        final double mu = PhysicalConstants.GRAVITATIONAL_CONSTANT
                * (PhysicalConstants.SOLAR_MASS + PhysicalConstants.EARTH_MASS);
        // Bounds must build (they are used to seed the policy; the constructor must not reject them).
        assertTrue(scenario.createAttempt(scenario.minPerturberMassKilograms(),
                scenario.minPerturberDistanceMetres()).progressSource().maxEccentricity() >= 0.0);
        assertTrue(scenario.createAttempt(scenario.maxPerturberMassKilograms(),
                scenario.maxPerturberDistanceMetres()).progressSource().maxEccentricity() >= 0.0);
        assertTrue(mu > 0.0);
    }

    @Test
    void thePolicyFailsWhenTheTargetContactsTheStar()
    {
        final Level3CompletionPolicy policy = new Level3CompletionPolicy(
                threeBodySnapshotFarApart(), "s", "t", "p", earthSunMu(), SIX_YEARS_SECONDS);

        // Contrived: target centre placed within (R_target + R_star) of the star.
        final double touching = PhysicalConstants.SOLAR_RADIUS + PhysicalConstants.EARTH_RADIUS - 1.0;
        final SimulationSnapshot contact = snapshotOf(
                bodyAt("s", CelestialBodyType.STAR, PhysicalConstants.SOLAR_MASS,
                        PhysicalConstants.SOLAR_RADIUS, new Vector3D(0, 0, 0)),
                bodyAt("t", CelestialBodyType.PLANET, PhysicalConstants.EARTH_MASS,
                        PhysicalConstants.EARTH_RADIUS, new Vector3D(touching, 0, 0)),
                bodyAt("p", CelestialBodyType.PLANET, JUPITER_MASS, 7.1492e7,
                        new Vector3D(0, 5.0 * AU, 0)));

        assertEquals(ScenarioEvaluation.FAIL,
                policy.evaluate(new ScenarioObservation(contact, 0.0)));
    }

    @Test
    void thePolicyFailsWhenTheTargetContactsThePerturber()
    {
        final Level3CompletionPolicy policy = new Level3CompletionPolicy(
                threeBodySnapshotFarApart(), "s", "t", "p", earthSunMu(), SIX_YEARS_SECONDS);

        // Contrived: target centre placed within (R_target + R_perturber) of the perturber, both far
        // from the star so the star-contact branch is not what fails.
        final double perturberRadius = 7.1492e7;
        final double touching = perturberRadius + PhysicalConstants.EARTH_RADIUS - 1.0;
        final SimulationSnapshot contact = snapshotOf(
                bodyAt("s", CelestialBodyType.STAR, PhysicalConstants.SOLAR_MASS,
                        PhysicalConstants.SOLAR_RADIUS, new Vector3D(0, 0, 0)),
                bodyAt("t", CelestialBodyType.PLANET, PhysicalConstants.EARTH_MASS,
                        PhysicalConstants.EARTH_RADIUS, new Vector3D(3.0 * AU + touching, 0, 0)),
                bodyAt("p", CelestialBodyType.PLANET, JUPITER_MASS, perturberRadius,
                        new Vector3D(3.0 * AU, 0, 0)));

        assertEquals(ScenarioEvaluation.FAIL,
                policy.evaluate(new ScenarioObservation(contact, 0.0)));
    }

    @Test
    void level3AttemptRejectsNonDistinctBodyIds()
    {
        final ScenarioSession session =
                scenario.createAttempt(STRONG_MASS, STRONG_DISTANCE).session();
        final Level3ProgressSource source = () -> 0.0;
        assertThrows(IllegalArgumentException.class,
                () -> new Level3Attempt(session, source, "same", "same", "p", 1.0, 1.0));
    }

    private static boolean containsBody(final SimulationSnapshot snapshot, final String id)
    {
        return snapshot.bodies().stream().anyMatch(b -> b.definition().id().equals(id));
    }

    private static double earthSunMu()
    {
        return PhysicalConstants.GRAVITATIONAL_CONSTANT
                * (PhysicalConstants.SOLAR_MASS + PhysicalConstants.EARTH_MASS);
    }

    private static BodySnapshot bodyAt(final String id, final CelestialBodyType type,
                                       final double mass, final double radius, final Vector3D position)
    {
        return new BodySnapshot(
                new CelestialBody(id, id, type, mass, radius),
                new StateVector(position, new Vector3D(0, 0, 0)));
    }

    private static SimulationSnapshot snapshotOf(final BodySnapshot... bodies)
    {
        return new SimulationSnapshot(0.0, 0L, List.of(bodies));
    }

    private static SimulationSnapshot threeBodySnapshotFarApart()
    {
        // A well-separated initial snapshot only to satisfy the policy constructor's baseline seed.
        return snapshotOf(
                bodyAt("s", CelestialBodyType.STAR, PhysicalConstants.SOLAR_MASS,
                        PhysicalConstants.SOLAR_RADIUS, new Vector3D(0, 0, 0)),
                bodyAt("t", CelestialBodyType.PLANET, PhysicalConstants.EARTH_MASS,
                        PhysicalConstants.EARTH_RADIUS, new Vector3D(AU, 0, 0)),
                bodyAt("p", CelestialBodyType.PLANET, JUPITER_MASS, 7.1492e7,
                        new Vector3D(0, 1.5 * AU, 0)));
    }

    private static double unperturbedReferenceMaxEccentricity()
    {
        // Sun + Earth-like target using the SAME near-circular L3 target initialisation (v = sqrt(G*M_star/r)),
        // but with NO perturbing planet, driven the same way. This isolates the independent variable as
        // the presence of the perturber rather than a different target initial velocity. Used only to
        // establish a near-zero baseline the perturbed run must exceed.
        final double mu = earthSunMu();
        final double v = Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT
                * PhysicalConstants.SOLAR_MASS / AU);

        // Centre-of-mass frame for the two bodies.
        final double totalMass = PhysicalConstants.SOLAR_MASS + PhysicalConstants.EARTH_MASS;
        final double starFraction = -PhysicalConstants.EARTH_MASS / totalMass;
        final double targetFraction = PhysicalConstants.SOLAR_MASS / totalMass;
        final Vector3D relPos = new Vector3D(AU, 0, 0);
        final Vector3D relVel = new Vector3D(0, v, 0);

        final List<BodySnapshot> bodies = List.of(
                new BodySnapshot(
                        new CelestialBody("s", "Star", CelestialBodyType.STAR,
                                PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS),
                        new StateVector(relPos.multiply(starFraction), relVel.multiply(starFraction))),
                new BodySnapshot(
                        new CelestialBody("t", "Target", CelestialBodyType.PLANET,
                                PhysicalConstants.EARTH_MASS, PhysicalConstants.EARTH_RADIUS),
                        new StateVector(relPos.multiply(targetFraction), relVel.multiply(targetFraction))));

        final SimulationEngine engine = new SimulationEngine(
                bodies,
                new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                        GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS)}),
                21_600.0,
                new VelocityVerletIntegrator());

        double maxE = referenceEccentricity(engine.snapshot(), mu);
        final long steps = Math.round(SIX_YEARS_SECONDS / 21_600.0);
        for (long i = 0; i < steps; i++)
        {
            final AdvanceResult result = engine.advanceBy(21_600.0);
            assertFalse(result.brokeDown());
            maxE = Math.max(maxE, referenceEccentricity(result.snapshot(), mu));
        }
        return maxE;
    }

    private static double referenceEccentricity(final SimulationSnapshot snapshot,
                                                final String starId, final String targetId, final double mu)
    {
        final BodySnapshot star = snapshot.bodies().stream()
                .filter(b -> b.definition().id().equals(starId)).findFirst().orElseThrow();
        final BodySnapshot target = snapshot.bodies().stream()
                .filter(b -> b.definition().id().equals(targetId)).findFirst().orElseThrow();
        return OrbitalStateCalculator.analyse(
                target.state().position().subtract(star.state().position()),
                target.state().velocity().subtract(star.state().velocity()),
                mu).eccentricity();
    }

    private static double referenceEccentricity(final SimulationSnapshot snapshot, final double mu)
    {
        return referenceEccentricity(snapshot, "s", "t", mu);
    }
}