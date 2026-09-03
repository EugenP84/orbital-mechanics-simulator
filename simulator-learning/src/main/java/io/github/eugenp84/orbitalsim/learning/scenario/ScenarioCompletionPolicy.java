package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Decides whether a scenario has completed or failed, given a committed observation (ADR-008 §1).
 * <p>
 * Completion semantics are composed into a session rather than inherited:
 * scenarios differ in whether they have a physical success condition, whether failure is possible at all, and whether progress is history-dependent.
 * A policy that needs history (for example counting completed revolutions) is
 * free to accumulate it across successive evaluations — history-dependent scenario progress belongs to the learning layer,
 * not to the computational core (ADR-008 §7).
 * <p>
 * A policy is evaluated once per committed observation, at the session's learning cadence, and must never return null: doing so is a programming
 * defect and is surfaced as one rather than being treated as a scenario outcome.
 */
@FunctionalInterface
public interface ScenarioCompletionPolicy
{

    /**
     * @param observation the committed state and elapsed scenario time
     * @return whether the scenario continues, has completed, or has failed;
     *        never null
     */
    ScenarioEvaluation evaluate(ScenarioObservation observation);
}