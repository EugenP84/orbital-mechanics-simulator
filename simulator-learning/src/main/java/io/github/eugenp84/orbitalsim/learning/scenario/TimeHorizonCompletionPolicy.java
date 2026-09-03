package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Completes a scenario once it has run for a configured amount of elapsed simulated time.
 * <p>
 * The comparison uses {@link ScenarioObservation#elapsedScenarioSeconds()},
 * which is measured from the scenario's start, not the engine's clock;
 * a session beginning from an already-advanced simulation therefore still runs for the full horizon.
 * <p>
 * This is the simplest genuinely dynamic completion condition: it depends on committed simulation state rather than on user action,
 * but requires no orbital mathematics. It has no failure condition.
 * <p>
 * Because the policy is evaluated only at observation boundaries, completion is reported at the first observation <em>at or after</em> the horizon, not at the instant the horizon is crossed (see {@link ScenarioSession}).
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
            throw new IllegalArgumentException(
                    "horizonSeconds must be positive and finite, was: " + horizonSeconds);
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