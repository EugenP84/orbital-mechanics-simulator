package io.github.eugenp84.orbitalsim.app.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsEpochState;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentData;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentLoader;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4Scenario;
import org.junit.jupiter.api.Test;


/**
 * Tests the application boundary that maps the verified Horizons fixture into Level 4.
 *
 * @author Eugeniu PLATITA
 */
class Level4AttemptFactoryTest
{
    /** Verifies that the factory selects the scenario's frozen epoch rather than a list position. */
    @Test
    void canonicalFactoryUsesTheFrozenHorizonsEpochAndStates()
    {
        final Level4Scenario scenario = new Level4Scenario();
        final HorizonsExperimentData data = new HorizonsExperimentLoader().load();
        final HorizonsEpochState expected = data.states().stream()
                .filter(state -> Double.compare(
                        state.jdTdb(), scenario.initialEpochJdTdb()) == 0)
                .findFirst()
                .orElseThrow();

        final Level4Attempt attempt = new Level4AttemptFactory().createAttempt();

        assertEquals(expected.jdTdb(), attempt.initialJdTdb(), 0.0);
        assertEquals(expected.earthState(), stateFor(attempt, attempt.earthId()).state());
        assertEquals(expected.apophisState(), stateFor(attempt, attempt.apophisId()).state());
    }

    /**
     * Resolves one body from a newly assembled attempt.
     *
     * @param attempt attempt whose initial snapshot is inspected
     * @param id      required body identifier
     * @return matching body snapshot
     */
    private static BodySnapshot stateFor(final Level4Attempt attempt, final String id)
    {
        return attempt.session().snapshot().bodies().stream()
                .filter(body -> body.definition().id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
