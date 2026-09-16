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
 * Assembles a runnable Level 3 (Gravitational Perturbation) attempt: a Sun-like star, an Earth-like
 * target on an initially near-circular orbit at 1 AU, and a learner-configurable perturbing planet
 * whose mass and initial stellar distance are the only learner variables.
 * <p>
 * The physical setup, numerical configuration and learner envelope are empirically calibrated and
 * fixed here (see the Level 3 behavioural specification and {@code Level3CalibrationSweep}). All three
 * bodies gravitate through the fully coupled {@link NBodyAccelerationCalculator}. Bodies are placed
 * coplanar with the target at phase 0 degrees and the perturber at phase +90 degrees — a controlled
 * initial condition, not a physically privileged configuration — each given the osculating two-body
 * circular speed about the star, and the whole configuration is then translated and boosted into the
 * system centre-of-mass inertial frame (zero total momentum). These are controlled near-circular
 * initial conditions, not an exact three-body equilibrium; once running, all three bodies interact
 * through the full model.
 * <p>
 * The integrator is fixed to Velocity Verlet at a 6-hour timestep, with a six-year observation
 * horizon — a scenario-specific calibrated choice, not a claim of accuracy for arbitrary three-body
 * systems. Level 3 is self-contained and does not require a planet created in Level 0.
 *
 * @author Eugeniu PLATITA
 */
public final class Level3Scenario
{
    private static final String STAR_ID = "l3-star";
    private static final String TARGET_ID = "l3-target";
    private static final String PERTURBER_ID = "l3-perturber";

    private static final double STAR_MASS = PhysicalConstants.SOLAR_MASS;
    private static final double STAR_RADIUS = PhysicalConstants.SOLAR_RADIUS;
    private static final double TARGET_MASS = PhysicalConstants.EARTH_MASS;
    private static final double TARGET_RADIUS = PhysicalConstants.EARTH_RADIUS;
    private static final double TARGET_DISTANCE = PhysicalConstants.ASTRONOMICAL_UNIT;

    // A fixed representative giant-planet radius (Jupiter's equatorial radius) used for the whole calibrated perturber mass range.
    // Radius does not affect gravity; it is used only by the policy's defensive surface-contact check.
    private static final double PERTURBER_RADIUS = 7.1492e7;
    private static final double JUPITER_MASS = 317.8 * PhysicalConstants.EARTH_MASS;

    private static final double TIME_STEP_SECONDS = 21_600.0;               // 6 h
    private static final int STEPS_PER_OBSERVATION = 1;
    private static final double HORIZON_SECONDS = 6.0 * 365.25 * 86_400.0;  // 6 years

    // Empirically calibrated learner envelope.
    private static final double MIN_PERTURBER_MASS = 0.3 * JUPITER_MASS;
    private static final double MAX_PERTURBER_MASS = 3.0 * JUPITER_MASS;
    private static final double MIN_PERTURBER_DISTANCE = 1.3 * PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double MAX_PERTURBER_DISTANCE = 2.0 * PhysicalConstants.ASTRONOMICAL_UNIT;

    private final double targetGravitationalParameter =
            PhysicalConstants.GRAVITATIONAL_CONSTANT * (STAR_MASS + TARGET_MASS);

    /** The learner-configurable perturber mass envelope, in kilograms. */
    public double minPerturberMassKilograms() {
        return MIN_PERTURBER_MASS;
    }

    /** The learner-configurable perturber mass envelope, in kilograms. */
    public double maxPerturberMassKilograms()
    {
        return MAX_PERTURBER_MASS;
    }

    /** The learner-configurable perturber initial stellar-distance envelope, in metres. */
    public double minPerturberDistanceMetres() {
        return MIN_PERTURBER_DISTANCE;
    }

    /** The learner-configurable perturber initial stellar-distance envelope, in metres. */
    public double maxPerturberDistanceMetres()
    {
        return MAX_PERTURBER_DISTANCE;
    }

