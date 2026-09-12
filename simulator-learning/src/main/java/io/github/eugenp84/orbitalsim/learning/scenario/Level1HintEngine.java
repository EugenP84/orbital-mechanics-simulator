package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateAnalysis;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationListener;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Produces Level 1 guidance by observing committed simulation states through the same
 * {@link SimulationListener} boundary the renderer uses (ADR-008 p9), so it is decoupled from the simulation and can be disabled without affecting physics or rendering.
 * It observes at the learning
 * cadence, because it is registered on the engine the session advances one observation at a time — not on numerical substeps and not on the presentation clock.
 * <p>
 * <strong>Direction is fixed by the attempt.</strong> The too-slow / too-fast judgement comes from the <em>configured initial speed</em> relative to the analytic circular speed — the only quantity
 * the learner sets.
 * It is deliberately not derived from the body's instantaneous speed, which on an eccentric orbit is slower near apoapsis and faster near periapsis and would otherwise make the same immutable attempt alternate between "speed up" and "slow down".
 * <p>
 * <strong>Live state validates, it does not steer.</strong> Each observation is used only to confirm whether the orbit is actually unbound (specific orbital energy ≥ 0);
 * an unbound orbit overrides the directional bands, because reducing an escape trajectory toward circular is the guidance that matters.
 * The magnitude bands here are pedagogical and are intentionally distinct from the completion policy's acceptance thresholds (ADR-008 p.6):
 * assessment decides whether the learner met the level criteria; hints point toward the analytic circular ideal.
 * <p>
 * Notifications arrive on the thread that advanced the engine — the JavaFX Application Thread in the current app — and the latest hint is read from that same thread,
 * so no synchronisation is needed.
 * This would need revisiting only if advancement moved off that thread.
 *
 * @author Eugeniu PLATITA
 */
public final class Level1HintEngine implements SimulationListener, HintSource
{
    // Pedagogical bands on the configured initial speed as a fraction of circular.
    // Distinct from the assessment thresholds; chosen to give a stable, progressively specific directional message.
    private static final double NEAR_CIRCULAR_LOWER_FRACTION = 0.95;
    private static final double NEAR_CIRCULAR_UPPER_FRACTION = 1.05;
    private static final double WELL_BELOW_UPPER_FRACTION = 0.80;
    private static final double WELL_ABOVE_LOWER_FRACTION = 1.20;

    private final String referenceBodyId;
    private final String learnerBodyId;
    private final double gravitationalParameter;
    private final double initialSpeedFractionOfCircular;

    private Optional<Hint> latestHint = Optional.empty();

    /**
     * @param referenceBodyId                the body the orbit is assessed about
     * @param learnerBodyId                  the learner-controlled body
     * @param gravitationalParameter         μ for the relative orbit, m³/s²; positive and finite
     * @param initialSpeedFractionOfCircular the attempt's configured initial speed as a fraction of circular; positive and finite
     * @throws NullPointerException     if an id is null
     * @throws IllegalArgumentException if an id is blank, or a numeric argument is out of range
     */
    public Level1HintEngine(
            final String referenceBodyId,
            final String learnerBodyId,
            final double gravitationalParameter,
            final double initialSpeedFractionOfCircular)
    {
        this.referenceBodyId = requireIdentifier(referenceBodyId, "referenceBodyId");
        this.learnerBodyId = requireIdentifier(learnerBodyId, "learnerBodyId");
        if (this.referenceBodyId.equals(this.learnerBodyId))
        {
            throw new IllegalArgumentException("referenceBodyId and learnerBodyId must differ");
        }
        this.gravitationalParameter = requirePositiveFinite(gravitationalParameter, "gravitationalParameter");
        this.initialSpeedFractionOfCircular = requirePositiveFinite(initialSpeedFractionOfCircular, "initialSpeedFractionOfCircular");
    }

    @Override
    public Optional<Hint> latestHint()
    {
        return latestHint;
    }

    @Override
    public void onSnapshot(final SimulationSnapshot snapshot)
    {
        latestHint = Optional.of(infer(snapshot));
    }

    private Hint infer(final SimulationSnapshot snapshot)
    {
        if (isUnbound(snapshot))
        {
            return new Hint(HintCategory.UNBOUND,
                    "This initial speed produces an unbound trajectory. Reduce it below escape speed.");
        }
        return directionalHint();
    }

    private Hint directionalHint()
    {
        final double fraction = initialSpeedFractionOfCircular;
        if (fraction < WELL_BELOW_UPPER_FRACTION)
        {
            return new Hint(HintCategory.BELOW_CIRCULAR_WELL,"Your initial speed is well below circular speed. " + "Increase it substantially toward circular speed.");
        }
        if (fraction < NEAR_CIRCULAR_LOWER_FRACTION)
        {
            return new Hint(HintCategory.BELOW_CIRCULAR_SLIGHTLY,"Your initial speed is below circular speed. " + "Increase it slightly to make the orbit rounder.");
        }
        if (fraction <= NEAR_CIRCULAR_UPPER_FRACTION)
        {
            return new Hint(HintCategory.NEAR_CIRCULAR,"Your initial speed is close to circular speed.");
        }
        if (fraction < WELL_ABOVE_LOWER_FRACTION)
        {
            return new Hint(HintCategory.ABOVE_CIRCULAR_SLIGHTLY,"Your initial speed is above circular speed. " + "Reduce it slightly to make the orbit rounder.");
        }
        return new Hint(HintCategory.ABOVE_CIRCULAR_WELL,"Your initial speed is well above circular speed. " + "Reduce it toward circular speed.");
    }

    private boolean isUnbound(final SimulationSnapshot snapshot)
    {
        final BodySnapshot reference = bodyById(snapshot, referenceBodyId);
        final BodySnapshot learner = bodyById(snapshot, learnerBodyId);
        final StateVector referenceState = reference.state();
        final StateVector learnerState = learner.state();

        final OrbitalStateAnalysis orbit = OrbitalStateCalculator.analyse(
                learnerState.position().subtract(referenceState.position()),
                learnerState.velocity().subtract(referenceState.velocity()),
                gravitationalParameter);

        return orbit.specificOrbitalEnergy() >= 0.0;
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