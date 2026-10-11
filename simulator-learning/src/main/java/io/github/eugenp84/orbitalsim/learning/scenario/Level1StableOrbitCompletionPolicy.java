package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateAnalysis;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateCalculator;
import io.github.eugenp84.orbitalsim.core.physics.EnergyCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Completion policy for Level 1: Stable Orbit.
 * <p>
 * The learner's (user's) body must stay in a bound orbit about a configured reference body for a required number of revolutions, and at that checkpoint the orbit must be no more eccentric than the configured limit.
 *
 * @author Eugeniu PLATITA
 */
public final class Level1StableOrbitCompletionPolicy implements ScenarioCompletionPolicy
{
    private static final double TWO_PI = 2.0 * Math.PI;
    private final String referenceBodyId;
    private final String learnerBodyId;
    private final double gravitationalParameter;
    private final Level1Criteria criteria;
    private final boolean initiallyUnbound;
    private final double baselineMechanicalEnergy;
    private double previousAzimuth;
    private double accumulatedAngularDisplacement;

    /**
     * @param initialSnapshot        committed state at scenario start
     * @param referenceBodyId        body the orbit is assessed about
     * @param learnerBodyId          the learner-controlled body
     * @param gravitationalParameter μ for the relative orbit, m³/s²; positive and finite.
     *                               For L1's coupled two-body system this is {@code G(M + m)}; the caller chooses,
     *                               because only it knows the model (ADR-009 p4) @param cadence the same cadence the session will execute, used to establish that the learner's orbit is sampled finely enough to count revolutions
     * @param criteria               calibrated acceptance thresholds
     * @throws NullPointerException     if the snapshot, an id, the cadence or the criteria is null
     * @throws IllegalArgumentException if configuration is invalid, a body cannot be resolved uniquely, the initial azimuth is undefined, or the learner's initial orbit is bound but too coarsely sampled for revolution counting to be trustworthy
     */
    public Level1StableOrbitCompletionPolicy(
            SimulationSnapshot initialSnapshot,
            String referenceBodyId,
            String learnerBodyId,
            double gravitationalParameter,
            ScenarioObservationCadence cadence,
            Level1Criteria criteria)
    {

        Objects.requireNonNull(initialSnapshot, "initialSnapshot must not be null");
        Objects.requireNonNull(cadence, "cadence must not be null");
        this.criteria = Objects.requireNonNull(criteria, "criteria must not be null");
        this.referenceBodyId = requireBodyId(referenceBodyId, "referenceBodyId");
        this.learnerBodyId = requireBodyId(learnerBodyId, "learnerBodyId");

        if (this.referenceBodyId.equals(this.learnerBodyId))
        {
            throw new IllegalArgumentException("referenceBodyId and learnerBodyId must identify different bodies");
        }
        if (!Double.isFinite(gravitationalParameter) || gravitationalParameter <= 0.0) {
            throw new IllegalArgumentException("gravitationalParameter must be positive and finite, was: " + gravitationalParameter);
        }
        this.gravitationalParameter = gravitationalParameter;

        BodyPair initialBodies = resolveBodies(initialSnapshot);
        OrbitalStateAnalysis initialOrbit = analyseRelativeOrbit(initialBodies);

        OptionalDouble initialAzimuth = initialOrbit.referencePlaneAzimuth();
        if (initialAzimuth.isEmpty()) {
            throw new IllegalArgumentException("Level 1 requires a defined reference-plane azimuth at scenario start");
        }
        this.previousAzimuth = initialAzimuth.getAsDouble();
        this.baselineMechanicalEnergy = totalMechanicalEnergy(initialBodies);

        // Only the sign of the initial energy is ever used. the flag records the conclusion rather than the quantity.
        this.initiallyUnbound = initialOrbit.specificOrbitalEnergy() >= 0.0;

        if (!initiallyUnbound) {
            requireAdequateSampling(initialOrbit, cadence.intervalSeconds());
        }
    }

