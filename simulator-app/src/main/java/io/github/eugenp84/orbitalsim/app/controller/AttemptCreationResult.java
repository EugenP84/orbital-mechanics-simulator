package io.github.eugenp84.orbitalsim.app.controller;

/**
 * The outcome of asking the controller to build an attempt at a chosen speed.
 * <p>
 * {@link #CREATED} means a fresh, not-yet-started attempt was built and installed.
 * {@link #NOT_ASSESSABLE} means the chosen initial conditions cannot be assessed reliably at the scenario's configured observation cadence,
 * so the proposed attempt was refused and the previous attempt left intact — a valid learner input to report, not an error.
 *
 * @author Eugeniu PLATITA
 */
public enum AttemptCreationResult
{
    CREATED,
    NOT_ASSESSABLE
}