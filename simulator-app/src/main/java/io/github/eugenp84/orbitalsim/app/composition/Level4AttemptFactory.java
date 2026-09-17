package io.github.eugenp84.orbitalsim.app.composition;

import io.github.eugenp84.orbitalsim.data.horizons.HorizonsEpochState;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentData;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentLoader;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4InitialConditions;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4Scenario;

/**
 * Application-layer composition boundary for the canonical Level 4 experiment.
 * <p>
 * The data module validates and normalises the bundled JPL Horizons fixture.
 * This class selects the epoch frozen by {@link Level4Scenario}, translates the data-layer state into the neutral learning input, and asks the scenario to assemble a fresh attempt.
 * Neither the data module nor the learning module therefore depends on the other.
 *
 * @author Eugeniu PLATITA
 */
public final class Level4AttemptFactory
{
    private final Level4InitialConditions initialConditions;
    private final Level4Scenario scenario;

    /**
     * Creates the production factory from the bundled, verified Horizons fixture.
     */
    public Level4AttemptFactory()
    {
        this(new HorizonsExperimentLoader(), new Level4Scenario());
    }

    /**
     * Creates a factory from explicit collaborators, primarily for focused application-layer tests.
     *
     * @param loader   verified Horizons fixture loader; never null
     * @param scenario Level 4 scenario defining the frozen production epoch; never null
     * @throws NullPointerException if either collaborator is null
     * @throws IllegalStateException if the verified fixture lacks the frozen Level 4 epoch
     */
    Level4AttemptFactory(final HorizonsExperimentLoader loader, final Level4Scenario scenario)
    {
        if (loader == null)
        {
            throw new NullPointerException("loader must not be null");
        }
        if (scenario == null)
        {
            throw new NullPointerException("scenario must not be null");
        }
        this.scenario = scenario;
        this.initialConditions = selectInitialConditions(loader.load(), scenario.initialEpochJdTdb());
    }

    /**
     * Builds a fresh Level 4 attempt from the already verified canonical initial conditions.
     *
     * @return fresh, not-yet-started Level 4 attempt
     */
    public Level4Attempt createAttempt()
    {
        return scenario.createAttempt(initialConditions);
    }

    /**
     * Selects the state whose epoch exactly matches the empirically frozen Level 4 epoch.
     * <p>
     * The fixture stores that epoch as a decimal string and the scenario constant uses the same {@code double} value.
     * Exact comparison is intentional: silently accepting a nearby calibration epoch would change the validated experiment.
     *
     * @param data        verified Horizons experiment data
     * @param requiredJd required Julian Day TDB
     * @return data-neutral Level 4 initial conditions
     * @throws IllegalStateException if the required epoch is absent
     */
    private static Level4InitialConditions selectInitialConditions(final HorizonsExperimentData data, final double requiredJd)
    {
        for (final HorizonsEpochState state : data.states())
        {
            if (Double.compare(state.jdTdb(), requiredJd) == 0)
            {
                return new Level4InitialConditions(state.apophisState(), state.earthState(), state.jdTdb());
            }
        }
        throw new IllegalStateException("Verified Horizons fixture lacks Level 4 initial JD TDB " + requiredJd);
    }
}
