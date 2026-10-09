package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The level-agnostic surface through which the user interface drives any scenario: the lifecycle,
 * presentation-speed, snapshot and reset operations shared by every level.
 * <p>
 * This is the view-facing counterpart to {@link ScenarioPlayer}: where {@code ScenarioPlayer} is the shared implementation of lifecycle/presentation behaviour,
 * this interface is the shared surface a generic screen depends on, so one animation loop and one set of common controls can drive both the Level 0 and Level 1 controllers without knowing which level is running.
 * Level-specific configuration and pedagogy — orbital speed, hints, planet parameters — are deliberately absent here and remain on each concrete controller.
 *
 * @author Eugeniu PLATITA
 */
public interface ScenarioControls
{
    /**
     * Advances by the simulated time corresponding to the given wall-clock interval, but only while running.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if the argument is negative or non-finite, or if it and the presentation speed produce a non-finite duration
     */
    void advanceByWallClock(double wallClockSeconds);

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    boolean canToggleRun();

    /** Simulated seconds elapsed since the current attempt started, or empty while not started. */
    OptionalDouble elapsedSimulatedSeconds();

    /** Rebuilds a fresh attempt from the currently configured parameters, discarding progress. */
    void reset();

    /**
     * Sets the presentation speed.
     *
     * @param value simulated seconds requested per real second; positive and finite
     * @throws IllegalArgumentException if the value is not positive and finite
     */
    void setSimulatedSecondsPerRealSecond(double value);

    /** The current presentation speed, in simulated seconds per real second. */
    double simulatedSecondsPerRealSecond();

    /** The latest committed simulation state, for rendering. */
    SimulationSnapshot snapshot();

    /** The current scenario lifecycle state. */
    ScenarioLifecycleState state();

    /** Why the scenario terminated, or empty if it has not terminated. */
    Optional<ScenarioTerminationReason> terminationReason();

    /** Applies the single run/pause/resume transition legal in the current state. */
    void toggleRun();
}