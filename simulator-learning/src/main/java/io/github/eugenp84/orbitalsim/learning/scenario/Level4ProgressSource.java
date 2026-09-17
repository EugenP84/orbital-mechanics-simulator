package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * Read-only encounter measurements accumulated during a Level 4 Apophis attempt.
 * <p>
 * The minimum is a sampled minimum at committed learning observations.
 * It is not presented as a continuous-time closest approach. With Level 4's frozen one-step observation cadence, every integration boundary is observed.
 *
 * @author Eugeniu PLATITA
 */
public interface Level4ProgressSource
{
    /** Current sampled Earth-Apophis centre-to-centre distance, in metres. */
    double currentDistanceMetres();

    /** Smallest sampled Earth-Apophis centre-to-centre distance observed so far, in metres. */
    double minimumDistanceMetres();

    /** Elapsed scenario time at the smallest sampled distance, in seconds. */
    double minimumDistanceElapsedSeconds();

    /** Earth-Apophis relative speed at the smallest sampled distance, in metres per second. */
    double relativeSpeedAtMinimumMetresPerSecond();
}
