package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import java.util.Objects;

/**
 * One committed observation presented to a completion policy: the simulation state, together with the simulated time elapsed <em>since this scenario
 * started</em>.
 * <p>
 * Elapsed scenario time is supplied by the session rather than inferred from the engine clock, because the engine's clock measures time since engine
 * construction and a session may legitimately begin from a simulation that has already advanced — for example one restored from a saved state.
 * A policy that compared the engine clock directly would then complete immediately.
 *
 * @param snapshot               the committed simulation state; never null
 * @param elapsedScenarioSeconds simulated time since the scenario started;
 *                               finite and non-negative
 */
public record ScenarioObservation(SimulationSnapshot snapshot, double elapsedScenarioSeconds)
{

    public ScenarioObservation
    {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (!Double.isFinite(elapsedScenarioSeconds) || elapsedScenarioSeconds < 0.0)
        {
            throw new IllegalArgumentException(
                    "elapsedScenarioSeconds must be finite and non-negative, was: "
                            + elapsedScenarioSeconds);
        } // end if --------------
    }
}