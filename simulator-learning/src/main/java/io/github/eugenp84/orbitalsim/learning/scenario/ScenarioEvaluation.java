package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The outcome of evaluating a scenario's completion policy against one committed observation (ADR-008 §1).
 */
public enum ScenarioEvaluation
{

    /** The scenario has neither completed nor failed; advancement continues. */
    CONTINUE,

    /** The scenario's success condition is satisfied. */
    COMPLETE,

    /** The scenario's failure condition is satisfied. */
    FAIL
}