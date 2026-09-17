package io.github.eugenp84.orbitalsim.app.controller;
import io.github.eugenp84.orbitalsim.app.composition.Level4AttemptFactory;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level4Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Coordinates the canonical L4 scenario Apophis encounter on behalf of the user interface.
 * <p>
 * The application composition boundary supplies a fresh attempt backed by the verified Horizons fixture.
 * Generic lifecycle and presentation behaviour is delegated to {@link ScenarioPlayer};
 * this controller adds only L4's body identifiers and live sampled encounter measurements.
 *
 * @author Eugeniu PLATITA
 */
public final class Level4Controller implements ScenarioControls
{
    private final Level4AttemptFactory attemptFactory;
    private final ScenarioPlayer player;
    private Level4Attempt attempt;

    /**
     * Creates a controller holding a fresh, not-yet-started canonical Level 4 attempt.
     *
     * @param attemptFactory application composition boundary; never null
     */
    public Level4Controller(final Level4AttemptFactory attemptFactory)
    {
        this.attemptFactory = Objects.requireNonNull(attemptFactory, "attemptFactory must not be null");
        this.attempt = attemptFactory.createAttempt();

        //----------
        this.player = new ScenarioPlayer(attempt.session());
    }

    /** Apophis body identifier in the current attempt. */
    public String apophisId() {
        return attempt.apophisId();
    }

    /**
     * Advances by the simulated time represented by the given wall-clock interval while running.
     *
     * @param wallClockSeconds elapsed real time for this frame; non-negative and finite
     */
    @Override
    public void advanceByWallClock(final double wallClockSeconds) {
        player.advanceByWallClock(wallClockSeconds);
    }

    /** Whether a run/pause/resume control should be offered; false only when terminated. */
    @Override
    public boolean canToggleRun() {
        return player.canToggleRun();
    }

    /** Current sampled Earth-Apophis centre-to-centre distance, in metres. */
    public double currentDistanceMetres()
    {
        return attempt.progressSource().currentDistanceMetres();
    }

    /** Earth body identifier in the current attempt. */
    public String earthId()
    {
        return attempt.earthId();
    }

    /** Simulated seconds elapsed since the attempt started, or empty while not started. */
    @Override
    public OptionalDouble elapsedSimulatedSeconds() {
        return player.elapsedSimulatedSeconds();
    }

    /** Authoritative initial epoch of the current attempt, as Julian Day TDB. */
    public double initialJdTdb()
    {
        return attempt.initialJdTdb();
    }

    /** Smallest sampled Earth-Apophis centre-to-centre distance observed so far, in metres. */
    public double minimumDistanceMetres()
    {
        return attempt.progressSource().minimumDistanceMetres();
    }

    /** Elapsed scenario time at the smallest sampled distance, in seconds. */
    public double minimumDistanceElapsedSeconds() {
        return attempt.progressSource().minimumDistanceElapsedSeconds();
    }

    /** Rebuilds a fresh canonical attempt, discarding accumulated encounter progress. */
    @Override
    public void reset()
    {
        final Level4Attempt replacement = attemptFactory.createAttempt();
        player.install(replacement.session());
        this.attempt = replacement;
    }

    /** Relative Earth-Apophis speed at the smallest sampled distance, in metres per second. */
    public double relativeSpeedAtMinimumMetresPerSecond()
    {
        return attempt.progressSource().relativeSpeedAtMinimumMetresPerSecond();
    }

    /**
     * Sets the presentation speed without changing Level 4's fixed numerical timestep.
     *
     * @param value simulated seconds requested per real second; positive and finite
     */
    @Override
    public void setSimulatedSecondsPerRealSecond(final double value)
    {
        player.setSimulatedSecondsPerRealSecond(value);
    }

    /** Current presentation speed, in simulated seconds per real second. */
    @Override
    public double simulatedSecondsPerRealSecond()
    {
        return player.simulatedSecondsPerRealSecond();
    }

    /** Latest committed simulation state for rendering. */
    @Override
    public SimulationSnapshot snapshot()
    {
        return player.snapshot();
    }

    /** Current scenario lifecycle state. */
    @Override
    public ScenarioLifecycleState state()
    {
        return player.state();
    }

    /** Sun body identifier in the current attempt. */
    public String sunId()
    {
        return attempt.sunId();
    }

    /** Why the scenario terminated, or empty if it has not terminated. */
    @Override
    public Optional<ScenarioTerminationReason> terminationReason()
    {
        return player.terminationReason();
    }

    /** Applies the single run/pause/resume transition legal in the current state. */
    @Override
    public void toggleRun() {
        player.toggleRun();
    }
}
