package io.github.eugenp84.orbitalsim.core.math;

/**
 * An immutable three-dimensional vector used for positions, velocities,
 * accelerations and forces throughout the simulation.
 * <p>
 * All operations return new instances; no method mutates state.
 */
public record Vector3D(double x, double y, double z) {

    /** The zero vector. */
    public static final Vector3D ZERO = new Vector3D(0.0, 0.0, 0.0);

    public Vector3D add(Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    public Vector3D multiply(double scalar) {
        return new Vector3D(x * scalar, y * scalar, z * scalar);
    }

    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double magnitudeSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * Returns the unit vector in the direction of this vector.
     *
     * @throws ArithmeticException if this is the zero vector.
     * An exact zero comparison is used rather than a tolerance:
     * any nonzero magnitude yields a valid normalization in IEEE 754 arithmetic,
     * and a tolerance threshold would be arbitrary at astronomical scale.
     */
    public Vector3D normalize() {
        double vectorMagnitude = magnitude();
        if (vectorMagnitude == 0.0) {
            throw new ArithmeticException("Cannot normalize the zero vector");
        }
        return multiply(1.0 / vectorMagnitude);
    }

    public double dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double distanceTo(Vector3D other) {
        return subtract(other).magnitude();
    }
}