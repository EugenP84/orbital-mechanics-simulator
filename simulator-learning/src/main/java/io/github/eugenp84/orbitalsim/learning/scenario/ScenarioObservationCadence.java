package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The learning-observation cadence, expressed as the two primitive facts it derives from (ADR-008 p.4).
 * <p>
 * @param timeStepSeconds     the simulation's fixed numerical timestep; must  match the engine the session drives
 * @param stepsPerObservation numerical steps per evaluation; positive.
 *
 * @author Eugeniu PLATITA
 */
public record ScenarioObservationCadence(double timeStepSeconds, int stepsPerObservation)
{

    public ScenarioObservationCadence
    {
        if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0)
        {
            throw new IllegalArgumentException("timeStepSeconds must be positive and finite. was: " + timeStepSeconds);
        }
        if (stepsPerObservation <= 0) {
            throw new IllegalArgumentException("stepsPerObservation must be positive, was: " + stepsPerObservation);
        }

        // Validated here so a cadence that cannot yield a usable interval cannot be constructed at all; consumers may then rely on it.
        double interval = timeStepSeconds * stepsPerObservation;
        if (!Double.isFinite(interval) || interval <= 0.0) {
            throw new IllegalArgumentException("Observation interval is not representable: " + timeStepSeconds + " x " + stepsPerObservation);
        }
    }

    /** Simulated time between successive evaluations. */
    public double intervalSeconds() {
        return timeStepSeconds * stepsPerObservation;
    }
}