    @Override
    public ScenarioEvaluation evaluate(ScenarioObservation observation)
    {
        Objects.requireNonNull(observation, "observation must not be null");

        BodyPair bodies = resolveBodies(observation.snapshot());

        if (violatesMinimumClearance(bodies)) {
            return ScenarioEvaluation.FAIL;
        }
        if (initiallyUnbound) {
            return ScenarioEvaluation.FAIL;
        }
        if (energyIntegrityExceeded(bodies)) {
            return ScenarioEvaluation.ASSESSMENT_INVALID;
        }

        OrbitalStateAnalysis orbit = analyseRelativeOrbit(bodies);

        if (orbit.specificOrbitalEnergy() >= 0.0) {
            return ScenarioEvaluation.FAIL;
        }

        OptionalDouble azimuth = orbit.referencePlaneAzimuth();
        if (azimuth.isEmpty()) {
            // !!! Without an azimuth there is no revolution progress to accumulate, so no verdict can be reached !!!!!!!
            return ScenarioEvaluation.ASSESSMENT_INVALID;
        }
        accumulateAngularProgress(azimuth.getAsDouble());

        if (!requiredRevolutionsReached())
        {
            return ScenarioEvaluation.CONTINUE;
        }

        // Eccentricity is summative here and formative via hints during the attempt
        return orbit.eccentricity() <= criteria.maximumEccentricity()
                ? ScenarioEvaluation.COMPLETE
                : ScenarioEvaluation.FAIL;
    }

    /** Signed accumulated angular displacement, radians; for diagnostics and tests. */
    public double accumulatedAngularDisplacement() {
        return accumulatedAngularDisplacement;
    }

    /** Revolutions represented by the accumulated displacement, ignoring direction. */
    public double completedRevolutions() {
        return Math.abs(accumulatedAngularDisplacement) / TWO_PI;
    }

    /**
     * Rejects an attempt whose orbit is too coarsely sampled to count revolutions.
     * No single cadence is adequate for every admissible orbit, but the orbit is known once the attempt exists, so adequacy is decided per attempt rather than per scenario.
     */
    private void requireAdequateSampling(OrbitalStateAnalysis initialOrbit, double observationIntervalSeconds)
    {
        OptionalDouble semiMajorAxis = initialOrbit.semiMajorAxis();
        if (semiMajorAxis.isEmpty() || semiMajorAxis.getAsDouble() <= 0.0)
        {
            throw new IllegalArgumentException("a bound initial orbit must have a positive semi-major axis, was: " + semiMajorAxis);
        }

        double a = semiMajorAxis.getAsDouble();
        // Written as a*sqrt(a/mu) rather than sqrt(a^3/mu): the intermediate is the sqrt of the same quantity, so it stays representable for orbits whose period itself is representable.
        double period = TWO_PI * a * Math.sqrt(a / gravitationalParameter);
        if (!Double.isFinite(period) || period <= 0.0) {
            throw new IllegalArgumentException("orbital period is not representable for the initial orbit, a=" + a);
        }

        double actualSamples = period / observationIntervalSeconds;
        double requiredSamples = requiredSamplesPerRevolution(initialOrbit.eccentricity());

        if (!(actualSamples > requiredSamples)) // Negated comparison so a non-finite value on either side is rejected rather than slipping through as an unsatisfied inequality.
        {
            throw new ScenarioAttemptNotAssessableException("the initial orbit yields " + actualSamples + " observations per revolution, at or below the "
                    + requiredSamples + " required for eccentricity "
                    + initialOrbit.eccentricity()
                    + "; revolution counting would not be trustworthy");
        }
    } //end of requireAdequateSampling() -------------------------------------------------------

    /**
     * {@code 2·√(1+e) / (1−e)^1.5}, the mean samples per revolution that keep the angular step below π everywhere on a Kepler ellipse,
     * the binding case being periapsis.
     * Written as {@code (1−e)·√(1−e)} rather than with {@code Math.pow} so the exponent is visible in the expression.
     */
    private static double requiredSamplesPerRevolution(double eccentricity) {
        double oneMinusE = 1.0 - eccentricity;
        return 2.0 * Math.sqrt(1.0 + eccentricity) / (oneMinusE * Math.sqrt(oneMinusE));
    }

