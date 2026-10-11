package io.github.eugenp84.orbitalsim.core.orbital;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Instantaneous orbital quantities for one relative state.
 * <p>
 * The quantities are produced together (not by separate accessors) so a consumer is guaranteed values derived from identical inputs;
 * sharing intermediate arithmetic is a secondary benefit, immaterial at this scale.
 * <p>
 * This type reports physics and deliberately offers no {@code isEscaping()} or {@code isStableOrbit()}:
 * what an energy or eccentricity means is a  pedagogical judgement owned by the learning layer.
 * <p>
 * Being a public value type, it enforces the invariants it documents, so an  instance is valid independently of the calculator.
 * It does not re-derive the relationships between fields; that would duplicate the calculator.
 * <p>
 * All values are SI.
 *
 * @param specificAngularMomentum {@code h = r × v} in m²/s. Zero for radial motion, where no orbital plane exists — hence no inclination or node vector is offered here.
 * @param specificOrbitalEnergy {@code ε = v²/2 − μ/r} in J/kg, relative to the two-body model implied by the caller's μ.
 *        Sign gives bound / parabolic / unbound.
 * @param eccentricityVector dimensionless, toward periapsis where one exists.
 * @param eccentricity its magnitude. Radial motion has {@code e = 1} whatever its energy, so eccentricity alone cannot separate bound from unbound — use {@code specificOrbitalEnergy}.
 * @param semiMajorAxis {@code a = −μ / 2ε} in metres. Absent means parabolic boundary.
 *        A near-parabolic orbit has a genuine, very large {@code a} and is present.
 * @param referencePlaneAzimuth {@code atan2(r_y, r_x)} normalised to
 *        {@code [0, 2π)}, in radians — frame-dependent geometry for tracking  angular progress, not an orbital element, and not to be confused with RAAN, argument of periapsis or true anomaly.
 *        Absent on the reference-plane normal, where the projection vanishes.
 *
 * @author Eugeniu PLATITA
 */
public record OrbitalStateAnalysis(
        Vector3D specificAngularMomentum,
        double specificOrbitalEnergy,
        Vector3D eccentricityVector,
        double eccentricity,
        OptionalDouble semiMajorAxis,
        OptionalDouble referencePlaneAzimuth)
{

    private static final double TWO_PI = 2.0 * Math.PI;

    public OrbitalStateAnalysis
    {
        Objects.requireNonNull(specificAngularMomentum, "specificAngularMomentum must not be null");
        Objects.requireNonNull(eccentricityVector, "eccentricityVector must not be null");
        Objects.requireNonNull(semiMajorAxis, "semiMajorAxis must not be null");
        Objects.requireNonNull(referencePlaneAzimuth, "referencePlaneAzimuth must not be null");

        requireFiniteComponents(specificAngularMomentum, "specificAngularMomentum");
        requireFiniteComponents(eccentricityVector, "eccentricityVector");

        if (!Double.isFinite(specificOrbitalEnergy))
        {
            throw new IllegalArgumentException("specificOrbitalEnergy must be finite, was: " + specificOrbitalEnergy);
        }
        if (!Double.isFinite(eccentricity) || eccentricity < 0.0)
        {
            throw new IllegalArgumentException("eccentricity must be finite and non-negative, was: " + eccentricity);
        }

        // Absence already carries the parabolic meaning, so a present value must be a real quantity. otherwise infinity would silently become a second contradictory way of saying the same thing.
        if (semiMajorAxis.isPresent() && !Double.isFinite(semiMajorAxis.getAsDouble())) {
            throw new IllegalArgumentException("a present semiMajorAxis must be finite, was: " + semiMajorAxis.getAsDouble());
        }

        // Normalisation is part of the contract, so callers can compare and unwrap azimuths without rechecking the range.
        if (referencePlaneAzimuth.isPresent())
        {
            double azimuth = referencePlaneAzimuth.getAsDouble();
            if (!Double.isFinite(azimuth) || azimuth < 0.0 || azimuth >= TWO_PI) {
                throw new IllegalArgumentException("referencePlaneAzimuth must be within [0, 2pi), was: " + azimuth);
            }
        }
    } // ---------------- end OrbitalStateAnalysis() -======================--------------------------------

    /** Whether the relative position and velocity are parallel, leaving no orbital plane. */
    public boolean isRadial() {
        return specificAngularMomentum.x() == 0.0
                && specificAngularMomentum.y() == 0.0 && specificAngularMomentum.z() == 0.0;
    }

    private static void requireFiniteComponents(Vector3D vector, String name) {
        if (!Double.isFinite(vector.x()) || !Double.isFinite(vector.y()) || !Double.isFinite(vector.z()))
        {
            throw new IllegalArgumentException(name + " must have finite components, was: " + vector);
        }//end if
    }
}