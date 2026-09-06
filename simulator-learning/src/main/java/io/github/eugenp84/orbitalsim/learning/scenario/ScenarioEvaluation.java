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
    FAIL,

    /**
     * The policy cannot produce a trustworthy assessment from the current run. e.g. excessive numerical energy drift or another policy-detectable integrity condition.
     * This is not a learner failure!!!
     */
    ASSESSMENT_INVALID
}