    private boolean violatesMinimumClearance(BodyPair bodies)
    {
        double centreDistance = separation(
                bodies.reference().state().position(),
                bodies.learner().state().position());

        double surfaceGap = centreDistance
                - bodies.reference().definition().radius()
                - bodies.learner().definition().radius();

        return surfaceGap <= criteria.minimumSurfaceClearanceMetres();
    }

    private OrbitalStateAnalysis analyseRelativeOrbit(BodyPair bodies)
    {
        StateVector reference = bodies.reference().state();
        StateVector learner = bodies.learner().state();

        return OrbitalStateCalculator.analyse(
                learner.position().subtract(reference.position()),
                learner.velocity().subtract(reference.velocity()),
                gravitationalParameter);
    }

    private boolean energyIntegrityExceeded(BodyPair bodies)
    {
        double current = totalMechanicalEnergy(bodies);

        // !!! A zero baseline should not arise for a bound two-body orbit;
        // the branch exists so the relative measure cannot silently become NaN.
        if (baselineMechanicalEnergy == 0.0) {
            return current != 0.0;
        }

        double relativeDrift = Math.abs(current - baselineMechanicalEnergy) / Math.abs(baselineMechanicalEnergy);

        return !Double.isFinite(relativeDrift)
                || relativeDrift > criteria.maximumRelativeEnergyDrift();
    }

    private double totalMechanicalEnergy(BodyPair bodies)
    {
        double[] masses = {
                bodies.reference().definition().mass(),
                bodies.learner().definition().mass()
        };
        StateVector[] states = {
                bodies.reference().state(),
                bodies.learner().state()
        };
        return EnergyCalculator.totalMechanicalEnergy(masses, states);
    }

    private void accumulateAngularProgress(double currentAzimuth) {
        double delta = currentAzimuth - previousAzimuth;

        // Map into (-pi, pi] so a wrap past 2pi reads as a small forward step
        if (delta > Math.PI)
        {
            delta -= TWO_PI;
        } else if (delta < -Math.PI) {
            delta += TWO_PI;
        }

        accumulatedAngularDisplacement += delta;
        previousAzimuth = currentAzimuth;
    }

    private boolean requiredRevolutionsReached()
    {

        // Magnitude, so a retrograde orbit counts as progress.
        return Math.abs(accumulatedAngularDisplacement)
                >= criteria.requiredRevolutions() * TWO_PI;
    }

    private BodyPair resolveBodies(SimulationSnapshot snapshot)
    {
        BodySnapshot reference = null;
        BodySnapshot learner = null;

        for (BodySnapshot body : snapshot.bodies())
        {
            String id = body.definition().id();

            if (referenceBodyId.equals(id)) {
                if (reference != null) {
                    throw new IllegalArgumentException("snapshot contains duplicate reference body id: " + referenceBodyId);
                }
                reference = body;
            }
            if (learnerBodyId.equals(id)) {
                if (learner != null) {
                    throw new IllegalArgumentException("snapshot contains duplicate learner body id: " + learnerBodyId);
                }
                learner = body;
            }
        }

        if (reference == null) {
            throw new IllegalArgumentException("snapshot does not contain reference body id: " + referenceBodyId);
        }
        if (learner == null) {
            throw new IllegalArgumentException("snapshot does not contain learner body id: " + learnerBodyId);
        }
        return new BodyPair(reference, learner);
    }

    private static String requireBodyId(String id, String name)
    {
        Objects.requireNonNull(id, name + " must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return id;
    }

    private static double separation(Vector3D a, Vector3D b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();

        double distance = Math.hypot(Math.hypot(dx, dy), dz); // Scaled magnitude, for the reasons given in OrbitalStateCalculator.
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("body separation is not representable");
        }
        return distance;
    }

    private record BodyPair(BodySnapshot reference, BodySnapshot learner) { }
}