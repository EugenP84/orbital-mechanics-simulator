package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateAnalysis;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import java.util.Objects;

/**
 * The completion policy for L3 (Gravitational Perturbation):
 * a controlled three-body experiment that reaches a fixed observation horizon while measuring how strongly the target planet's orbit is perturbed.
 * <p>
 * Per the Level 3 behavioural specification, this policy is deliberately simpler than Level 1's:
 * it has no revolution counting, no sampling-adequacy gate, no energy-integrity check, and no ejection
 * classifier (calibration established no numerically robust ejection threshold within or immediately
 * beyond the learner envelope). Its decisions are:
 * <ul>
 *   <li><strong>FAIL</strong> on physical surface contact — the target intersecting the star, or the
 *       target intersecting the perturbing planet — using centre-to-centre distance against the sum
 *       of physical radii read from each body's definition. Detection is performed at learning
 *       observations, so a contact beginning and ending entirely between two observations is not
 *       guaranteed to be detected; the calibrated learner envelope stays far from surface contact
 *       (minimum target–star ≈ 0.787 AU, target–perturber ≈ 0.257 AU), so this is a defensive
 *       condition rather than an outcome the envelope reaches.</li>
 *   <li><strong>COMPLETE</strong> when elapsed scenario time reaches the observation horizon without a
 *       physical failure. Completion means the experiment ran to its horizon, not that the orbit is
 *       stable.</li>
 *   <li><strong>CONTINUE</strong> otherwise.</li>
 * </ul>
 * Numerical breakdown is handled by the session before the policy is consulted, so it is not
 * duplicated here.
 * <p>
 * The policy accumulates the maximum osculating target eccentricity (relative to the star, with
 * {@code mu = G(M_star + M_target)} — the perturber acts through the evolving relative state, not
 * through mu) and exposes it through {@link Level3ProgressSource}. The accumulator is seeded from the
 * initial snapshot, so {@link #maxEccentricity()} is valid before the first observation. The three
 * bodies are resolved by their identities, and radii come from their body definitions rather than
 * being duplicated as policy constants.
 * <p>
 * These are instantaneous osculating Sun–target elements within a three-body simulation, not conserved
 * Keplerian elements.
 *
 * @author Eugeniu PLATITA
 */
public final class Level3CompletionPolicy implements ScenarioCompletionPolicy, Level3ProgressSource
{
    private final String starId;
    private final String targetId;
    private final String perturberId;
    private final double gravitationalParameter;
    private final double horizonSeconds;

    private double maxEccentricity;

    /**
     * @param initialSnapshot        the committed initial state, used to seed the eccentricity
     *                               baseline; never null and must contain all three bodies
     * @param starId                 the star's id
     * @param targetId               the target planet's id (the orbit measured and assessed)
     * @param perturberId            the perturbing planet's id
     * @param gravitationalParameter mu = G(M_star + M_target) for the target's osculating elements;
     *                               positive and finite
     * @param horizonSeconds         the observation horizon; positive and finite
     * @throws NullPointerException     if a reference is null
     * @throws IllegalArgumentException if an id is blank, ids are not distinct, a numeric argument is
     *                                  out of range, or a body id is not present in the snapshot
     */
    public Level3CompletionPolicy(
            final SimulationSnapshot initialSnapshot,
            final String starId,
            final String targetId,
            final String perturberId,
            final double gravitationalParameter,
            final double horizonSeconds)
    {
        Objects.requireNonNull(initialSnapshot, "initialSnapshot must not be null");
        this.starId = requireIdentifier(starId, "starId");
        this.targetId = requireIdentifier(targetId, "targetId");
        this.perturberId = requireIdentifier(perturberId, "perturberId");
        requireDistinct(this.starId, this.targetId, this.perturberId);
        this.gravitationalParameter = requirePositiveFinite(gravitationalParameter, "gravitationalParameter");
        this.horizonSeconds = requirePositiveFinite(horizonSeconds, "horizonSeconds");

        // Seed the baseline so maxEccentricity() is meaningful before the first observation.
        this.maxEccentricity = targetEccentricity(initialSnapshot);
    }

    @Override
    public ScenarioEvaluation evaluate(final ScenarioObservation observation)
    {
        Objects.requireNonNull(observation, "observation must not be null");
        final SimulationSnapshot snapshot = observation.snapshot();

        if (surfacesInContact(snapshot, targetId, starId) || surfacesInContact(snapshot, targetId, perturberId))
        {
            return ScenarioEvaluation.FAIL;
        }

        maxEccentricity = Math.max(maxEccentricity, targetEccentricity(snapshot));

        if (observation.elapsedScenarioSeconds() >= horizonSeconds)
        {
            return ScenarioEvaluation.COMPLETE;
        }
        return ScenarioEvaluation.CONTINUE;
    }

    @Override
    public double maxEccentricity()
    {
        return maxEccentricity;
    }

    private double targetEccentricity(final SimulationSnapshot snapshot)
    {
        final BodySnapshot star = bodyById(snapshot, starId);
        final BodySnapshot target = bodyById(snapshot, targetId);
        final OrbitalStateAnalysis orbit = OrbitalStateCalculator.analyse(
                target.state().position().subtract(star.state().position()),
                target.state().velocity().subtract(star.state().velocity()),
                gravitationalParameter);
        return orbit.eccentricity();
    }

    private boolean surfacesInContact(
            final SimulationSnapshot snapshot, final String firstId, final String secondId)
    {
        final BodySnapshot first = bodyById(snapshot, firstId);
        final BodySnapshot second = bodyById(snapshot, secondId);
        final double contactDistance = first.definition().radius() + second.definition().radius();
        return first.state().position().distanceTo(second.state().position()) <= contactDistance;
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

    private static void requireDistinct(final String a, final String b, final String c)
    {
        if (a.equals(b) || a.equals(c) || b.equals(c))
        {
            throw new IllegalArgumentException("star, target and perturber ids must be distinct");
        }
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