    /**
     * Assembles an attempt with a perturbing planet of the given mass at the given initial stellar
     * distance.
     *
     * @param perturberMassKilograms    the perturbing planet's mass; within the supported envelope
     * @param perturberDistanceMetres   the perturber's initial stellar distance; within the envelope
     * @return a ready-to-run, not-yet-started attempt
     * @throws IllegalArgumentException if either argument is non-finite or outside the supported
     *                                  learner envelope
     */
    public Level3Attempt createAttempt(
            final double perturberMassKilograms, final double perturberDistanceMetres)
    {
        requireInRange(perturberMassKilograms, MIN_PERTURBER_MASS, MAX_PERTURBER_MASS,"perturberMassKilograms");
        requireInRange(perturberDistanceMetres, MIN_PERTURBER_DISTANCE, MAX_PERTURBER_DISTANCE,"perturberDistanceMetres");

        final SimulationEngine engine = new SimulationEngine(centreOfMassBodies(perturberMassKilograms, perturberDistanceMetres), coupledThreeBodyGravity(perturberMassKilograms), TIME_STEP_SECONDS, new VelocityVerletIntegrator());

        final ScenarioObservationCadence cadence = new ScenarioObservationCadence(TIME_STEP_SECONDS, STEPS_PER_OBSERVATION);

        final Level3CompletionPolicy policy = new Level3CompletionPolicy(
                engine.snapshot(), STAR_ID, TARGET_ID, PERTURBER_ID,
                targetGravitationalParameter, HORIZON_SECONDS);

        return new Level3Attempt(
                new ScenarioSession(engine, policy, cadence),
                policy,
                STAR_ID, TARGET_ID, PERTURBER_ID,
                perturberMassKilograms, perturberDistanceMetres);
    }

    private static List<BodySnapshot> centreOfMassBodies(final double perturberMass, final double perturberDistance)
    {
        // Osculating two-body circular speeds about the star (initial approximation, not equilibrium).
        final double vTarget = Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT * STAR_MASS / TARGET_DISTANCE);
        final double vPerturber = Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT * STAR_MASS / perturberDistance);

        // Target at phase 0 (+x, moving +y); perturber at phase +90 degrees (+y, moving -x).
        final double[] mass = {STAR_MASS, TARGET_MASS, perturberMass};
        final Vector3D[] position = {
                new Vector3D(0.0, 0.0, 0.0),
                new Vector3D(TARGET_DISTANCE, 0.0, 0.0),
                new Vector3D(0.0, perturberDistance, 0.0)};
        final Vector3D[] velocity = {
                new Vector3D(0.0, 0.0, 0.0),
                new Vector3D(0.0, vTarget, 0.0),
                new Vector3D(-vPerturber, 0.0, 0.0)};

        // Translate and boost into the system centre-of-mass inertial frame (zero total momentum).
        final double totalMass = mass[0] + mass[1] + mass[2];
        Vector3D comPosition = new Vector3D(0.0, 0.0, 0.0);
        Vector3D comVelocity = new Vector3D(0.0, 0.0, 0.0);
        for (int i = 0; i < mass.length; i++)
        {
            comPosition = comPosition.add(position[i].multiply(mass[i] / totalMass));
            comVelocity = comVelocity.add(velocity[i].multiply(mass[i] / totalMass));
        }

        final CelestialBody star = new CelestialBody(STAR_ID, "Star", CelestialBodyType.STAR, STAR_MASS, STAR_RADIUS);
        final CelestialBody target = new CelestialBody(TARGET_ID, "Target", CelestialBodyType.PLANET, TARGET_MASS, TARGET_RADIUS);
        final CelestialBody perturber = new CelestialBody(PERTURBER_ID, "Perturber", CelestialBodyType.PLANET, perturberMass, PERTURBER_RADIUS);
        final CelestialBody[] definitions = {star, target, perturber};

        final Vector3D comPos = comPosition;
        final Vector3D comVel = comVelocity;
        return List.of(
                new BodySnapshot(definitions[0], new StateVector(
                        position[0].subtract(comPos), velocity[0].subtract(comVel))),
                new BodySnapshot(definitions[1], new StateVector(
                        position[1].subtract(comPos), velocity[1].subtract(comVel))),
                new BodySnapshot(definitions[2], new StateVector(
                        position[2].subtract(comPos), velocity[2].subtract(comVel))));
    }

    private static NBodyAccelerationCalculator coupledThreeBodyGravity(final double perturberMass)
    {
        return new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{
                GravitationalBodyConfig.contributing(STAR_MASS),
                GravitationalBodyConfig.contributing(TARGET_MASS),
                GravitationalBodyConfig.contributing(perturberMass)});
    }

    private static void requireInRange(final double value, final double min, final double max, final String name)
    {
        if (!Double.isFinite(value) || value < min || value > max)
        {
            throw new IllegalArgumentException(name + " must be within [" + min + ", " + max + "], was: " + value);
        }
    }
}