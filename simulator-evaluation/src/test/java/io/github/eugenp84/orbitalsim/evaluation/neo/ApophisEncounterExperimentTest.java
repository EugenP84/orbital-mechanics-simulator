package io.github.eugenp84.orbitalsim.evaluation.neo;

import io.github.eugenp84.orbitalsim.data.horizons.HorizonsEpochState;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentData;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsExperimentLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behavioural tests for the headless Apophis encounter experiment.
 *
 * @author Eugeniu PLATITA
 */
public final class ApophisEncounterExperimentTest
{
  private static final double ONE_DAY_SECONDS = 86_400.0;
  private static final double ONE_HOUR_SECONDS = 3_600.0;

  /**
   * Verifies that identical initial conditions and numerical configuration produce exactly repeatable measurements.
   */
  @Test
  public void identicalRunsAreDeterministic()
  {
    HorizonsEpochState state = latestState();
    ApophisEncounterExperiment experiment = new ApophisEncounterExperiment();
    EncounterMeasurement first = experiment.run(state, ONE_HOUR_SECONDS, 2.0 * ONE_DAY_SECONDS);
    EncounterMeasurement second = experiment.run(state, ONE_HOUR_SECONDS, 2.0 * ONE_DAY_SECONDS);

    assertEquals(first, second);
  }

  /**
   * Verifies that the one-day-before candidate approaches more closely than its initial separation during a horizon spanning the encounter.
   */
  @Test
  public void oneDayCandidateApproachesEarthWithinExperimentHorizon()
  {
    HorizonsEpochState state = latestState();
    double initialDistance = state.apophisState().position().distanceTo(state.earthState().position());
    EncounterMeasurement measurement = new ApophisEncounterExperiment().run(state, ONE_HOUR_SECONDS, 2.0 * ONE_DAY_SECONDS);

    assertTrue(measurement.minimumDistanceMetres() < initialDistance);
    assertTrue(measurement.elapsedSecondsAtMinimum() > 0.0);
    assertTrue(measurement.elapsedSecondsAtMinimum() < 2.0 * ONE_DAY_SECONDS);
  }//----= end of oneDayCandidateApproachesEarthWithinExperimentHorizon() ---------=====================

  /**
   * Verifies that a horizon requiring a fractional final step is rejected rather than silently overshot or truncated.
   */
  @Test
  public void rejectsHorizonThatIsNotWholeNumberOfSteps() {
    HorizonsEpochState state = latestState();
    ApophisEncounterExperiment experiment = new ApophisEncounterExperiment();

    assertThrows(IllegalArgumentException.class,
        () -> experiment.run(state, ONE_HOUR_SECONDS, ONE_DAY_SECONDS + 1.0));
  }

  /**
   * Loads the latest of the three verified Horizons candidate states.
   *
   * @return one-day-before candidate state
   */
  private static HorizonsEpochState latestState() {
    HorizonsExperimentData data = new HorizonsExperimentLoader().load();
    return data.states().get(data.states().size() - 1);
  }
}
