package io.github.eugenp84.orbitalsim.evaluation.neo;

/**
 * Immutable result of one sampled Apophis close-approach propagation run.
 * <p>
 * The minimum is the least Earth-Apophis separation observed at the initial state or at a committed integration-step boundary.
 * It is therefore a sampled minimum, not an interpolated continuous-time closest approach.
 *
 * @param completedSteps number of committed steps in the run
 * @param elapsedSecondsAtMinimum elapsed simulation time at the sampled minimum
 * @param initialEpochJdTdb initial Horizons epoch in Julian Day TDB
 * @param minimumDistanceMetres sampled Earth-Apophis minimum distance
 * @param minimumEpochJdTdb astronomical epoch of the sampled minimum
 * @param relativeSpeedMetresPerSecond Earth-Apophis relative speed at the minimum
 * @param timeStepSeconds fixed numerical integration step
 *
 * @author Eugeniu PLATITA
 */
public record EncounterMeasurement(
    long completedSteps,
    double elapsedSecondsAtMinimum,
    double initialEpochJdTdb,
    double minimumDistanceMetres,
    double minimumEpochJdTdb,
    double relativeSpeedMetresPerSecond,
    double timeStepSeconds) {

  /**
   * Validates that the measurement represents a finite, physically meaningful completed propagation result.
   */
  public EncounterMeasurement
  {
    if (completedSteps < 0L) {
      throw new IllegalArgumentException("completedSteps must be non-negative");
    }
    requireNonNegativeFinite(elapsedSecondsAtMinimum, "elapsedSecondsAtMinimum");
    requirePositiveFinite(initialEpochJdTdb, "initialEpochJdTdb");
    requirePositiveFinite(minimumDistanceMetres, "minimumDistanceMetres");
    requirePositiveFinite(minimumEpochJdTdb, "minimumEpochJdTdb");
    requireNonNegativeFinite(relativeSpeedMetresPerSecond, "relativeSpeedMetresPerSecond");
    requirePositiveFinite(timeStepSeconds, "timeStepSeconds");
  }

  /**
   * Requires a value to be finite and non-negative.
   *
   * @param value value to validate
   * @param name parameter name for diagnostics
   */
  private static void requireNonNegativeFinite(final double value, final String name)
  {
    if (!Double.isFinite(value) || value < 0.0)
    {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }

  /**
   * Requires a value to be finite and strictly positive.
   *
   * @param value value to validate
   * @param name parameter name for diagnostics
   */
  private static void requirePositiveFinite(final double value, final String name)
  {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }
}
