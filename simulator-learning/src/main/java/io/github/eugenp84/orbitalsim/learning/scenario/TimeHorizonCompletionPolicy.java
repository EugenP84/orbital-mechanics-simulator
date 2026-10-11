package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Completes a scenario once it has run for a configured amount of elapsed simulated time.
 * <p>
 *
 * Because the policy is evaluated only at observation boundaries, completion is reported at the first observation <em>at or after</em> the horizon, not at the instant the horizon is crossed (see {@link ScenarioSession}).
 *
 * @author Eugeniu PLATITA
 */
public final class TimeHorizonCompletionPolicy implements ScenarioCompletionPolicy
{

    private final double horizonSeconds;

    /**
     * @param horizonSeconds elapsed scenario time at which the scenario completes; must be positive and finite
     * @throws IllegalArgumentException if the horizon is not positive and finite
     */
    public TimeHorizonCompletionPolicy(double horizonSeconds)
    {
        if (!Double.isFinite(horizonSeconds) || horizonSeconds <= 0.0)
        {
            throw new IllegalArgumentException("horizonSeconds must be positive and finite, was: " + horizonSeconds);
        }
        this.horizonSeconds = horizonSeconds;
    } // end of TimeHorizonCompletionPolicy() -----------

    /** The configured horizon in elapsed scenario seconds. */
    public double horizonSeconds() {
        return horizonSeconds;
    }

    @Override
    public ScenarioEvaluation evaluate(ScenarioObservation observation) {
        return observation.elapsedScenarioSeconds() >= horizonSeconds
                ? ScenarioEvaluation.COMPLETE
                : ScenarioEvaluation.CONTINUE;
    }
}