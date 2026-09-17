package io.github.eugenp84.orbitalsim.learning.scenario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;

/**
 * Headless tests for the canonical Level 4 Apophis close-approach scenario.
 * <p>
 * The fixture values below are the verified one-day-before JPL Horizons Earth and Apophis vectors, converted from km and km/s to SI units exactly as the production data loader does.
 * These tests do not duplicate CNEOS validation; that remains in simulator-evaluation.
 *
 * @author Eugeniu PLATITA
 */
class Level4ScenarioTest
{
    private static final double INITIAL_JD_TDB = 2_462_239.407091595;
    private static final double HORIZON_SECONDS = 172_800.0;

    private final Level4Scenario scenario = new Level4Scenario();

    @Test
    void aCanonicalAttemptCompletesAtTheTwoDayHorizon()
    {
        final Level4Attempt attempt = scenario.createAttempt(canonicalInitialConditions());
        final ScenarioSession session = attempt.session();
        session.start();

        while (session.state() == ScenarioLifecycleState.RUNNING)
        {
            session.advanceBy(session.observationIntervalSeconds());
        }

        assertEquals(ScenarioLifecycleState.COMPLETED, session.state());
        assertEquals(java.util.Optional.of(ScenarioTerminationReason.POLICY_COMPLETION), session.terminationReason());
        assertEquals(HORIZON_SECONDS, session.elapsedScenarioSeconds());
    }

    @Test
    void aFreshAttemptContainsSunEarthAndApophis()
    {
        final Level4Attempt attempt = scenario.createAttempt(canonicalInitialConditions());
        final SimulationSnapshot snapshot = attempt.session().snapshot();
        assertEquals(3, snapshot.bodies().size());
        assertTrue(containsBody(snapshot, attempt.sunId()));
        assertTrue(containsBody(snapshot, attempt.earthId()));
        assertTrue(containsBody(snapshot, attempt.apophisId()));
    }

    @Test
    void closestApproachMeasurementIsSeededBeforeTheSessionStarts()
    {
        final Level4Attempt attempt = scenario.createAttempt(canonicalInitialConditions());
        final SimulationSnapshot snapshot = attempt.session().snapshot();
        final double expectedDistance = distanceBetween(
                snapshot, attempt.earthId(), attempt.apophisId());
        final double expectedSpeed = relativeSpeedBetween(
                snapshot, attempt.earthId(), attempt.apophisId());

        assertEquals(expectedDistance, attempt.progressSource().currentDistanceMetres());
        assertEquals(expectedDistance, attempt.progressSource().minimumDistanceMetres());
        assertEquals(0.0, attempt.progressSource().minimumDistanceElapsedSeconds());
        assertEquals(expectedSpeed, attempt.progressSource().relativeSpeedAtMinimumMetresPerSecond());
    }

    @Test
    void minimumDistanceNeverIncreasesAcrossSuccessiveObservations()
    {
        final Level4Attempt attempt = scenario.createAttempt(canonicalInitialConditions());
        final ScenarioSession session = attempt.session();
        session.start();
        final double interval = session.observationIntervalSeconds();
        double previous = attempt.progressSource().minimumDistanceMetres();

        for (int i = 0; i < 100 && session.state() == ScenarioLifecycleState.RUNNING; i++)
        {
            session.advanceBy(interval);
            final double current = attempt.progressSource().minimumDistanceMetres();
            assertTrue(current <= previous);
            previous = current;
        }
    }

    @Test
    void scenarioRejectsAStateFromAnotherCalibrationEpoch()
    {
        final Level4InitialConditions canonical = canonicalInitialConditions();
        final Level4InitialConditions wrongEpoch = new Level4InitialConditions(
                canonical.apophisState(), canonical.earthState(), INITIAL_JD_TDB - 1.0);

        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(wrongEpoch));
    }

    @Test
    void timestepAndInitialEpochExposeTheFrozenProductionConfiguration()
    {
        assertEquals(INITIAL_JD_TDB, scenario.initialEpochJdTdb());
        assertEquals(112.5, scenario.timeStepSeconds());
    }

    private static Level4InitialConditions canonicalInitialConditions()
    {
        final StateVector apophis = new StateVector(
                new Vector3D(
                        -1.386256313085135e11,
                        -5.856612637608582e10,
                        -1.386547728258595e8),
                new Vector3D(
                        1.534054503159745e4,
                        -2.372457828978432e4,
                        1.664942291305919e3));
        final StateVector earth = new StateVector(
                new Vector3D(
                        -1.382162456064592e11,
                        -5.825895653945697e10,
                        4.268419239073992e6),
                new Vector3D(
                        1.108839570149948e4,
                        -2.757278429027363e4,
                        1.868164283823859));
        return new Level4InitialConditions(apophis, earth, INITIAL_JD_TDB);
    }

    private static boolean containsBody(final SimulationSnapshot snapshot, final String id)
    {
        return snapshot.bodies().stream().anyMatch(body -> body.definition().id().equals(id));
    }

    private static double distanceBetween(
            final SimulationSnapshot snapshot, final String firstId, final String secondId)
    {
        final Vector3D first = snapshot.bodies().stream()
                .filter(body -> body.definition().id().equals(firstId))
                .findFirst().orElseThrow().state().position();
        final Vector3D second = snapshot.bodies().stream()
                .filter(body -> body.definition().id().equals(secondId))
                .findFirst().orElseThrow().state().position();
        return first.distanceTo(second);
    }

    private static double relativeSpeedBetween(
            final SimulationSnapshot snapshot, final String firstId, final String secondId)
    {
        final Vector3D first = snapshot.bodies().stream()
                .filter(body -> body.definition().id().equals(firstId))
                .findFirst().orElseThrow().state().velocity();
        final Vector3D second = snapshot.bodies().stream()
                .filter(body -> body.definition().id().equals(secondId))
                .findFirst().orElseThrow().state().velocity();
        return first.subtract(second).magnitude();
    }
}
