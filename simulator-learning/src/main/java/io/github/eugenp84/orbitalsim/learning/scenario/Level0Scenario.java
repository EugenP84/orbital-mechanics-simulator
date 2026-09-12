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
 * Assembles a runnable Level 0 (Planet Creation) attempt from the learner's chosen planet parameters:
 * mass, radius, initial orbital distance, and tangential speed.
 * <p>
 * Level 0 introduces the parameter space "before any orbital objective is imposed" (project concept).
 * The learner's tangential speed is therefore taken as given and used directly as the initial velocity — the planet is deliberately not placed on a derived circular orbit, because doing
 * so would impose the very orbital objective this level precedes.
 * The circular speed for the chosen distance is computed only as a read-only reference exposed on the attempt, to bridge into Level 1.
 * <p>
 * Level 0 has no physical assessment and no learner-failure path. Configuration validity is enforced here:
 * parameters must be positive and finite, and the planet must start physically clear of the star.
 * Completion is then unconditional (see {@link Level0PlanetCreationCompletionPolicy}).
 * The star is fixed; only the planet is configured.
 * The gravitational parameter uses the coupled two-body sum {@code G (M_star + m)} to match the rest of the simulation.
 *
 * @author Eugeniu PLATITA
 */
public final class Level0Scenario
{
    private static final String STAR_ID = "l0-star";
    private static final String PLANET_ID = "l0-planet";
    private static final double STAR_MASS = PhysicalConstants.SOLAR_MASS;
    private static final double STAR_RADIUS = PhysicalConstants.SOLAR_RADIUS;
    private static final double TIME_STEP_SECONDS = 21_600.0;
    private static final int STEPS_PER_OBSERVATION = 1;

    /**
     * Builds an attempt in which a planet of the given mass and radius starts at the given distance from the star, moving tangentially at the given speed.
     *
     * @param planetMassKilograms   the planet's mass; positive and finite
     * @param planetRadiusMetres    the planet's radius; positive and finite
     * @param orbitalDistanceMetres the initial star–planet distance; positive and finite, and greater than the sum of the star and planet radii so the bodies start clear
     * @param tangentialSpeedMetresPerSecond the initial tangential speed; positive and finite
     * @return a ready-to-run, not-yet-started attempt
     * @throws IllegalArgumentException if any argument is not positive and finite, or if the planet's surface would reach the star's surface at the initial distance
     */
    public Level0Attempt createAttempt(
            final double planetMassKilograms,
            final double planetRadiusMetres,
            final double orbitalDistanceMetres,
            final double tangentialSpeedMetresPerSecond)
    {
        requirePositiveFinite(planetMassKilograms, "planetMassKilograms");
        requirePositiveFinite(planetRadiusMetres, "planetRadiusMetres");
        requirePositiveFinite(orbitalDistanceMetres, "orbitalDistanceMetres");
        requirePositiveFinite(tangentialSpeedMetresPerSecond, "tangentialSpeedMetresPerSecond");

        final double minimumSeparation = STAR_RADIUS + planetRadiusMetres;
        if (orbitalDistanceMetres <= minimumSeparation)
        {
            throw new IllegalArgumentException(
                    "orbitalDistanceMetres must exceed the star and planet radii sum ("
                            + minimumSeparation + ") so the bodies start clear, was: "
                            + orbitalDistanceMetres);
        }

        final double gravitationalParameter = PhysicalConstants.GRAVITATIONAL_CONSTANT * (STAR_MASS + planetMassKilograms);
        final double circularSpeed = Math.sqrt(gravitationalParameter / orbitalDistanceMetres);

        final Vector3D relativePosition = new Vector3D(orbitalDistanceMetres, 0.0, 0.0);
        final Vector3D relativeVelocity = new Vector3D(0.0, tangentialSpeedMetresPerSecond, 0.0);

        final SimulationEngine engine = new SimulationEngine(
                centreOfMassBodies(relativePosition, relativeVelocity,
                        planetMassKilograms, planetRadiusMetres),
                coupledTwoBodyGravity(planetMassKilograms),
                TIME_STEP_SECONDS,
                new VelocityVerletIntegrator());

        final ScenarioObservationCadence cadence = new ScenarioObservationCadence(TIME_STEP_SECONDS, STEPS_PER_OBSERVATION);

        final ScenarioSession session = new ScenarioSession(
                engine, new Level0PlanetCreationCompletionPolicy(), cadence);

        return new Level0Attempt(
                session, STAR_ID, PLANET_ID,
                planetMassKilograms, planetRadiusMetres, orbitalDistanceMetres,
                tangentialSpeedMetresPerSecond, circularSpeed);
    }

    private static List<BodySnapshot> centreOfMassBodies(
            final Vector3D relativePosition,
            final Vector3D relativeVelocity,
            final double planetMassKilograms,
            final double planetRadiusMetres)
    {
        final double totalMass = STAR_MASS + planetMassKilograms;
        final double starFraction = -planetMassKilograms / totalMass;
        final double planetFraction = STAR_MASS / totalMass;

        return List.of(
                new BodySnapshot(
                        new CelestialBody(STAR_ID, "Star", CelestialBodyType.STAR,
                                STAR_MASS, STAR_RADIUS),
                        new StateVector(
                                relativePosition.multiply(starFraction),
                                relativeVelocity.multiply(starFraction))),
                new BodySnapshot(
                        new CelestialBody(PLANET_ID, "Planet", CelestialBodyType.PLANET,
                                planetMassKilograms, planetRadiusMetres),
                        new StateVector(
                                relativePosition.multiply(planetFraction),
                                relativeVelocity.multiply(planetFraction))));
    }

    private static NBodyAccelerationCalculator coupledTwoBodyGravity(final double planetMassKilograms)
    {
        return new NBodyAccelerationCalculator(new GravitationalBodyConfig[]{
                GravitationalBodyConfig.contributing(STAR_MASS),
                GravitationalBodyConfig.contributing(planetMassKilograms)});
    }

    private static void requirePositiveFinite(final double value, final String name)
    {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}