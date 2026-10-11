package io.github.eugenp84.orbitalsim.core.physics;

import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;

import java.util.Objects;

/**
 * Computes Newtonian gravitational interactions between point masses.
 * <p>
 * Gravity is modelled by Newton's law of universal gravitation
 * <pre>
 *     F = G · (m₁ · m₂ / r²) · r̂
 * </pre>
 * No softening parameter is applied: the exact inverse-square law is used so that simulated behaviour remains directly comparable with the analytical Newtonian quantities used for validation.
 * <p>
 * This class computes gravitational interaction when asked and holds no policy about which bodies interact.
 * Decisions such as the restricted three-body approximation, in which a test particle exerts no back-reaction on the primaries, belong to the force-aggregation layer or scenario configuration.
 * <p>
 * All quantities are in canonical SI units.
 * The class is stateless and its methods are pure functions of their arguments.
 * Inputs are validated independently of invariants guaranteed elsewhere in the domain model, because this is a public API over primitive values.
 *
 * @author Eugeniu PLATITA
 */
public final class GravityCalculator {

    private GravityCalculator() {
        // Stateless utility — not instantiable
    }

    /**
     * Gravitational acceleration experienced by a body at {@code position} due to a source mass at {@code sourcePosition}.
     * <p>
     * The mass of the accelerated body does not appear in the expression: by the equivalence of inertial and gravitational mass it cancels.
     * This supports restricted test-particle propagation, where a body's trajectory is determined by the masses of the primaries alone.
     *
     * @param position       position of the accelerated body, in metres
     * @param sourcePosition position of the attracting body, in metres
     * @param sourceMass     mass of the attracting body, in kilograms; must be positive and finite
     * @return acceleration in m·s⁻², directed towards the source
     * @throws NullPointerException     if either position is null
     * @throws IllegalArgumentException if the source mass is not positive and finite, if the two positions coincide, if the geometry is non-finite, or if the resulting acceleration is not finite
     */
    public static Vector3D accelerationBetween(
            Vector3D position,
            Vector3D sourcePosition,
            double sourceMass) {

        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(sourcePosition, "sourcePosition must not be null");
        requirePositiveFiniteMass(sourceMass, "Source mass");

        Vector3D separationVector = sourcePosition.subtract(position);
        double distanceSquared = separationVector.magnitudeSquared();

        // NaN fails both comparisons below, so isFinite is checked explicitly:
        // a NaN admitted here would propagate across every body within a few integration steps.
        if (!Double.isFinite(distanceSquared)) {
            throw new IllegalArgumentException("Gravitational acceleration is undefined for non-finite geometry");
        }
        if (distanceSquared == 0.0) {
            throw new IllegalArgumentException("Gravitational acceleration is undefined for coincident positions");
        }

        // a = G·M/r² directed towards the source.
        // Dividing the separation by its own magnitude yields the unit vector, so only one square root is taken rather than calling normalize() and magnitudeSquared() separately.
        double distance = Math.sqrt(distanceSquared);
        double accelerationMagnitude = PhysicalConstants.GRAVITATIONAL_CONSTANT * sourceMass / distanceSquared;

        Vector3D acceleration = separationVector.multiply(accelerationMagnitude / distance);

        // Components are checked individually: a vector may hold finite components whose squared norm is not representable.
        if (!Double.isFinite(acceleration.x()) || !Double.isFinite(acceleration.y()) || !Double.isFinite(acceleration.z())) {
            throw new IllegalArgumentException("Computed acceleration is not finite; separation may be too small: " + distance + " m");
        }

        return acceleration;
    }

    /**
     * Magnitude of the mutual gravitational force between two masses separated
     * by {@code distance}.
     * <p>
     * Provided for physical verification and for reporting quantities to the
     * user interface; the simulation loop uses {@link #accelerationBetween} directly, since acceleration is the quantity required by the equations of motion.
     *
     * @param massA    first mass, in kilograms; must be positive and finite
     * @param massB    second mass, in kilograms; must be positive and finite
     * @param distance separation, in metres; must be positive and finite
     * @return force magnitude in newtons
     * @throws IllegalArgumentException if any argument is not positive and finite, or if the resulting force is not finite
     */
    public static double forceMagnitude(double massA, double massB, double distance) {
        requirePositiveFiniteMass(massA, "First mass");
        requirePositiveFiniteMass(massB, "Second mass");

        if (distance <= 0.0 || !Double.isFinite(distance)) {
            throw new IllegalArgumentException("Separation must be positive and finite, was: " + distance);
        }

        double force = PhysicalConstants.GRAVITATIONAL_CONSTANT * massA * massB / (distance * distance);

        if (!Double.isFinite(force)) {
            throw new IllegalArgumentException("Computed force is not finite for the given masses and separation");
        }

        return force;
    }

    private static void requirePositiveFiniteMass(double mass, String label) {
        if (mass <= 0.0 || !Double.isFinite(mass)) {
            throw new IllegalArgumentException(label + " must be positive and finite, was: " + mass);
        }
    }
}