package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import java.util.Objects;

/**
 * One committed observation presented to a completion policy: the simulation state, together with the simulated time elapsed <em>since this scenario
 * started</em>.
 * <p>
 *
 * @param snapshot               the committed simulation state; never null
 * @param elapsedScenarioSeconds simulated time since the scenario started; finite and non-negative
 *
 * @author Eugeniu PLATITA
 */
public record ScenarioObservation(SimulationSnapshot snapshot, double elapsedScenarioSeconds)
{

    public ScenarioObservation
    {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (!Double.isFinite(elapsedScenarioSeconds) || elapsedScenarioSeconds < 0.0)
        {
            throw new IllegalArgumentException("elapsedScenarioSeconds must be finite and non-negative, was: " + elapsedScenarioSeconds);
        } // end if
    }
}