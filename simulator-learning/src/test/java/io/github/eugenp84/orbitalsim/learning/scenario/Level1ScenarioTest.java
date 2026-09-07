package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class Level1ScenarioTest
{
    private static final double ONE_DAY = 86_400.0;

    /** A safety bound on the drive loop, in whole simulated days. */
    private static final int SIMULATED_LIMIT_DAYS = 3_000;

    private final Level1Scenario scenario = new Level1Scenario();

    /**
     * Drives an attempt the way the application will:
     * repeated frame-sized advances until the scenario terminates.
     * Exceeding the limit is itself a failure, because these tests exist to prove the real stack terminates.
     */
    private static ScenarioLifecycleState runToTermination(Level1Attempt attempt) {
        ScenarioSession session = attempt.session();
        session.start();

        for (int day = 0; day < SIMULATED_LIMIT_DAYS; day++) {
            if (session.state() != ScenarioLifecycleState.RUNNING) {
                return session.state();
            }
            session.advanceBy(ONE_DAY);
        }

        return fail("scenario did not terminate within "
                + SIMULATED_LIMIT_DAYS + " simulated days");
    }

    // --- End-to-end through the real stack ---

    @Test
    void aCircularAttemptCompletesThroughTheRealEngineAndIntegrator() {
        // The end-to-end proof: real bodies, real gravity, real Velocity Verlet, real session cadence, real policy — from initial condition to verdict, with nothing synthesised.
        Level1Attempt attempt =
                scenario.createAttempt(scenario.circularSpeedMetresPerSecond());

        assertEquals(ScenarioLifecycleState.COMPLETED, runToTermination(attempt));
        assertEquals(Optional.of(ScenarioTerminationReason.POLICY_COMPLETION),
                attempt.session().terminationReason());
    }

    @Test
    void anOverEccentricAttemptFailsAtTheCheckpoint() {
        // 0.75x circular gives e ~ 0.44, well outside the 0.1 criterion, but still a bound orbit that must run its two revolutions first.
        Level1Attempt attempt =
                scenario.createAttempt(0.75 * scenario.circularSpeedMetresPerSecond());

        assertEquals(ScenarioLifecycleState.FAILED, runToTermination(attempt));
        assertEquals(Optional.of(ScenarioTerminationReason.POLICY_FAILURE),
                attempt.session().terminationReason());
    }

    @Test
    void anEscapingAttemptFails() {
        Level1Attempt attempt =
                scenario.createAttempt(1.5 * scenario.circularSpeedMetresPerSecond());

        assertEquals(ScenarioLifecycleState.FAILED, runToTermination(attempt));
        assertEquals(Optional.of(ScenarioTerminationReason.POLICY_FAILURE),
                attempt.session().terminationReason());
    }

    // --- Admissible input range ---

    @Test
    void bothEndsOfTheIntendedInputRangeCanBeConstructed() {
        // The range a slider is expected to span. The two ends are accepted for
        // different reasons, which is why both are checked: 0.5x is a bound
        // orbit whose sampling is adequate, while 1.5x is unbound and skips the
        // sampling check entirely, being decided by the escape test alone.
        double circular = scenario.circularSpeedMetresPerSecond();

        assertDoesNotThrow(() -> scenario.createAttempt(0.5 * circular),
                "0.5x circular is bound and must be adequately sampled");
        assertDoesNotThrow(() -> scenario.createAttempt(1.5 * circular),
                "1.5x circular is unbound and needs no revolution counting");
    }

    @Test
    void anAttemptTooEccentricToSampleIsRefusedRatherThanRun() {
        // 0.1x circular gives e ~ 0.99, needing far more observations per
        // revolution than this cadence provides. Refusing is honest; running it
        // would produce a verdict from a miscounted orbit.
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(0.1 * scenario.circularSpeedMetresPerSecond()));
    }

    @Test
    void createAttemptRejectsANonPositiveOrNonFiniteSpeed() {
        assertThrows(IllegalArgumentException.class, () -> scenario.createAttempt(0.0));
        assertThrows(IllegalArgumentException.class, () -> scenario.createAttempt(-1.0));
        assertThrows(IllegalArgumentException.class, () -> scenario.createAttempt(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> scenario.createAttempt(Double.POSITIVE_INFINITY));
    }

    // --------- What the presentation layer receives ------------------

    @Test
    void anAttemptExposesWhatThePresentationLayerNeeds()
    {
        double speed = 0.9 * scenario.circularSpeedMetresPerSecond();
        Level1Attempt attempt = scenario.createAttempt(speed);

        assertEquals(scenario.orbitalRadiusMetres(), attempt.orbitalRadiusMetres());
        assertEquals(speed, attempt.initialSpeedMetresPerSecond());
        assertEquals(0.9, attempt.speedFractionOfCircular(), 1e-12);

        // The body ids resolve within the published snapshot, so a renderer can find what to draw.
        SimulationSnapshot snapshot = attempt.session().snapshot();
        assertTrue(snapshot.bodies().stream()
                .anyMatch(b -> b.definition().id().equals(attempt.referenceBodyId())));
        assertTrue(snapshot.bodies().stream()
                .anyMatch(b -> b.definition().id().equals(attempt.learnerBodyId())));
    }

    @Test
    void aFreshAttemptIsNotStarted() {
        Level1Attempt attempt =
                scenario.createAttempt(scenario.circularSpeedMetresPerSecond());
        assertEquals(ScenarioLifecycleState.NOT_STARTED, attempt.session().state());
    }

    @Test
    void theAttemptRecordValidatesItsOwnNumericContract() {
        // Public type, so it must be trustworthy independently of the factory.
        ScenarioSession session =
                scenario.createAttempt(scenario.circularSpeedMetresPerSecond()).session();

        assertThrows(IllegalArgumentException.class,
                () -> new Level1Attempt(session, "a", "b", -1.0, 100.0, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Level1Attempt(session, "a", "b", 1.0, 0.0, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Level1Attempt(session, "a", "b", 1.0, 100.0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new Level1Attempt(session, "  ", "b", 1.0, 100.0, 100.0));
        assertThrows(NullPointerException.class,
                () -> new Level1Attempt(null, "a", "b", 1.0, 100.0, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Level1Attempt(session, "same", "same", 1.0, 100.0, 100.0));
    }
}