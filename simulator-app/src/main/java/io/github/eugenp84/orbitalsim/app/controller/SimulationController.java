package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Hint;
import io.github.eugenp84.orbitalsim.learning.scenario.HintSource;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioAttemptNotAssessableException;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Coordinates a running Level 1 scenario on behalf of the user interface.
 * <p>
 * Lifecycle and presentation behaviour shared by every level — run/pause, wall-clock-to-simulated advancement, snapshot and lifecycle access, presentation speed — is delegated to a {@link ScenarioPlayer}.
 * This controller adds only Level 1's configuration and pedagogy: building an attempt from a chosen orbital speed, exposing that speed relative to circular, and surfacing hints.
 * It remains JavaFX-free and its public API is unchanged from before the {@code ScenarioPlayer} extraction, so the view and the existing tests are unaffected.
 * <p>
 * Reset and reconfiguration build a new attempt rather than rewinding the session (ADR-008 p3);
 * a reconfiguration that cannot be assessed is refused, leaving the previous attempt unchanged, because construction completes before any controller state is modified.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationController implements ScenarioControls
{
    private final Level1Scenario scenario;
    private final ScenarioPlayer player;

    private double configuredSpeedMetresPerSecond;
    private HintSource hints;

    /**
     * Creates a controller holding a fresh, not-yet-started attempt at the scenario's circular speed, and the default presentation rate.
     *
     * @param scenario the Level 1 scenario used to build attempts; never null
     */
    public SimulationController(final Level1Scenario scenario)
    {
        this.scenario = Objects.requireNonNull(scenario, "scenario must not be null");
        final Level1Attempt attempt = scenario.createAttempt(scenario.circularSpeedMetresPerSecond());
        this.player = new ScenarioPlayer(attempt.session());
        this.hints = attempt.hints();
        this.configuredSpeedMetresPerSecond = scenario.circularSpeedMetresPerSecond();
    }

    /**
     * Advances by the simulated time corresponding to the given wall-clock interval, but only while running.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if the argument is negative or non-finite, or if it and the
     *         presentation speed produce a non-finite duration
     */
    public void advanceByWallClock(final double wallClockSeconds)
    {
        player.advanceByWallClock(wallClockSeconds);
    }

    /**
     * Builds a fresh, not-yet-started attempt at the given orbital speed and, only if it can be assessed,
     * installs it. On refusal the previous attempt — and any progress it has made — is left untouched.
     *
     * @param speedMetresPerSecond initial tangential speed; positive and finite
     * @return {@code CREATED} if built and installed, or {@code NOT_ASSESSABLE} if the resulting
     *         attempt cannot be assessed reliably at the observation cadence
     * @throws IllegalArgumentException if the speed is not positive and finite
     */
    public AttemptCreationResult configureAttempt(final double speedMetresPerSecond)
    {
        if (!Double.isFinite(speedMetresPerSecond) || speedMetresPerSecond <= 0.0)
        {
            throw new IllegalArgumentException("speedMetresPerSecond must be positive and finite, was: " + speedMetresPerSecond);
        }
        try
        {
            installAttempt(speedMetresPerSecond);
            return AttemptCreationResult.CREATED;
        }
        catch (final ScenarioAttemptNotAssessableException notAssessable)
        {
            return AttemptCreationResult.NOT_ASSESSABLE;
        }
    }

    /** Applies the single run/pause/resume transition legal in the current state. */
    public void toggleRun()
    {
        player.toggleRun();
    }

    /** Rebuilds a fresh attempt at the current orbital speed, discarding progress. */
    public void reset()
    {
        installAttempt(configuredSpeedMetresPerSecond);
    }

    /**
     * Sets the presentation speed.
     *
     * @param value simulated seconds requested per real second; positive and finite
     * @throws IllegalArgumentException if the value is not positive and finite
     */
    public void setSimulatedSecondsPerRealSecond(final double value)
    {
        player.setSimulatedSecondsPerRealSecond(value);
    }

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    public boolean canToggleRun()
    {
        return player.canToggleRun();
    }

    /** The scenario's circular speed, the reference for the learner's input. */
    public double circularSpeedMetresPerSecond()
    {
        return scenario.circularSpeedMetresPerSecond();
    }

    /**
     * The initial speed the installed attempt was built with, as a fraction of the circular speed.
     * This is the configured launch speed, not the body's instantaneous orbital speed during the run.
     */
    public double configuredSpeedFractionOfCircular()
    {
        return configuredSpeedMetresPerSecond / scenario.circularSpeedMetresPerSecond();
    }

    /**
     * Simulated seconds elapsed since the current attempt started, or empty while not started.
     */
    public OptionalDouble elapsedSimulatedSeconds()
    {
        return player.elapsedSimulatedSeconds();
    }

    /** The current learner guidance, or empty if none has been inferred yet. */
    public Optional<Hint> latestHint()
    {
        return hints.latestHint();
    }

    /** The current presentation speed, in simulated seconds per real second. */
    public double simulatedSecondsPerRealSecond()
    {
        return player.simulatedSecondsPerRealSecond();
    }

    /** The latest committed simulation state, for rendering. */
    public SimulationSnapshot snapshot()
    {
        return player.snapshot();
    }

    /** The current scenario lifecycle state. */
    public ScenarioLifecycleState state()
    {
        return player.state();
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    public Optional<ScenarioTerminationReason> terminationReason()
    {
        return player.terminationReason();
    }

    private void installAttempt(final double speedMetresPerSecond)
    {
        // Attempt construction occurs before any controller state is modified, so a construction or
        // assessability refusal leaves the previously installed attempt, hints and speed unchanged.
        final Level1Attempt attempt = scenario.createAttempt(speedMetresPerSecond);
        player.install(attempt.session());
        this.hints = attempt.hints();
        this.configuredSpeedMetresPerSecond = speedMetresPerSecond;
    }
}