package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Why a scenario reached a terminal lifecycle state (ADR-008 §1).
 * <p>
 * Lifecycle state and termination reason are separate concepts:
 * two fundamentally different events both terminate a scenario as {@link ScenarioLifecycleState#FAILED},
 * and collapsing them would leave the application unable to tell a learner whether their orbit failed or the simulation itself became untrustworthy.
 */
public enum ScenarioTerminationReason
{

    /** completion policy reported success against a committed observation. */
    POLICY_COMPLETION,

    /**
     * The completion policy reported failure against committed observation:
     * a valid simulation in which the scenario's failure condition was met.
     */
    POLICY_FAILURE,


    /**
     * The completion policy determined that the current attempt could no longer be assessed reliably.
     */
    ASSESSMENT_INVALID,

    /**
     * The engine reported numerical breakdown.
     * The simulation result cannot be trusted, so no pedagogical conclusion is drawn — this is a technical failure, not a statement about the learner's configuration.
     */
    SIMULATION_BREAKDOWN
}