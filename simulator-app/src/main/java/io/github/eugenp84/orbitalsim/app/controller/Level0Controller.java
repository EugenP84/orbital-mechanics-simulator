package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level0Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level0Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Coordinates a running Level 0 (Planet Creation) scenario on behalf of the user interface.
 * <p>
 * Lifecycle and presentation behaviour shared by every level — run/pause, wall-clock-to-simulated advancement, snapshot and lifecycle access, presentation speed — is delegated to a {@link ScenarioPlayer}.
 * This controller adds only Level 0's configuration surface:
 * building an attempt from the learner's chosen mass, radius, distance and tangential speed, and exposing those values plus the circular-speed reference for the current distance.
 * <p>
 * On construction an Earth-like default attempt is installed immediately, so the view always has a committed state to render before the learner configures anything. The default tangential speed is
 * derived from the default configuration through {@link Level0Scenario}'s own physical relationship rather than hard-coded, so it cannot drift from the model.
 *
 * @author Eugeniu PLATITA
 */
public final class Level0Controller implements ScenarioControls
{
    private static final double DEFAULT_MASS_KILOGRAMS = PhysicalConstants.EARTH_MASS;
    private static final double DEFAULT_RADIUS_METRES = PhysicalConstants.EARTH_RADIUS;
    private static final double DEFAULT_DISTANCE_METRES = PhysicalConstants.ASTRONOMICAL_UNIT;
    private final Level0Scenario scenario;
    private final ScenarioPlayer player;
    private double configuredMassKilograms;
    private double configuredRadiusMetres;
    private double configuredDistanceMetres;
    private double configuredTangentialSpeedMetresPerSecond;
    private double circularSpeedReferenceMetresPerSecond;

    /**
     * Creates a controller holding a fresh, not-yet-started Earth-like default attempt, launched at the circular speed for the default distance, and the default presentation rate.
     *
     * @param scenario the Level 0 scenario used to build attempts; never null
     */
    public Level0Controller(final Level0Scenario scenario)
    {
        this.scenario = Objects.requireNonNull(scenario, "scenario must not be null");
        final double defaultSpeed = scenario.circularSpeedMetresPerSecond(DEFAULT_MASS_KILOGRAMS, DEFAULT_DISTANCE_METRES);
        final Level0Attempt attempt = scenario.createAttempt(DEFAULT_MASS_KILOGRAMS, DEFAULT_RADIUS_METRES, DEFAULT_DISTANCE_METRES, defaultSpeed);
        this.player = new ScenarioPlayer(attempt.session());
        recordConfiguration(attempt);
    }

    /**
     * Advances by the simulated time corresponding to the given wall-clock interval, but only while running.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if the argument is negative or non-finite, or if it and the presentation speed produce a non-finite duration
     */
    public void advanceByWallClock(final double wallClockSeconds) {
        player.advanceByWallClock(wallClockSeconds);
    }

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    public boolean canToggleRun() {
        return player.canToggleRun();
    }

    /** The circular speed for the current distance, a read-only reference for the learner. */
    public double circularSpeedReferenceMetresPerSecond()
    {
        return circularSpeedReferenceMetresPerSecond;
    }

    /**
     * Builds and installs a fresh, not-yet-started attempt from the given planet parameters.
     * <p>
     * Level 0 has no assessability refusal: an invalid configuration throws, and construction occurs before any controller state is modified, so a rejected configuration leaves the previous attempt unchanged.
     *
     * @param massKilograms       the planet's mass; positive and finite
     * @param radiusMetres        the planet's radius; positive and finite
     * @param distanceMetres      the star–planet distance; positive and finite, and clear of the star
     * @param tangentialSpeedMetresPerSecond the initial tangential speed; positive and finite
     * @throws IllegalArgumentException if any argument is invalid or the planet would overlap the star
     */
    public void configureAttempt(
            final double massKilograms,
            final double radiusMetres,
            final double distanceMetres,
            final double tangentialSpeedMetresPerSecond)
    {
        final Level0Attempt attempt = scenario.createAttempt(massKilograms, radiusMetres, distanceMetres, tangentialSpeedMetresPerSecond);
        player.install(attempt.session());
        recordConfiguration(attempt);
    }

    /** The currently configured star–planet distance, in metres. */
    public double configuredDistanceMetres() {
        return configuredDistanceMetres;
    }

    /** The currently configured planet mass, in kilograms. */
    public double configuredMassKilograms()
    {
        return configuredMassKilograms;
    }

    /** The currently configured planet radius, in metres. */
    public double configuredRadiusMetres() {
        return configuredRadiusMetres;
    }

    /** The currently configured initial tangential speed, in metres per second. */
    public double configuredTangentialSpeedMetresPerSecond()
    {
        return configuredTangentialSpeedMetresPerSecond;
    }

    /**
     * Simulated seconds elapsed since the current attempt started, or empty while not started.
     */
    public OptionalDouble elapsedSimulatedSeconds()
    {
        return player.elapsedSimulatedSeconds();
    }

    /** Rebuilds a fresh attempt from the currently configured parameters, discarding progress. */
    public void reset()
    {
        final Level0Attempt attempt = scenario.createAttempt(
                configuredMassKilograms, configuredRadiusMetres,
                configuredDistanceMetres, configuredTangentialSpeedMetresPerSecond);
        player.install(attempt.session());
        recordConfiguration(attempt);
    }

    /**
     * Sets the presentation speed.
     *
     * @param value simulated seconds requested per real second; positive and finite
     * @throws IllegalArgumentException if the value is not positive and finite
     */
    public void setSimulatedSecondsPerRealSecond(final double value) {
        player.setSimulatedSecondsPerRealSecond(value);
    }

    /** The current presentation speed, in simulated seconds per real second. */
    public double simulatedSecondsPerRealSecond()
    {
        return player.simulatedSecondsPerRealSecond();
    }

    /** The latest committed simulation state, for rendering. */
    public SimulationSnapshot snapshot() {
        return player.snapshot();
    }

    /** The current scenario lifecycle state. */
    public ScenarioLifecycleState state()
    {
        return player.state();
    }

    /**
     * Why the scenario terminated, or empty if it has not terminated.
     * Level 0 has no learner-failure path, but the session may still terminate for an infrastructure reason such as {@link ScenarioTerminationReason#SIMULATION_BREAKDOWN}, so the result is exposed for symmetry.
     */
    public Optional<ScenarioTerminationReason> terminationReason()
    {
        return player.terminationReason();
    }

    /** Applies the single run/pause/resume transition legal in the current state. */
    public void toggleRun() {
        player.toggleRun();
    }

    private void recordConfiguration(final Level0Attempt attempt)
    {
        this.configuredMassKilograms = attempt.planetMassKilograms();
        this.configuredRadiusMetres = attempt.planetRadiusMetres();
        this.configuredDistanceMetres = attempt.orbitalDistanceMetres();
        this.configuredTangentialSpeedMetresPerSecond = attempt.tangentialSpeedMetresPerSecond();
        this.circularSpeedReferenceMetresPerSecond = attempt.circularSpeedMetresPerSecond();
    }
}