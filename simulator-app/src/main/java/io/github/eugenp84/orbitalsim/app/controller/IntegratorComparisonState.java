package io.github.eugenp84.orbitalsim.app.controller;

/**
 * Presentation lifecycle of the current numerical-integrator comparison run.
 * <p>
 * This lifecycle belongs to the comparison feature and is deliberately independent of the learning-scenario lifecycle.
 *
 * @author Eugeniu PLATITA
 */
public enum IntegratorComparisonState
{
    READY,
    RUNNING,
    PAUSED,
    COMPLETED
}