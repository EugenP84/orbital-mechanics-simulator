package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The learning-observation cadence, expressed as the two primitive facts it derives from (ADR-008 p.4).
 * <p>
 * This exists because the session and a completion policy must agree on the adence exactly: the session produces observations at it, while a policy such
 * as Level 1 validates that the learner's orbit is sampled finely enough at it.
 * Supplying the interval to each independently would let a policy validate a cadence the session never executes, with nothing to detect the disagreement.
 * <p>
 * The interval is derived rather than stored, so the object cannot hold an interval inconsistent with its own timestep and step count.
 *
 * @param timeStepSeconds     the simulation's fixed numerical timestep; must  match the engine the session drives
 * @param stepsPerObservation numerical steps per evaluation; positive, making the ADR-008 p,4 integer-multiple invariant structural rather than a floating-point check
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