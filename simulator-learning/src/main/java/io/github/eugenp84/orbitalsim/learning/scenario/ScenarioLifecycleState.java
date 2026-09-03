package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The progression states a scenario session may occupy (ADR-008 §3).
 * <p>
 * Not every state is reachable in every scenario: a scenario with no failure
 * condition never enters {@link #FAILED}. Reachability is a property of the
 * scenario's completion policy, not of this model.
 * <p>
 * Scenario progression is distinct from simulation pause in the engine sense:
 * the engine owns no lifecycle concept (ADR-006 §2), so pausing is entirely a learning-layer concern.
 */
public enum ScenarioLifecycleState
{

    /** Configured but not yet started; no baseline for elapsed time exists. */
    NOT_STARTED,

    /** Active; advancement requests are chunked, executed and evaluated. */
    RUNNING,

    /** Temporarily suspended; advancement requests are ignored. */
    PAUSED,

    /** Terminal: the scenario finished successfully. */
    COMPLETED,

    /** Terminal: the scenario failed. See {@link ScenarioTerminationReason}. */
    FAILED
}