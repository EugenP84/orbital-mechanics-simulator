package io.github.eugenp84.orbitalsim.core.orbital;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Derives instantaneous orbital quantities from a relative state (ADR-009).
 * Stateless, and deliberately ignorant of which body is the reference, which scenario is running, and where the data came from.
 * <p>
 * μ is supplied by the caller because the correct value depends on the physical model — {@code G(M + m)} for a coupled two-body orbit, {@code GM} for a
 * restricted test particle.
 * Those are different models, and only the caller knows which applies: this class performs mathematics,
 * it does not choose a gravitational model (ADR-009 p4).
 * <p>
 * Because every quantity derives from differences, results are unchanged by a common spatial translation or Galilean boost of both bodies (ADR-009 p.3).
 *
 * @author Eugeniu PLATITA
 */
public final class OrbitalStateCalculator
{

    private static final double TWO_PI = 2.0 * Math.PI;

    private OrbitalStateCalculator() {
        // Static utility; the calculation carries no state.
    }

    /**
     * Analyses a relative state.
     *
     * @param relativePosition       position relative to the reference body, in metres; finite components, non-zero
     * @param relativeVelocity       velocity relative to the reference body, in m/s; finite components
     * @param gravitationalParameter μ in m³/s²; positive and finite
     * @return the derived instantaneous quantities
     * @throws NullPointerException     if either vector is null
     * @throws IllegalArgumentException if a component is non-finite, the bodies are coincident, μ is not positive and finite,
     *              or the inputs are so extreme that a derived quantity is not representable
     */
    public static OrbitalStateAnalysis analyse(
            Vector3D relativePosition,
            Vector3D relativeVelocity,
            double gravitationalParameter)
    {

        Objects.requireNonNull(relativePosition, "relativePosition must not be null");
        Objects.requireNonNull(relativeVelocity, "relativeVelocity must not be null");
        requireFiniteComponents(relativePosition, "relativePosition");
        requireFiniteComponents(relativeVelocity, "relativeVelocity");

        if (!Double.isFinite(gravitationalParameter) || gravitationalParameter <= 0.0)
        {
            throw new IllegalArgumentException("gravitationalParameter must be positive and finite, was: " + gravitationalParameter);
        }

        double rx = relativePosition.x();
        double ry = relativePosition.y();
        double rz = relativePosition.z();
        double vx = relativeVelocity.x();
        double vy = relativeVelocity.y();
        double vz = relativeVelocity.z();

        // Scaled magnitude: squaring first would overflow for large components whose true norm is finite, and underflow for subnormal ones
        // the latter reporting a genuinely separated pair as coincident.
        double distance = Math.hypot(Math.hypot(rx, ry), rz);
        if (distance == 0.0)
        {
            throw new IllegalArgumentException("relativePosition must not be the zero vector; coincident bodies " + "have no defined orbit");
        }

        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("relativePosition magnitude is not representable: " + relativePosition);
        }

        double speedSquared = vx * vx + vy * vy + vz * vz;
        double radialDotVelocity = rx * vx + ry * vy + rz * vz;

        Vector3D angularMomentum = new Vector3D(
                ry * vz - rz * vy,
                rz * vx - rx * vz,
                rx * vy - ry * vx);

        double energy = speedSquared / 2.0 - gravitationalParameter / distance;
        if (!Double.isFinite(energy))
        {
            throw new IllegalArgumentException("specific orbital energy is not representable: distance=" + distance
                            + ", mu=" + gravitationalParameter);
        }

        double scale = speedSquared - gravitationalParameter / distance;
        Vector3D eccentricityVector = new Vector3D(
                (scale * rx - radialDotVelocity * vx) / gravitationalParameter,
                (scale * ry - radialDotVelocity * vy) / gravitationalParameter,
                (scale * rz - radialDotVelocity * vz) / gravitationalParameter);

        double eccentricity = Math.hypot(
                Math.hypot(eccentricityVector.x(), eccentricityVector.y()),
                eccentricityVector.z());


        if (!Double.isFinite(eccentricity))
        {
            throw new IllegalArgumentException("eccentricity is not representable: distance=" + distance + ", mu=" + gravitationalParameter);
        }

        return new OrbitalStateAnalysis(
                angularMomentum,
                energy,
                eccentricityVector,
                eccentricity,
                semiMajorAxis(gravitationalParameter, energy),
                referencePlaneAzimuth(rx, ry));
    }

    /**
     * {@code a = −μ / 2ε}, absent only at the parabolic boundary.
     * <p>
     * Zero energy is tested first so that absence keeps a single physical meaning:
     * a non-zero energy whose semi-major axis overflows is a representability failure, not a parabola, and is rejected like any other.
     * The half is applied to μ rather than the energy because {@code 2 × energy} can overflow,
     * and the resulting division would yield a signed zero — a finite value that would masquerade as a valid semi-major axis of zero.
     */
    private static OptionalDouble semiMajorAxis(double gravitationalParameter, double energy)
    {
        if (energy == 0.0)
        {
            return OptionalDouble.empty();
        }

        double a = (-0.5 * gravitationalParameter) / energy;
        if (!Double.isFinite(a)) {
            throw new IllegalArgumentException(
                    "semi-major axis is not representable: mu=" + gravitationalParameter
                            + ", specific energy=" + energy);
        }
        return OptionalDouble.of(a);
    }

    /**
     * Azimuth of the relative position projected onto the reference XY plane.
     * The zero-projection guard is required because {@code Math.atan2(0, 0)}
     * returns {@code 0.0} rather than signalling, so a body on the reference-plane normal would otherwise report a meaningless azimuth.
     */
    private static OptionalDouble referencePlaneAzimuth(double rx, double ry)
    {
        if (rx == 0.0 && ry == 0.0)
        {
            return OptionalDouble.empty();
        }

        double azimuth = Math.atan2(ry, rx);
        if (azimuth < 0.0)
        {
            azimuth += TWO_PI;
        }
        if (azimuth >= TWO_PI) {
            azimuth = 0.0;
        }
        return OptionalDouble.of(azimuth);
    }

    private static void requireFiniteComponents(Vector3D vector, String name)
    {

        if (!Double.isFinite(vector.x()) || !Double.isFinite(vector.y()) || !Double.isFinite(vector.z()))
        {
            throw new IllegalArgumentException(name + " must have finite components, was: " + vector);
        }
    }
}