package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The completion policy for Level 0 (Planet Creation): the scenario completes at its first committed
 * observation, unconditionally.
 * <p>
 * Level 0 has no physical success criterion and no learner-failure path (ADR-008 level table):
 * its purpose is parameter familiarisation — the learner sets a body's physical parameters and confirms its creation — with no orbital objective imposed.
 * <p>
 * This trivial, physics-free policy sits behind the same {@link ScenarioCompletionPolicy} interface as Level 1's history-dependent orbital assessment is what demonstrates completion semantics are fully decoupled from the lifecycle.
 *
 * @author Eugeniu PLATITA
 */
public final class Level0PlanetCreationCompletionPolicy implements ScenarioCompletionPolicy
{
    @Override
    public ScenarioEvaluation evaluate(final ScenarioObservation observation)
    {
        // Unconditional: once a committed observation exists, the planet has been created.
        // Level 0 never returns FAIL or ASSESSMENT_INVALID — it has no reachable failure state.
        return ScenarioEvaluation.COMPLETE;
    }
}