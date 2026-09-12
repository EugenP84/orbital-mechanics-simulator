package io.github.eugenp84.orbitalsim.learning.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Level1HintEngine}. Snapshots are taken from real attempts, then the listener
 * callback is invoked directly — no JavaFX and no live simulation loop.
 *
 * @author Eugeniu PLATITA
 */
class Level1HintEngineTest
{
    private static final Level1Scenario SCENARIO = new Level1Scenario();
    private static final String STAR = "l1-star";
    private static final String PLANET = "l1-planet";

    private static Level1HintEngine engineForFraction(final double fraction)
    {
        return new Level1HintEngine(STAR, PLANET, gravitationalParameterFromScenario(), fraction);
    }

    private static double gravitationalParameterFromScenario()
    {
        // circularSpeed = sqrt(mu / r)  =>  mu = circularSpeed^2 * r
        final double circular = SCENARIO.circularSpeedMetresPerSecond();
        return circular * circular * SCENARIO.orbitalRadiusMetres();
    }

    private static SimulationSnapshot snapshotAtFraction(final double fraction)
    {
        return SCENARIO.createAttempt(fraction * SCENARIO.circularSpeedMetresPerSecond())
                .session().snapshot();
    }

    @Test
    void noHintBeforeAnyObservation()
    {
        assertTrue(engineForFraction(1.0).latestHint().isEmpty());
    }

    @Test
    void nearCircularIsReported()
    {
        final Level1HintEngine engine = engineForFraction(1.0);
        engine.onSnapshot(snapshotAtFraction(1.0));
        assertEquals(HintCategory.NEAR_CIRCULAR, engine.latestHint().orElseThrow().category());
    }

    @Test
    void slightlyBelowCircularIsReported()
    {
        final Level1HintEngine engine = engineForFraction(0.90);
        engine.onSnapshot(snapshotAtFraction(0.90));
        assertEquals(HintCategory.BELOW_CIRCULAR_SLIGHTLY, engine.latestHint().orElseThrow().category());
    }

    @Test
    void wellBelowCircularIsReported()
    {
        final Level1HintEngine engine = engineForFraction(0.70);
        engine.onSnapshot(snapshotAtFraction(0.70));
        assertEquals(HintCategory.BELOW_CIRCULAR_WELL, engine.latestHint().orElseThrow().category());
    }

    @Test
    void slightlyAboveCircularIsReported()
    {
        final Level1HintEngine engine = engineForFraction(1.10);
        engine.onSnapshot(snapshotAtFraction(1.10));
        assertEquals(HintCategory.ABOVE_CIRCULAR_SLIGHTLY, engine.latestHint().orElseThrow().category());
    }

    @Test
    void wellAboveButBoundIsReported()
    {
        final Level1HintEngine engine = engineForFraction(1.30);
        engine.onSnapshot(snapshotAtFraction(1.30));
        assertEquals(HintCategory.ABOVE_CIRCULAR_WELL, engine.latestHint().orElseThrow().category());
    }

    @Test
    void unboundOverridesDirectionAndReportsUnbound()
    {
        // 1.45x circular exceeds escape (sqrt(2) ~ 1.414): specific orbital energy >= 0.
        final Level1HintEngine engine = engineForFraction(1.45);
        engine.onSnapshot(snapshotAtFraction(1.45));
        assertEquals(HintCategory.UNBOUND, engine.latestHint().orElseThrow().category());
    }

    @Test
    void messageIsAlwaysPresentAndNonBlank()
    {
        final Level1HintEngine engine = engineForFraction(0.70);
        engine.onSnapshot(snapshotAtFraction(0.70));
        assertFalse(engine.latestHint().orElseThrow().message().isBlank());
    }

    @Test
    void constructionRejectsBadArguments()
    {
        final double mu = gravitationalParameterFromScenario();
        assertThrows(IllegalArgumentException.class,
                () -> new Level1HintEngine(STAR, STAR, mu, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Level1HintEngine(STAR, PLANET, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Level1HintEngine(STAR, PLANET, mu, 0.0));
        assertThrows(NullPointerException.class,
                () -> new Level1HintEngine(null, PLANET, mu, 1.0));
    }
}