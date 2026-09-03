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

class TimeHorizonCompletionPolicyTest {

    private static SimulationSnapshot snapshotAt(double seconds, long steps) {
        return new SimulationSnapshot(seconds, steps, List.of(new BodySnapshot(
                new CelestialBody("star-1", "Star", CelestialBodyType.STAR,
                        PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS),
                new StateVector(Vector3D.ZERO, Vector3D.ZERO))));
    }

    /** An observation whose engine clock and elapsed scenario time agree. */
    private static ScenarioObservation observedAt(double elapsedSeconds) {
        return new ScenarioObservation(
                snapshotAt(elapsedSeconds, (long) (elapsedSeconds / 10.0)), elapsedSeconds);
    }

    @Test
    void rejectsANonPositiveOrNonFiniteHorizon() {
        assertThrows(IllegalArgumentException.class, () -> new TimeHorizonCompletionPolicy(0.0));
        assertThrows(IllegalArgumentException.class, () -> new TimeHorizonCompletionPolicy(-1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeHorizonCompletionPolicy(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeHorizonCompletionPolicy(Double.POSITIVE_INFINITY));
    }

    @Test
    void exposesTheConfiguredHorizon() {
        assertEquals(60.0, new TimeHorizonCompletionPolicy(60.0).horizonSeconds(), 1e-12);
    }

    @Test
    void continuesBeforeTheHorizonAndCompletesAtOrAfterIt() {
        TimeHorizonCompletionPolicy policy = new TimeHorizonCompletionPolicy(60.0);

        assertEquals(ScenarioEvaluation.CONTINUE, policy.evaluate(observedAt(0.0)));
        assertEquals(ScenarioEvaluation.CONTINUE, policy.evaluate(observedAt(59.999)));
        assertEquals(ScenarioEvaluation.COMPLETE, policy.evaluate(observedAt(60.0)));
        assertEquals(ScenarioEvaluation.COMPLETE, policy.evaluate(observedAt(90.0)));
    }

    @Test
    void comparesElapsedScenarioTimeNotTheEngineClock() {
        TimeHorizonCompletionPolicy policy = new TimeHorizonCompletionPolicy(60.0);

        // Engine clock far past the horizon, but only 10 s of scenario elapsed
        ScenarioObservation observation =
                new ScenarioObservation(snapshotAt(5000.0, 500L), 10.0);

        assertEquals(ScenarioEvaluation.CONTINUE, policy.evaluate(observation));
    }

    @Test
    void neverReportsFailure() {
        TimeHorizonCompletionPolicy policy = new TimeHorizonCompletionPolicy(60.0);
        for (double t : new double[]{0.0, 30.0, 60.0, 1.0e9}) {
            assertNotEquals(ScenarioEvaluation.FAIL, policy.evaluate(observedAt(t)));
        }
    }
}