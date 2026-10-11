package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The progression states a scenario session may occupy (ADR-008 §3).
 * <p>
 *
 * @author Eugeniu PLATITA
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