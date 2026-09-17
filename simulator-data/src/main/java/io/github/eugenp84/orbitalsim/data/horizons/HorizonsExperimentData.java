package io.github.eugenp84.orbitalsim.data.horizons;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, verified input dataset for the canonical Apophis 2029 experiment.
 * The dataset carries one common provenance description and paired Earth/Apophis states at each candidate calibration epoch.
 *
 * @param provenance authoritative Horizons provenance
 * @param states     paired candidate-epoch states, in ascending epoch order
 *
 * @author Eugeniu PLATITA
 */
public record HorizonsExperimentData(
        HorizonsProvenance provenance,
        List<HorizonsEpochState> states) {

    /**
     * Defensively copies the state list and rejects missing or unordered data.
     */
    public HorizonsExperimentData
    {
        Objects.requireNonNull(provenance, "provenance must not be NULL");
        Objects.requireNonNull(states, "states must not be null");
        states = List.copyOf(states);
        if (states.isEmpty()) {
            throw new IllegalArgumentException("States must not be empty");
        }
        for (int i = 1; i < states.size(); i++)
        {
            if (states.get(i).jdTdb() <= states.get(i - 1).jdTdb())
            {
                throw new IllegalArgumentException("states must have strictly increasing epochs");
            }
        }// end for loop -------
    }
}
