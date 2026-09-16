package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level3Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level3Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;


/**
 * Coordinates a running Level 3 (Gravitational Perturbation) scenario on behalf of the user interface.
 * <p>
 * Lifecycle and presentation behaviour shared by every level is delegated to a {@link ScenarioPlayer}.
 * This controller adds only Level 3's configuration surface — building an attempt from the learner's
 * chosen perturber mass and initial stellar distance, exposing those values and the envelope bounds —
 * and a read-through to the running perturbation measurement (maximum target eccentricity). Level 3
 * has no hints and no assessability refusal: an out-of-envelope configuration is a construction error
 * the interface is expected to prevent, so {@link #configureAttempt} has no result value and lets such
 * an error propagate.
 * <p>
 * On construction a moderate default attempt from within the calibrated envelope is installed
 * immediately, so the view always has a committed state to render. The default (1.5 Jupiter masses at
 * 1.65 AU) is a deliberate application choice of a visible-but-modest starting experiment, not a value
 * inferred from calibration. Reset and reconfiguration build a new attempt rather than rewinding, so
 * the accumulated maximum eccentricity returns to the new attempt's seeded initial value.
 *
 * @author Eugeniu PLATITA
 */
public final class Level3Controller implements ScenarioControls
{
    private static final double JUPITER_MASS = 317.8 * PhysicalConstants.EARTH_MASS;
    private static final double DEFAULT_PERTURBER_MASS = 1.5 * JUPITER_MASS;
    private static final double DEFAULT_PERTURBER_DISTANCE = 1.65 * PhysicalConstants.ASTRONOMICAL_UNIT;
    //private static final double ASTRONOMICAL_UNIT = 1.495_978_707e11;         // m

    private final Level3Scenario scenario;
    private final ScenarioPlayer player;

    private Level3Attempt attempt;
    private double configuredPerturberMassKilograms;
    private double configuredPerturberDistanceMetres;

    /**
     * Creates a controller holding a fresh, not-yet-started moderate default attempt.
     *
     * @param scenario the Level 3 scenario used to build attempts; never null
     */
    public Level3Controller(final Level3Scenario scenario)
    {
        this.scenario = Objects.requireNonNull(scenario, "scenario must not be null");
        this.attempt = scenario.createAttempt(DEFAULT_PERTURBER_MASS, DEFAULT_PERTURBER_DISTANCE);
        this.player = new ScenarioPlayer(attempt.session());
        this.configuredPerturberMassKilograms = DEFAULT_PERTURBER_MASS;
        this.configuredPerturberDistanceMetres = DEFAULT_PERTURBER_DISTANCE;
    }

    /**
     * Advances by the simulated time corresponding to the given wall-clock interval, but only while
     * running.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     * @throws IllegalArgumentException if the argument is negative or non-finite, or if it and the
     *         presentation speed produce a non-finite duration
     */
    @Override
    public void advanceByWallClock(final double wallClockSeconds)
    {
        player.advanceByWallClock(wallClockSeconds);
    }

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    @Override
    public boolean canToggleRun()
    {
        return player.canToggleRun();
    }

    /**
     * Builds and installs a fresh, not-yet-started attempt from the given perturber configuration.
     * <p>
     * Construction occurs before any controller state is modified, so an out-of-envelope configuration
     * throws and leaves the previous attempt unchanged.
     *
     * @param perturberMassKilograms  the perturbing planet's mass; within the supported envelope
     * @param perturberDistanceMetres the perturber's initial stellar distance; within the envelope
     * @throws IllegalArgumentException if either argument is outside the supported learner envelope
     */
    public void configureAttempt(
            final double perturberMassKilograms, final double perturberDistanceMetres)
    {
        final Level3Attempt replacement =
                scenario.createAttempt(perturberMassKilograms, perturberDistanceMetres);
        player.install(replacement.session());
        this.attempt = replacement;
        this.configuredPerturberMassKilograms = perturberMassKilograms;
        this.configuredPerturberDistanceMetres = perturberDistanceMetres;
    }

    /** The currently configured perturber distance, in metres. */
    public double configuredPerturberDistanceMetres()
    {
        return configuredPerturberDistanceMetres;
    }

    /** The currently configured perturber mass, in kilograms. */
    public double configuredPerturberMassKilograms()
    {
        return configuredPerturberMassKilograms;
    }

    /**
     * Simulated seconds elapsed since the current attempt started, or empty while not started.
     */
    @Override
    public OptionalDouble elapsedSimulatedSeconds()
    {
        return player.elapsedSimulatedSeconds();
    }

    /** The greatest supported perturber distance, in metres. */
    public double maxPerturberDistanceMetres()
    {
        return scenario.maxPerturberDistanceMetres();
    }

    /** The greatest supported perturber mass, in kilograms. */
    public double maxPerturberMassKilograms()
    {
        return scenario.maxPerturberMassKilograms();
    }

    /**
     * The greatest osculating target eccentricity observed so far — the running perturbation measure.
     * A finite-horizon result, not a stability claim.
     */
    public double maxEccentricity()
    {
        return attempt.progressSource().maxEccentricity();
    }

    /** The least supported perturber distance, in metres. */
    public double minPerturberDistanceMetres()
    {
        return scenario.minPerturberDistanceMetres();
    }

    /** The least supported perturber mass, in kilograms. */
    public double minPerturberMassKilograms()
    {
        return scenario.minPerturberMassKilograms();
    }

    /** Rebuilds a fresh attempt from the current configuration, discarding progress. */
    @Override
    public void reset()
    {
        configureAttempt(configuredPerturberMassKilograms, configuredPerturberDistanceMetres);
    }

    /**
     * Sets the presentation speed.
     *
     * @param value simulated seconds requested per real second; positive and finite
     * @throws IllegalArgumentException if the value is not positive and finite
     */
    @Override
    public void setSimulatedSecondsPerRealSecond(final double value)
    {
        player.setSimulatedSecondsPerRealSecond(value);
    }

    /** The current presentation speed, in simulated seconds per real second. */
    @Override
    public double simulatedSecondsPerRealSecond()
    {
        return player.simulatedSecondsPerRealSecond();
    }

    /** The latest committed simulation state, for rendering. */
    @Override
    public SimulationSnapshot snapshot()
    {
        return player.snapshot();
    }

    /** The current scenario lifecycle state. */
    @Override
    public ScenarioLifecycleState state()
    {
        return player.state();
    }

    /**
     * The stable identifier of the fixed Earth-like target in the current attempt.
     *
     * @return the target body's identifier
     */
    public String targetId()
    {
        return attempt.targetId();
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    @Override
    public Optional<ScenarioTerminationReason> terminationReason()
    {
        return player.terminationReason();
    }

    /** Applies the single run/pause/resume transition legal in the current state. */
    @Override
    public void toggleRun()
    {
        player.toggleRun();
    }
}