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
import java.util.Objects;

/**
 * Assembles Level 4: the canonical 99942 Apophis close approach of 13 April 2029.
 * <p>
 * Production initial conditions are supplied through {@link Level4InitialConditions}.
 * This keeps the learning module independent of the Horizons adapter while allowing the application layer to map
 * the verified data-layer fixture into the scenario.
 * The scenario accepts only the empirically selected one-day-before epoch, JD 2462239.407091595 TDB.
 * <p>
 * The model contains the Sun and Earth as mutually contributing gravitational sources.
 * Apophis is a restricted body: it experiences their gravity but exerts no back-reaction.
 * The Sun begins at the heliocentric origin with zero velocity, matching the origin of the supplied Earth and Apophis
 * states. Velocity Verlet uses the frozen 112.5-second timestep, and every integration boundary is a learning observation.
 * The experiment completes after two simulated days.
 * <p>
 * Apophis' representative mass is 4.9e10 kg. Its 170 m representational radius corresponds to the 340 m mean diameter used for the project experiment.
 * Neither value influences the Sun-Earth trajectory because Apophis is non-contributing; its mass also cancels from its own acceleration.
 *
 * @author Eugeniu PLATITA
 */
public final class Level4Scenario
{
    private static final String APOPHIS_ID = "l4-apophis";
    private static final String EARTH_ID = "l4-earth";
    private static final String SUN_ID = "l4-sun";
    private static final double APOPHIS_MASS = 4.9e10;
    private static final double APOPHIS_RADIUS = 170.0;
    private static final double HORIZON_SECONDS = 172_800.0;
    private static final double INITIAL_JD_TDB = 2_462_239.407091595;
    private static final double TIME_STEP_SECONDS = 112.5;
    private static final int STEPS_PER_OBSERVATION = 1;

    /**
     * Builds the canonical Level 4 attempt from verified, SI-normalised Cartesian initial states.
     *
     * @param initialConditions Earth and Apophis heliocentric states at the frozen TDB epoch
     * @return ready-to-run Level 4 attempt
     * @throws NullPointerException     if initialConditions is null
     * @throws IllegalArgumentException if the supplied epoch is not the frozen production epoch
     */
    public Level4Attempt createAttempt(final Level4InitialConditions initialConditions)
    {
        Objects.requireNonNull(initialConditions, "initialConditions must not be null");
        if (Double.compare(initialConditions.jdTdb(), INITIAL_JD_TDB) != 0)
        {
            throw new IllegalArgumentException("Level 4 requires initial JD TDB " + INITIAL_JD_TDB + ", was: " + initialConditions.jdTdb());
        }

        final SimulationEngine engine = new SimulationEngine(
                initialBodies(initialConditions),
                restrictedGravity(),
                TIME_STEP_SECONDS,
                new VelocityVerletIntegrator());
        final ScenarioObservationCadence cadence = new ScenarioObservationCadence(TIME_STEP_SECONDS, STEPS_PER_OBSERVATION);
        final Level4CompletionPolicy policy = new Level4CompletionPolicy(engine.snapshot(), EARTH_ID, APOPHIS_ID, HORIZON_SECONDS);

        return new Level4Attempt(
                new ScenarioSession(engine, policy, cadence),
                policy,
                SUN_ID,
                EARTH_ID,
                APOPHIS_ID,
                initialConditions.jdTdb());
    }

    /** Frozen production initial epoch, as Julian Day TDB. */
    public double initialEpochJdTdb()
    {
        return INITIAL_JD_TDB;
    }

    /** Frozen physics timestep, in seconds. */
    public double timeStepSeconds()
    {
        return TIME_STEP_SECONDS;
    }

    private static List<BodySnapshot> initialBodies(final Level4InitialConditions conditions)
    {
        final CelestialBody apophis = new CelestialBody(
                APOPHIS_ID, "99942 Apophis", CelestialBodyType.ASTEROID, APOPHIS_MASS, APOPHIS_RADIUS);
        final CelestialBody earth = new CelestialBody(EARTH_ID, "Earth", CelestialBodyType.PLANET, PhysicalConstants.EARTH_MASS, PhysicalConstants.EARTH_RADIUS);
        final CelestialBody sun = new CelestialBody(SUN_ID, "Sun", CelestialBodyType.STAR, PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS);

        return List.of(
                new BodySnapshot(sun, new StateVector(
                        new Vector3D(0.0, 0.0, 0.0),
                        new Vector3D(0.0, 0.0, 0.0))),
                new BodySnapshot(earth, conditions.earthState()),
                new BodySnapshot(apophis, conditions.apophisState()));
    }

    private static NBodyAccelerationCalculator restrictedGravity()
    {
        return new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{
                GravitationalBodyConfig.contributing(PhysicalConstants.SOLAR_MASS),
                GravitationalBodyConfig.contributing(PhysicalConstants.EARTH_MASS),
                GravitationalBodyConfig.nonContributing(APOPHIS_MASS)});
    }
}
