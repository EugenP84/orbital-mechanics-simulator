package io.github.eugenp84.orbitalsim.learning.scenario;

import io.github.eugenp84.orbitalsim.core.integrator.VelocityVerletIntegrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationEngine;

import java.util.List;

/**
 * Assembles a runnable Level 1 attempt from the learner's chosen orbital speed.
 * <p>
 * This layer exists so that no caller — least of all the user interface — has to know how to construct a valid attempt.
 * Getting any part of it wrong (frame, integrator, gravitational parameter, cadence agreement) would produce
 * an assessment that looks plausible and is not, so the assembly lives in one place and enforces the Level 1 invariants itself.
 *
 * <h2>Invariants enforced here</h2>
 * <ul>
 *   <li><strong>Planar initial state.</strong> The learner supplies a scalar
 *       tangential speed, not a vector, so no out-of-plane component can be introduced. Level 1's sampling bound and revolution counting are valid
 *       only in the reference plane.</li>
 *   <li><strong>Centre-of-mass frame.</strong> The bodies are placed either side of the origin in inverse mass proportion, giving zero net
 *       momentum, so the energy-integrity measure is not distorted by a bulk kinetic term (ADR-003).</li>
 *   <li><strong>Fixed assessed integrator.</strong> Velocity Verlet, so a correct answer cannot fail because of the integrator (ADR-008 §5).
 *       Method comparison belongs to a separate exploration mode.</li>
 *   <li><strong>One shared cadence</strong> between session and policy, so the sampling validation applies to the cadence actually executed.</li>
 * </ul>
 *
 * <h2>Provisional configuration</h2>
 * The physical setup and the acceptance thresholds below are <strong>provisional and not yet calibrated</strong>.
 * ADR-008 p.6 requires them to be justified empirically before final evaluation; they are chosen here to be plausible and to let the level run, not because evidence has selected them.
 */
public final class Level1Scenario {

    private static final String STAR_ID = "l1-star";
    private static final String PLANET_ID = "l1-planet";

    private static final double STAR_MASS = PhysicalConstants.SOLAR_MASS;
    private static final double STAR_RADIUS = PhysicalConstants.SOLAR_RADIUS;
    private static final double PLANET_MASS = PhysicalConstants.EARTH_MASS;
    private static final double PLANET_RADIUS = PhysicalConstants.EARTH_RADIUS;
    private static final double ORBITAL_RADIUS = PhysicalConstants.ASTRONOMICAL_UNIT;

    /**
     * Six hours. Chosen to give a learning-observation cadence substantially finer than the orbital timescale of ordinary Level 1 attempts.
     * Whether a particular bound attempt is adequately sampled is determined by the completion policy against that attempt's own initial orbit,
     * not by this value alone. Provisional.
     */
    private static final double TIME_STEP_SECONDS = 21_600.0;
    private static final int STEPS_PER_OBSERVATION = 1;

    /** Provisional; see the class documentation. */
    private static final Level1Criteria PROVISIONAL_CRITERIA = new Level1Criteria(
            2,          // requiredRevolutions, per the project proposal
            0.1,        // maximumEccentricity: "roughly circular"
            0.0,        // minimumSurfaceClearanceMetres: surfaces touching is failure
            1.0e-3);    // maximumRelativeEnergyDrift: ~7 orders above measured Verlet drift

    private final double gravitationalParameter =
            PhysicalConstants.GRAVITATIONAL_CONSTANT * (STAR_MASS + PLANET_MASS);

    /** The circular speed at this scenario's orbital radius: the learner's target. */
    public double circularSpeedMetresPerSecond() {
        return Math.sqrt(gravitationalParameter / ORBITAL_RADIUS);
    }

    /** Initial separation between the bodies. */
    public double orbitalRadiusMetres() {
        return ORBITAL_RADIUS;
    }

    /**
     * Assembles an attempt in which the learner's body starts at the scenario's orbital radius moving tangentially at the given speed.
     *
     * @param initialTangentialSpeed speed in m/s; positive and finite
     * @return a ready-to-run attempt, not yet started
     * @throws IllegalArgumentException if the speed is not positive and finite,
     *         or if the resulting orbit is too eccentric to be counted reliably at this scenario's observation cadence — in which case the attempt
     *         could not be assessed and is refused rather than run to a meaningless verdict
     */
    public Level1Attempt createAttempt(double initialTangentialSpeed)
    {
        if (!Double.isFinite(initialTangentialSpeed) || initialTangentialSpeed <= 0.0)
        {
            throw new IllegalArgumentException("initialTangentialSpeed must be positive and finite, was: " + initialTangentialSpeed);
        }

        // Planar by construction: the learner supplies a scalar, so there is no way to introduce an out-of-plane component.
        Vector3D relativePosition = new Vector3D(ORBITAL_RADIUS, 0.0, 0.0);
        Vector3D relativeVelocity = new Vector3D(0.0, initialTangentialSpeed, 0.0);

        SimulationEngine engine = new SimulationEngine(
                centreOfMassBodies(relativePosition, relativeVelocity),
                coupledTwoBodyGravity(),
                TIME_STEP_SECONDS,
                new VelocityVerletIntegrator());

        // Compose the hint engine as an observer of committed states before handing the engine to the session.
        // The session remains the sole advancer (ADR-008 p4); this only attaches an observer at the composition point, so hints see the learning observation cadence.
        Level1HintEngine hintEngine = new Level1HintEngine(
                STAR_ID, PLANET_ID, gravitationalParameter,
                initialTangentialSpeed / circularSpeedMetresPerSecond());
        engine.addListener(hintEngine);

        ScenarioObservationCadence cadence = new ScenarioObservationCadence(TIME_STEP_SECONDS, STEPS_PER_OBSERVATION);

        Level1StableOrbitCompletionPolicy policy = new Level1StableOrbitCompletionPolicy(engine.snapshot(), STAR_ID, PLANET_ID, gravitationalParameter, cadence, PROVISIONAL_CRITERIA);

        return new Level1Attempt(
                new ScenarioSession(engine, policy, cadence),
                hintEngine,
                STAR_ID, PLANET_ID,
                ORBITAL_RADIUS,
                circularSpeedMetresPerSecond(),
                initialTangentialSpeed);
    } // ----- end createAttempt() -----------------------------------------------------

    /**
     * Places the two bodies either side of the origin in inverse mass proportion, so total momentum is zero and the total mechanical energy carries no bulk kinetic term.
     */
    private static List<BodySnapshot> centreOfMassBodies(Vector3D relativePosition, Vector3D relativeVelocity)
    {
        double totalMass = STAR_MASS + PLANET_MASS;
        double starFraction = -PLANET_MASS / totalMass;
        double planetFraction = STAR_MASS / totalMass;

        return List.of(
                new BodySnapshot(
                        new CelestialBody(STAR_ID, "Star", CelestialBodyType.STAR,
                                STAR_MASS, STAR_RADIUS),
                        new StateVector(
                                relativePosition.multiply(starFraction),
                                relativeVelocity.multiply(starFraction))),
                new BodySnapshot(
                        new CelestialBody(PLANET_ID, "Planet", CelestialBodyType.PLANET,
                                PLANET_MASS, PLANET_RADIUS),
                        new StateVector(
                                relativePosition.multiply(planetFraction),
                                relativeVelocity.multiply(planetFraction))));
    }

    /** Both bodies act on each other: the fully coupled two-body model. */
    private static NBodyAccelerationCalculator coupledTwoBodyGravity()
    {
        return new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{
                GravitationalBodyConfig.contributing(STAR_MASS),
                GravitationalBodyConfig.contributing(PLANET_MASS)});
    }
}