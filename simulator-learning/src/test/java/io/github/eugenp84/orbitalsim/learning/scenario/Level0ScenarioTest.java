package io.github.eugenp84.orbitalsim.learning.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Level0Scenario}, exercised through the real session and engine.
 *
 * @author Eugeniu PLATITA
 */
class Level0ScenarioTest
{
    private static final double EARTH_MASS = PhysicalConstants.EARTH_MASS;
    private static final double EARTH_RADIUS = PhysicalConstants.EARTH_RADIUS;
    private static final double ONE_AU = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double SOME_SPEED = 25_000.0;

    private final Level0Scenario scenario = new Level0Scenario();

    @Test
    void completesAtTheFirstObservationThroughTheRealSession()
    {
        final Level0Attempt attempt =
                scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, SOME_SPEED);
        final ScenarioSession session = attempt.session();

        assertEquals(ScenarioLifecycleState.NOT_STARTED, session.state());
        session.start();
        session.advanceBy(session.observationIntervalSeconds());

        assertEquals(ScenarioLifecycleState.COMPLETED, session.state());
        assertEquals(Optional.of(ScenarioTerminationReason.POLICY_COMPLETION),
                session.terminationReason());
    }

    @Test
    void installsTheGivenTangentialSpeedAsTheInitialRelativeVelocity()
    {
        // Proves the scenario used the learner's speed in the actual StateVector, not a derived
        // circular one. Checked as a relative velocity because the system is initialised in the
        // centre-of-mass frame, so the planet's absolute velocity is not exactly SOME_SPEED.
        final Level0Attempt attempt =
                scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, SOME_SPEED);
        final SimulationSnapshot snapshot = attempt.session().snapshot();

        final Vector3D starVelocity =
                bodyById(snapshot, attempt.referenceBodyId()).state().velocity();
        final Vector3D planetVelocity =
                bodyById(snapshot, attempt.learnerBodyId()).state().velocity();
        final Vector3D relativeVelocity = planetVelocity.subtract(starVelocity);

        assertEquals(0.0, relativeVelocity.x(), 1e-6);
        assertEquals(SOME_SPEED, relativeVelocity.y(), 1e-6);
        assertEquals(0.0, relativeVelocity.z(), 1e-6);
    }

    @Test
    void exposesCircularSpeedAsAReferenceDistinctFromTheGivenSpeed()
    {
        final Level0Attempt attempt =
                scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, SOME_SPEED);

        final double mu = PhysicalConstants.GRAVITATIONAL_CONSTANT
                * (PhysicalConstants.SOLAR_MASS + EARTH_MASS);
        assertEquals(Math.sqrt(mu / ONE_AU), attempt.circularSpeedMetresPerSecond(), 1e-6);
        assertTrue(attempt.circularSpeedMetresPerSecond() != attempt.tangentialSpeedMetresPerSecond());
    }

    @Test
    void exposesTheConfiguredPlanetInTheSnapshot()
    {
        final Level0Attempt attempt =
                scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, SOME_SPEED);
        final SimulationSnapshot snapshot = attempt.session().snapshot();

        assertTrue(snapshot.bodies().stream()
                .anyMatch(b -> b.definition().id().equals(attempt.referenceBodyId())));
        assertTrue(snapshot.bodies().stream()
                .anyMatch(b -> b.definition().id().equals(attempt.learnerBodyId())));
    }

    @Test
    void aFreshAttemptIsNotStarted()
    {
        final Level0Attempt attempt =
                scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, SOME_SPEED);
        assertEquals(ScenarioLifecycleState.NOT_STARTED, attempt.session().state());
    }

    @Test
    void rejectsNonPositiveOrNonFiniteConfiguration()
    {
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(0.0, EARTH_RADIUS, ONE_AU, SOME_SPEED));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(EARTH_MASS, 0.0, ONE_AU, SOME_SPEED));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, 0.0, SOME_SPEED));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, Double.NaN));
    }

    @Test
    void rejectsAPlanetThatWouldOverlapTheStarAtTheStart()
    {
        // Distance smaller than the star radius: the planet's surface reaches into the star.
        final double insideStar = PhysicalConstants.SOLAR_RADIUS * 0.5;
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, insideStar, SOME_SPEED));
    }

    @Test
    void completedLevel0AttemptRemainsCompletedOnFurtherAdvance()
    {
        // Terminal-state stability: once completed at the first observation, further requested
        // advancement leaves the session completed. This does not, by itself, prove the broad
        // "no future observation can fail" claim — that follows from the policy having no FAIL path.
        final Level0Attempt attempt =
                scenario.createAttempt(EARTH_MASS, EARTH_RADIUS, ONE_AU, SOME_SPEED);
        final ScenarioSession session = attempt.session();
        session.start();
        session.advanceBy(10.0 * session.observationIntervalSeconds());
        assertEquals(ScenarioLifecycleState.COMPLETED, session.state());
    }

    private static BodySnapshot bodyById(final SimulationSnapshot snapshot, final String id)
    {
        return snapshot.bodies().stream()
                .filter(b -> b.definition().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("snapshot missing body id: " + id));
    }
}