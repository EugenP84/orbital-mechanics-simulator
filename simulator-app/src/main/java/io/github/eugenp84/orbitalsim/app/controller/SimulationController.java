package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioAttemptNotAssessableException;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioSession;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import io.github.eugenp84.orbitalsim.learning.scenario.Hint;
import io.github.eugenp84.orbitalsim.learning.scenario.HintSource;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Attempt;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Coordinates a running Level 1 scenario on behalf of the user interface.
 * <p>
 * This is the JavaFX-free application logic — lifecycle transitions, attempt configuration, reset,
 * and the presentation-speed conversion — so it can be unit-tested like every other layer.
 * The view calls these methods and reads the lifecycle state; it never drives the {@link ScenarioSession}
 * directly, so the session stays the sole advancement boundary (ADR-008 p.4).
 * <p>
 * Presentation speed is a wall-clock-to-simulated multiplier and never changes the engine's fixed
 * numerical timestep (ADR-005 §8). Orbital speed is a different quantity entirely — the learner's
 * chosen initial tangential speed — which {@link #configureAttempt(double)} turns into a new attempt.
 * <p>
 * Reset and reconfiguration build a new attempt rather than rewinding the session, because the
 * session has no legitimate transition back from a started or terminal state (ADR-008 §3).
 * A reconfiguration that cannot be assessed is refused and the previous attempt left intact;
 * an assessable one replaces it atomically.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationController
{
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND = 14.0 * SECONDS_PER_DAY;

    private final Level1Scenario scenario;

    private ScenarioSession session;
    private double simulatedSecondsPerRealSecond;
    private double configuredSpeedMetresPerSecond;
    private HintSource hints;

    /**
     * Creates a controller holding a fresh, not-yet-started attempt at the scenario's circular
     * speed, and the default presentation rate.
     *
     * @param scenario the Level 1 scenario used to build attempts; never null
     */
    public SimulationController(final Level1Scenario scenario)
    {
        this.scenario = Objects.requireNonNull(scenario, "scenario must not be null");
        this.simulatedSecondsPerRealSecond = DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND;
        installAttempt(scenario.circularSpeedMetresPerSecond());
    }

    /**
     * Advances by the simulated time corresponding to the given wall-clock interval, but only while
     * running. The argument is validated independently of lifecycle state, and the derived duration
     * is checked before it reaches the learning layer.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if the argument is negative or non-finite, or if it and the
     *         presentation speed produce a non-finite duration
     */
    public void advanceByWallClock(final double wallClockSeconds)
    {
        if (!Double.isFinite(wallClockSeconds) || wallClockSeconds < 0.0)
        {
            throw new IllegalArgumentException(
                    "wallClockSeconds must be non-negative and finite, was: " + wallClockSeconds);
        }
        if (session.state() != ScenarioLifecycleState.RUNNING)
        {
            return;
        }
        final double simulatedSeconds = wallClockSeconds * simulatedSecondsPerRealSecond;
        if (!Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException(
                    "wall-clock interval and presentation speed produce a non-finite duration: "
                            + wallClockSeconds + " * " + simulatedSecondsPerRealSecond);
        }
        session.advanceBy(simulatedSeconds);
    }

    /**
     * Builds a fresh, not-yet-started attempt at the given orbital speed and, only if it can be
     * assessed, installs it. On refusal the previous attempt — and any progress it has made — is
     * left untouched.
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
            throw new IllegalArgumentException(
                    "speedMetresPerSecond must be positive and finite, was: " + speedMetresPerSecond);
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
        final ScenarioLifecycleState current = session.state();
        if (current == ScenarioLifecycleState.NOT_STARTED)
        {
            session.start();
        }
        else if (current == ScenarioLifecycleState.RUNNING)
        {
            session.pause();
        }
        else if (current == ScenarioLifecycleState.PAUSED)
        {
            session.resume();
        }
        // COMPLETED and FAILED are terminal: no transition exists; Reset builds anew.
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
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(
                    "simulated-seconds-per-real-second must be positive and finite, was: " + value);
        }
        this.simulatedSecondsPerRealSecond = value;
    }

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    public boolean canToggleRun()
    {
        return !isTerminated();
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
     * Simulated seconds elapsed since the current attempt started, or empty while not started — a
     * real state (no baseline exists yet), not an error or a zero.
     */
    public OptionalDouble elapsedSimulatedSeconds()
    {
        if (session.state() == ScenarioLifecycleState.NOT_STARTED)
        {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(session.elapsedScenarioSeconds());
    }

    /** The current presentation speed, in simulated seconds per real second. */
    public double simulatedSecondsPerRealSecond()
    {
        return simulatedSecondsPerRealSecond;
    }

    /** The latest committed simulation state, for rendering. */
    public SimulationSnapshot snapshot()
    {
        return session.snapshot();
    }

    /** The current scenario lifecycle state. */
    public ScenarioLifecycleState state()
    {
        return session.state();
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    public Optional<ScenarioTerminationReason> terminationReason()
    {
        return session.terminationReason();
    }

    private void installAttempt(final double speedMetresPerSecond)
    {
        // createAttempt throws before either assignment if the attempt cannot be built,
        // so a refused reconfiguration leaves session, hints and speed unchanged.
        final Level1Attempt attempt = scenario.createAttempt(speedMetresPerSecond);
        this.session = attempt.session();
        this.hints = attempt.hints();
        this.configuredSpeedMetresPerSecond = speedMetresPerSecond;
    }

    private boolean isTerminated()
    {
        final ScenarioLifecycleState current = session.state();
        return current == ScenarioLifecycleState.COMPLETED
                || current == ScenarioLifecycleState.FAILED;
    }

    /** The current learner guidance, or empty if none has been inferred yet. */
    public Optional<Hint> latestHint()
    {
        return hints.latestHint();
    }
}