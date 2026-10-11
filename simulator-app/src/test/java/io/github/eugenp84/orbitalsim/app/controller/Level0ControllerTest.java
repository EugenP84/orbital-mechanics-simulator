package io.github.eugenp84.orbitalsim.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.learning.scenario.Level0Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Level0Controller}: its default attempt, configuration, reset, and delegation to
 * the shared {@link ScenarioPlayer}. Advancement behaviour is owned by {@link ScenarioPlayerTest};
 * these tests cover the controller's own responsibilities.
 *
 * @author Eugeniu PLATITA
 */
class Level0ControllerTest
{
    private static final double EARTH_MASS = PhysicalConstants.EARTH_MASS;
    private static final double EARTH_RADIUS = PhysicalConstants.EARTH_RADIUS;
    private static final double ONE_AU = PhysicalConstants.ASTRONOMICAL_UNIT;

    private final Level0Controller controller = new Level0Controller(new Level0Scenario());

    @Test
    void anInvalidConfigurationThrowsAndLeavesThePreviousAttemptFullyIntact()
    {
        // Install a distinctive valid configuration first, so any leak from the rejected one would be
        // visible in at least one field.
        controller.configureAttempt(2.0 * EARTH_MASS, 3.0 * EARTH_RADIUS, 1.5 * ONE_AU, 20_000.0);

        final double massBefore = controller.configuredMassKilograms();
        final double radiusBefore = controller.configuredRadiusMetres();
        final double distanceBefore = controller.configuredDistanceMetres();
        final double speedBefore = controller.configuredTangentialSpeedMetresPerSecond();
        final double circularBefore = controller.circularSpeedReferenceMetresPerSecond();

        // A substantially different, invalid configuration: distance inside the star.
        assertThrows(IllegalArgumentException.class, () -> controller.configureAttempt(
                5.0 * EARTH_MASS, EARTH_RADIUS, PhysicalConstants.SOLAR_RADIUS * 0.5, 40_000.0));

        assertEquals(massBefore, controller.configuredMassKilograms(), 0.0);
        assertEquals(radiusBefore, controller.configuredRadiusMetres(), 0.0);
        assertEquals(distanceBefore, controller.configuredDistanceMetres(), 0.0);
        assertEquals(speedBefore, controller.configuredTangentialSpeedMetresPerSecond(), 0.0);
        assertEquals(circularBefore, controller.circularSpeedReferenceMetresPerSecond(), 0.0);
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
    }

    @Test
    void configureAttemptInstallsANewNotStartedAttempt()
    {
        controller.toggleRun();
        assertEquals(ScenarioLifecycleState.RUNNING, controller.state());

        controller.configureAttempt(2.0 * EARTH_MASS, EARTH_RADIUS, 1.5 * ONE_AU, 20_000.0);

        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertEquals(2.0 * EARTH_MASS, controller.configuredMassKilograms(), 0.0);
        assertEquals(20_000.0, controller.configuredTangentialSpeedMetresPerSecond(), 0.0);
    }

    @Test
    void constructionRejectsNullScenario()
    {
        assertThrows(NullPointerException.class, () -> new Level0Controller(null));
    }

    @Test
    void defaultTangentialSpeedIsTheCircularReferenceNotAHardcodedConstant()
    {
        // The default launch speed is the circular speed for the default configuration, derived by
        // the scenario — so the two are equal, and neither is a literal.
        final double expected =
                new Level0Scenario().circularSpeedMetresPerSecond(EARTH_MASS, ONE_AU);
        assertEquals(expected, controller.configuredTangentialSpeedMetresPerSecond(), 1e-6);
        assertEquals(expected, controller.circularSpeedReferenceMetresPerSecond(), 1e-6);
    }

    @Test
    void resetRebuildsTheCurrentConfigurationAsNotStarted()
    {
        controller.configureAttempt(3.0 * EARTH_MASS, EARTH_RADIUS, ONE_AU, 18_000.0);
        controller.toggleRun();

        controller.reset();

        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertEquals(3.0 * EARTH_MASS, controller.configuredMassKilograms(), 0.0);
        assertEquals(18_000.0, controller.configuredTangentialSpeedMetresPerSecond(), 0.0);
    }

    @Test
    void startsWithAnEarthLikeNotStartedDefaultAttempt()
    {
        assertEquals(ScenarioLifecycleState.NOT_STARTED, controller.state());
        assertEquals(EARTH_MASS, controller.configuredMassKilograms(), 0.0);
        assertEquals(EARTH_RADIUS, controller.configuredRadiusMetres(), 0.0);
        assertEquals(ONE_AU, controller.configuredDistanceMetres(), 0.0);
    }
}