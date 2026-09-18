package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import java.util.List;
import java.util.Objects;

/**
 * Constructs a two-body initial state in the centre-of-mass inertial frame from relative position and velocity.
 * <p>
 * The supplied relative vectors are defined as body 2 relative to body 1:
 * {@code r = r2 - r1} and {@code v = v2 - v1}.
 * The resulting absolute states preserve those relative vectors while placing the centre of mass at the origin and giving the system zero total momentum.
 *
 * @author Eugeniu PLATITA
 */
public final class TwoBodyInitialState
{
    private TwoBodyInitialState()
    {
        throw new AssertionError("No instances");
    }

    /**
     * Creates centre-of-mass states for two bodies.
     *
     * @param body1            first body definition, never null
     * @param body2            second body definition, never null
     * @param relativePosition position of body 2 relative to body 1, never null
     * @param relativeVelocity velocity of body 2 relative to body 1, never null
     * @return immutable list containing body 1 followed by body 2
     */
    public static List<BodySnapshot> centreOfMass(
            final CelestialBody body1,
            final CelestialBody body2,
            final Vector3D relativePosition,
            final Vector3D relativeVelocity)
    {
        Objects.requireNonNull(body1, "body1 must not be null");
        Objects.requireNonNull(body2, "body2 must not be null");
        Objects.requireNonNull(relativePosition, "relativePosition must not be null");
        Objects.requireNonNull(relativeVelocity, "relativeVelocity must not be null");

        final double totalMass = body1.mass() + body2.mass();

        if (!Double.isFinite(totalMass)) {
            throw new IllegalArgumentException("Combined body mass must be finite, was: " + totalMass);
        }

        final double body1Fraction = -body2.mass() / totalMass;
        final double body2Fraction = body1.mass() / totalMass;

        return List.of(
                new BodySnapshot(
                        body1,
                        new StateVector(
                                relativePosition.multiply(body1Fraction),
                                relativeVelocity.multiply(body1Fraction))),
                new BodySnapshot(
                        body2,
                        new StateVector(
                                relativePosition.multiply(body2Fraction),
                                relativeVelocity.multiply(body2Fraction))));
    }
}