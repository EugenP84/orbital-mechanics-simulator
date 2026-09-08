package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioSession;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import java.util.Optional;
import java.util.Objects;
import java.util.OptionalDouble;


/**
 * Coordinates a running Level 1 scenario on behalf of the user interface.
 * <p>
 * This is the one piece of genuine application logic in the app module — lifecycle transitions, reset, and the presentation-speed conversion —
 * deliberately kept free of any JavaFX type so it can be unit-tested like every other layer.
 * The view calls these methods and reads the current lifecycle state to decide what it may offer;
 * it never drives the {@link ScenarioSession} directly, so the session stays the sole advancement boundary (ADR-008 p.4).
 * <p>
 * Presentation speed is a multiplier from wall-clock seconds to requested simulated seconds.
 * It never changes the engine's fixed numerical timestep (ADR-005 p8);
 * it only changes how much simulated time a given real interval requests.
 * <p>
 * Reset follows the established attempt lifecycle:
 * it builds a new attempt rather than rewinding the session, because the session has no legitimate
 * transition back from a started or terminal state (ADR-008 p.3).
 * The presentation speed is a controller-level setting and survives a reset.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationController
{
    private static final double SECONDS_PER_DAY = 86_400.0;

    // Fourteen simulated days per real second by default: ~56 six-hour steps per real second, visibly smooth without touching the numerical timestep.
    private static final double DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND = 14.0 * SECONDS_PER_DAY;

    private final Level1Scenario scenario;
    private ScenarioSession session;
    private double simulatedSecondsPerRealSecond;

    /**
     * Creates a controller holding a fresh, not-yet-started Level 1 attempt at the scenario's analytic circular speed, and the default presentation rate.
     *
     * @param scenario the Level 1 scenario used to build attempts; never null
     */
    public SimulationController(final Level1Scenario scenario)
    {
        this.scenario = Objects.requireNonNull(scenario, "scenario must not be null");
        this.simulatedSecondsPerRealSecond = DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND;
        this.session = newAttemptSession();
    }

    /**
     * Advances the scenario by the simulated time corresponding to the given wall-clock interval, but only while running. When not running the request is
     * ignored after its argument is validated, so a paused or terminated scenario accumulates no time.
     * The argument's validity does not depend on lifecycle state.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if {@code wallClockSeconds} is negative or non-finite, or if it and the presentation speed produce a non-finite simulated duration
     */
    public void advanceByWallClock(final double wallClockSeconds)
    {
        if (!Double.isFinite(wallClockSeconds) || wallClockSeconds < 0.0)
        {
            throw new IllegalArgumentException("wallClockSeconds must be non-negative and finite, was: " + wallClockSeconds);
        }
        if (session.state() != ScenarioLifecycleState.RUNNING) {
            return;
        }
        final double simulatedSeconds = wallClockSeconds * simulatedSecondsPerRealSecond;
        if (!Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException("wall-clock interval and presentation speed produce a non-finite duration: " + wallClockSeconds + " * " + simulatedSecondsPerRealSecond);
        }
        session.advanceBy(simulatedSeconds);
    }

    /**
     * Applies the single run/pause/resume transition legal in the current state.
     * Terminal scenarios have no such transition, so the call is then a safe no-op;
     * the view also disables the control in that case.
     */
    public void toggleRun()
    {
        final ScenarioLifecycleState current = session.state();
        if (current == ScenarioLifecycleState.NOT_STARTED)
        {
            session.start();
        }
        else if (current == ScenarioLifecycleState.RUNNING) {
            session.pause();
        }
        else if (current == ScenarioLifecycleState.PAUSED) {
            session.resume();
        }
        // COMPLETED and FAILED are terminal: no transition exists; Reset builds anew.
    }

    /** Replaces the current attempt with a fresh, not-yet-started one. */
    public void reset() {
        this.session = newAttemptSession();
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
            throw new IllegalArgumentException("simulated-seconds-per-real-second must be positive and finite, was: " + value);
        }
        this.simulatedSecondsPerRealSecond = value;
    }

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    public boolean canToggleRun()
    {
        return !isTerminated();
    }

    /** The current presentation speed, in simulated seconds per real second. */
    public double simulatedSecondsPerRealSecond()
    {
        return simulatedSecondsPerRealSecond;
    }

    /** The latest committed simulation state, for rendering. */
    public SimulationSnapshot snapshot() {
        return session.snapshot();
    }

    /** The current scenario lifecycle state. */
    public ScenarioLifecycleState state() {
        return session.state();
    }

    private boolean isTerminated()
    {
        final ScenarioLifecycleState current = session.state();
        return current == ScenarioLifecycleState.COMPLETED
                || current == ScenarioLifecycleState.FAILED;
    }

    private ScenarioSession newAttemptSession()
    {
        return scenario.createAttempt(scenario.circularSpeedMetresPerSecond()).session();
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    public Optional<ScenarioTerminationReason> terminationReason()
    {
        return session.terminationReason();
    }

    /**
     * Simulated seconds elapsed since the current attempt started, or empty while not started.
     * Empty is a real state — a not-yet-started attempt has no baseline from which to measure — not an error or a zero.
     */
    public OptionalDouble elapsedSimulatedSeconds()
    {
        if (session.state() == ScenarioLifecycleState.NOT_STARTED)
        {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(session.elapsedScenarioSeconds());
    }
}