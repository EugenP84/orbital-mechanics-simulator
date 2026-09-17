package io.github.eugenp84.orbitalsim.evaluation.neo;

import io.github.eugenp84.orbitalsim.data.horizons.HorizonsEpochState;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentData;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentLoader;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the ADR-011 candidate-epoch and timestep convergence matrix.
 * <p>
 * The output is intentionally measurement-only. It does not compare against CNEOS,
 * choose a production timestep, interpolate the minimum, or impose an accuracy pass/fail threshold.
 * Those decisions are made only after inspecting numerical convergence independently of external-reference discrepancy.
 *
 * @author Eugeniu PLATITA
 */
public final class ApophisConvergenceExperiment
{

  private static final double CLOSE_APPROACH_JD_TDB = 2_462_240.407091595;
  private static final double POST_ENCOUNTER_DAYS = 1.0;
  private static final double SECONDS_PER_DAY = 86_400.0;

  private static final double[] TIME_STEPS_SECONDS = {
      3_600.0,
      1_800.0,
      900.0,
      450.0
  };

  /**
   * Executes all three candidate epochs at four fixed timesteps and writes a* CSV-compatible table to the Gradle test output.
   */
  @Test
  public void reportsCandidateEpochAndTimestepMatrix()
  {
    HorizonsExperimentData data = new HorizonsExperimentLoader().load();
    assertEquals(3, data.states().size(), "ADR-011 calibration expects three epochs");

    ApophisEncounterExperiment experiment = new ApophisEncounterExperiment();
    System.out.println("initial_jd_tdb,timestep_s,min_distance_km,min_jd_tdb," + "elapsed_at_min_s,relative_speed_km_s,completed_steps");

    for (HorizonsEpochState state : data.states())
    {
      double horizonSeconds = horizonSecondsFor(state.jdTdb());
      for (double timeStepSeconds : TIME_STEPS_SECONDS)
      {
        EncounterMeasurement measurement = experiment.run(state, timeStepSeconds, horizonSeconds);
        print(measurement);
      }
    }
  }

  /**
   * Calculates a horizon ending one day after the authoritative nominal CNEOS close-approach epoch.
   * Candidate epochs were selected as integral day offsets, so all configured timesteps divide these horizons exactly.
   *
   * @param initialEpochJdTdb candidate initial epoch
   * @return propagation horizon in seconds
   */
  private static double horizonSecondsFor(final double initialEpochJdTdb) {
    double days = CLOSE_APPROACH_JD_TDB - initialEpochJdTdb + POST_ENCOUNTER_DAYS;
    return days * SECONDS_PER_DAY;
  }

  /**
   * Prints one measurement as a locale-independent CSV row.
   *
   * @param measurement completed experiment result
   */
  private static void print(final EncounterMeasurement measurement)
  {
    System.out.printf(
        Locale.ROOT,
        "%.15f,%.1f,%.6f,%.15f,%.1f,%.9f,%d%n",
        measurement.initialEpochJdTdb(),
        measurement.timeStepSeconds(),
        measurement.minimumDistanceMetres() / 1_000.0,
        measurement.minimumEpochJdTdb(),
        measurement.elapsedSecondsAtMinimum(),
        measurement.relativeSpeedMetresPerSecond() / 1_000.0,
        measurement.completedSteps());
  }
}
