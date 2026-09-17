package io.github.eugenp84.orbitalsim.evaluation.neo;

import io.github.eugenp84.orbitalsim.data.horizons.HorizonsEpochState;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentData;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentLoader;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Refines the one-day Apophis propagation timestep and reports discrepancy against the independent JPL CAD close-approach reference.
 * <p>
 * The JPL reference is used only after propagation has independently selected its sampled minimum.
 * It never initialises the trajectory, chooses the sampled minimum, stops the simulation, or supplies an accuracy pass/fail threshold.
 *
 * @author Eugeniu PLATITA
 */
public final class ApophisExternalValidationExperiment
{

    private static final double PHASE_OFFSET_TIME_STEP_SECONDS = 51.2;
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double[] TIME_STEPS_SECONDS = {
            450.0,
            225.0,
            112.5,
            56.25
    };

    /**
     * Runs one deliberately phase-offset sampling experiment.
     * The two-day propagation horizon is an exact whole number of 51.2-second steps, while the independent JPL encounter epoch lies halfway between two sample times.
     * The reference therefore cannot coincide with a committed sample.
     */
    @Test
    public void reportsPhaseOffsetCheckAgainstCneos()
    {
        HorizonsEpochState initialState = oneDayInitialState();
        CneosCloseApproachReference reference = new CneosReferenceLoader().load();
        ApophisEncounterExperiment experiment = new ApophisEncounterExperiment();

        double referenceOffsetSeconds = (reference.epochJdTdb() - initialState.jdTdb()) * SECONDS_PER_DAY;
        assertEquals(
                SECONDS_PER_DAY,
                referenceOffsetSeconds,
                1.0e-3,
                "selected Horizons state must be one day before the CAD nominal epoch");

        double phase = referenceOffsetSeconds / PHASE_OFFSET_TIME_STEP_SECONDS;
        assertEquals(
                0.5,
                phase - Math.floor(phase),
                1.0e-9,
                "CAD epoch must lie halfway between adjacent phase-offset samples");

        EncounterMeasurement encounter = experiment.run(
                initialState,
                PHASE_OFFSET_TIME_STEP_SECONDS,
                2.0 * SECONDS_PER_DAY);

        System.out.println("phase_offset_check");
        System.out.println("timestep_s,min_distance_km,min_jd_tdb,epoch_delta_s," + "relative_speed_km_s,distance_delta_km,speed_delta_m_s,completed_steps");
        print(compare(encounter, reference));
    }

    /**
     * Runs the local timestep-refinement matrix and prints descriptive external validation differences as CSV-compatible rows.
     */
    @Test
    public void reportsOneDayRefinementAgainstCneos()
    {
        HorizonsEpochState initialState = oneDayInitialState();
        CneosCloseApproachReference reference = new CneosReferenceLoader().load();
        ApophisEncounterExperiment experiment = new ApophisEncounterExperiment();

        assertEquals(
                SECONDS_PER_DAY,
                (reference.epochJdTdb() - initialState.jdTdb()) * SECONDS_PER_DAY,
                1.0e-3,
                "selected Horizons state must be one day before the CAD nominal epoch");

        System.out.println("timestep_s,min_distance_km,min_jd_tdb,epoch_delta_s," + "relative_speed_km_s,distance_delta_km,speed_delta_m_s,completed_steps");

        for (double timeStepSeconds : TIME_STEPS_SECONDS)
        {
            EncounterMeasurement encounter = experiment.run(
                    initialState,
                    timeStepSeconds,
                    2.0 * SECONDS_PER_DAY);
            print(compare(encounter, reference));
        }
    }

    /**
     * Computes descriptive differences without applying an acceptance threshold.
     *
     * @param encounter independently measured simulated encounter
     * @param reference independent JPL CAD reference
     * @return discrepancy measurement
     */
    private static ExternalValidationMeasurement compare(final EncounterMeasurement encounter, final CneosCloseApproachReference reference)
    {
        double signedDistance = encounter.minimumDistanceMetres() - reference.nominalDistanceMetres();
        double signedEpochSeconds = (encounter.minimumEpochJdTdb() - reference.epochJdTdb()) * SECONDS_PER_DAY;
        double signedSpeed = encounter.relativeSpeedMetresPerSecond() - reference.relativeSpeedMetresPerSecond();

        return new ExternalValidationMeasurement(
                encounter,
                signedDistance,
                Math.abs(signedDistance),
                signedEpochSeconds,
                signedSpeed);
    }

    /**
     * Loads the verified one-day-before Horizons state by epoch rather than list position, avoiding accidental dependence on fixture ordering.
     *
     * @return one-day-before initial state
     */
    private static HorizonsEpochState oneDayInitialState()
    {
        HorizonsExperimentData data = new HorizonsExperimentLoader().load();
        double expectedEpoch = 2_462_239.407091595;
        for (HorizonsEpochState state : data.states())
        {
            if (Math.abs(state.jdTdb() - expectedEpoch) < 1.0e-12) {
                return state;
            }
        }
        throw new IllegalStateException("Missing one-day-before Horizons state");
    }

    /**
     * Prints one external-validation measurement.
     *
     * @param measurement measurement to print
     */
    private static void print(final ExternalValidationMeasurement measurement)
    {
        EncounterMeasurement encounter = measurement.encounter();
        System.out.printf(
                Locale.ROOT,
                "%.2f,%.6f,%.15f,%.3f,%.9f,%.6f,%.6f,%d%n",
                encounter.timeStepSeconds(),
                encounter.minimumDistanceMetres() / 1_000.0,
                encounter.minimumEpochJdTdb(),
                measurement.signedEpochDifferenceSeconds(),
                encounter.relativeSpeedMetresPerSecond() / 1_000.0,
                measurement.signedDistanceDifferenceMetres() / 1_000.0,
                measurement.signedRelativeSpeedDifferenceMetresPerSecond(),
                encounter.completedSteps());
    }
}
