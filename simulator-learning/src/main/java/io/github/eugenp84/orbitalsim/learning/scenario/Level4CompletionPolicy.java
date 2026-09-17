package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import java.util.Objects;

/**
 * Observation-based completion and measurement policy for Level 4 (Real NEO Close Approach).
 * <p>
 * Level 4 has no pedagogical failure threshold and does not steer the simulated trajectory toward an external reference.
 * It completes when the fixed observation horizon is reached while tracking the Earth-Apophis sampled separation minimum and the relative speed at that sample.
 * Numerical breakdown remains the responsibility of {@link ScenarioSession}.
 * <p>
 * The initial snapshot seeds all measurements, so the progress source is meaningful before the session starts.
 * A new minimum replaces the previous one only when its sampled distance is strictly smaller.
 * Equal-distance samples retain the earlier occurrence.
 *
 * @author Eugeniu PLATITA
 */
public final class Level4CompletionPolicy implements ScenarioCompletionPolicy, Level4ProgressSource
{
    private final String apophisId;
    private final String earthId;
    private final double horizonSeconds;
    private double currentDistanceMetres;
    private double minimumDistanceElapsedSeconds;
    private double minimumDistanceMetres;
    private double relativeSpeedAtMinimumMetresPerSecond;

    /**
     * @param initialSnapshot initial committed Sun-Earth-Apophis state
     * @param earthId         Earth body id
     * @param apophisId       Apophis body id
     * @param horizonSeconds  fixed experiment horizon in seconds
     */
    public Level4CompletionPolicy(
            final SimulationSnapshot initialSnapshot,
            final String earthId,
            final String apophisId,
            final double horizonSeconds)
    {
        Objects.requireNonNull(initialSnapshot, "initialSnapshot must not be null");
        this.earthId = requireIdentifier(earthId, "earthId");
        this.apophisId = requireIdentifier(apophisId, "apophisId");
        if (this.earthId.equals(this.apophisId))
        {
            throw new IllegalArgumentException("earthId and apophisId must be distinct");
        }
        this.horizonSeconds = requirePositiveFinite(horizonSeconds, "horizonSeconds");

        final BodySnapshot earth = bodyById(initialSnapshot, this.earthId);
        final BodySnapshot apophis = bodyById(initialSnapshot, this.apophisId);
        currentDistanceMetres = distanceMetres(earth, apophis);
        minimumDistanceMetres = currentDistanceMetres;
        minimumDistanceElapsedSeconds = 0.0;
        relativeSpeedAtMinimumMetresPerSecond = relativeSpeedMetresPerSecond(earth, apophis);
    }

    @Override
    public double currentDistanceMetres()
    {
        return currentDistanceMetres;
    }

    @Override
    public ScenarioEvaluation evaluate(final ScenarioObservation observation)
    {
        Objects.requireNonNull(observation, "observation must not be null");
        final BodySnapshot earth = bodyById(observation.snapshot(), earthId);
        final BodySnapshot apophis = bodyById(observation.snapshot(), apophisId);

        currentDistanceMetres = distanceMetres(earth, apophis);
        if (currentDistanceMetres < minimumDistanceMetres)
        {
            minimumDistanceMetres = currentDistanceMetres;
            minimumDistanceElapsedSeconds = observation.elapsedScenarioSeconds();
            relativeSpeedAtMinimumMetresPerSecond = relativeSpeedMetresPerSecond(earth, apophis);
        }

        if (observation.elapsedScenarioSeconds() >= horizonSeconds)
        {
            return ScenarioEvaluation.COMPLETE;
        }
        return ScenarioEvaluation.CONTINUE;
    }

    @Override
    public double minimumDistanceElapsedSeconds()
    {
        return minimumDistanceElapsedSeconds;
    }

    @Override
    public double minimumDistanceMetres()
    {
        return minimumDistanceMetres;
    }

    @Override
    public double relativeSpeedAtMinimumMetresPerSecond()
    {
        return relativeSpeedAtMinimumMetresPerSecond;
    }

    private static BodySnapshot bodyById(final SimulationSnapshot snapshot, final String id)
    {
        for (final BodySnapshot body : snapshot.bodies())
        {
            if (id.equals(body.definition().id()))
            {
                return body;
            }
        }
        throw new IllegalArgumentException("snapshot does not contain body id: " + id);
    }

    private static double distanceMetres(final BodySnapshot first, final BodySnapshot second)
    {
        return first.state().position().distanceTo(second.state().position());
    }

    private static double relativeSpeedMetresPerSecond(
            final BodySnapshot first, final BodySnapshot second)
    {
        return first.state().velocity().subtract(second.state().velocity()).magnitude();
    }

    private static String requireIdentifier(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static double requirePositiveFinite(final double value, final String name)
    {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
        return value;
    }
}
