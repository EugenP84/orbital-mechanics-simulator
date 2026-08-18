package io.github.eugenp84.orbitalsim.core.physics;

/**
 * Parameterises one body within a gravitational model: the properties the
 * N-body acceleration model requires, and how the scenario chooses to treat
 * that body as a gravitational source.
 * <p>
 * This record deliberately carries only mass and interaction policy rather
 * than a full {@code CelestialBody}. The gravitational model has no use for
 * identity, name, classification or radius, and omitting them keeps this
 * package independent of the domain model. That independence is preserved
 * deliberately; it is not currently enforced by any architectural mechanism.
 * <p>
 * {@code contributesGravity} expresses a modelling decision belonging to the
 * scenario, not a physical property of the object (ADR-004). A body with
 * {@code contributesGravity = false} is still accelerated by every
 * contributing source; only its own influence on others is suppressed. This
 * is how the restricted three-body model used by the NEO scenario is
 * expressed: the asteroid retains its assigned physical mass but does not act
 * back on the primaries.
 * <p>
 * Mass is validated here rather than at the point of use. The acceleration
 * model passes only <em>source</em> masses to {@link GravityCalculator}, so a
 * non-contributing body's mass would otherwise never be validated by any
 * component in the chain.
 *
 * @param mass               mass in kilograms; must be positive and finite
 * @param contributesGravity whether this body acts as a gravitational source
 */
public record GravitationalBodyConfig(double mass, boolean contributesGravity) {

    public GravitationalBodyConfig {
        if (mass <= 0.0 || !Double.isFinite(mass)) {
            throw new IllegalArgumentException(
                    "Mass must be positive and finite, was: " + mass);
        }
    }

    /**
     * A body that both experiences and exerts gravitational attraction.
     */
    public static GravitationalBodyConfig contributing(double mass) {
        return new GravitationalBodyConfig(mass, true);
    }

    /**
     * A body that experiences gravitational attraction but exerts none,
     * as used by the restricted three-body model.
     */
    public static GravitationalBodyConfig nonContributing(double mass) {
        return new GravitationalBodyConfig(mass, false);
    }
}