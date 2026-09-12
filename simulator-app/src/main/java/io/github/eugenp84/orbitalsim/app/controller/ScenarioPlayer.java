package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioSession;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Drives one scenario session on behalf of the user interface:
 * the lifecycle and presentation behaviour that every level shares, independent of how a level is configured or assessed.
 * <p>
 * This is the common core proven necessary once a second, structurally different scenario (Level 0)
 * required exactly the same run/pause/reset loop, wall-clock-to-simulated-time conversion, and snapshot/lifecycle access that Level 1 already used.
 * It is deliberately free of any level-specific concept — orbital speed, hints, assessment outcome — which remain the responsibility of each level's own controller.
 * It is also JavaFX-free, so it is unit-tested like every other layer, and it never lets the view drive the {@link ScenarioSession} directly:
 * the session stays the sole advancement boundary (ADR-008 p.4).
 * <p>
 * Presentation speed is a wall-clock-to-simulated multiplier and never changes the engine's fixed numerical timestep (ADR-005 p8).
 * <p>
 * A level controller {@link #install(ScenarioSession) installs} a freshly built session whenever it configures or resets an attempt. Installation replaces the current session wholesale.
 * the player carries no lifecycle or elapsed-time state of its own across an install (all such state lives in  the session),
 * while the presentation-speed multiplier — a view setting, not attempt state — is deliberately retained so a reset does not reset the learner's chosen playback speed.
 *
 * @author Eugeniu PLATITA
 */
public final class ScenarioPlayer
{
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND = 14.0 * SECONDS_PER_DAY;


    private ScenarioSession session;
    private double simulatedSecondsPerRealSecond = DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND;

    /**
     * Creates a player driving the given session at the default presentation rate.
     *
     * @param session the session to drive; never null
     */
    public ScenarioPlayer(final ScenarioSession session)
    {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    /**
     * Advances by the simulated time corresponding to the given wall-clock interval, but only while running. T
     * he argument is validated independently of lifecycle state, and the derived duration is checked before it reaches the learning layer.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if the argument is negative or non-finite, or if it and the
     *         presentation speed produce a non-finite duration
     */
    public void advanceByWallClock(final double wallClockSeconds)
    {
        if (!Double.isFinite(wallClockSeconds) || wallClockSeconds < 0.0)
        {
            throw new IllegalArgumentException("wallClockSeconds must be non-negative and finite, was: " + wallClockSeconds);
        }
        if (session.state() != ScenarioLifecycleState.RUNNING)
        {
            return;
        }
        final double simulatedSeconds = wallClockSeconds * simulatedSecondsPerRealSecond;
        if (!Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException("wall-clock interval and presentation speed produce a non-finite duration: "
                            + wallClockSeconds + " * " + simulatedSecondsPerRealSecond);
        }
        session.advanceBy(simulatedSeconds);
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
        // COMPLETED and FAILED are terminal: no transition exists; a reset installs a new session.
    }

    /**
     * Installs a freshly built session, replacing the current one.
     * Package-private: only a level controller in this package installs a session; the view never manipulates player composition.
     * The presentation-speed multiplier is retained across the install; all lifecycle and elapsed-time state comes from the new session.
     *
     * @param newSession the session to drive from now on; never null
     */
    void install(final ScenarioSession newSession)
    {
        this.session = Objects.requireNonNull(newSession, "newSession must not be null");
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

    private boolean isTerminated()
    {
        final ScenarioLifecycleState current = session.state();
        return current == ScenarioLifecycleState.COMPLETED
                || current == ScenarioLifecycleState.FAILED;
    }
}