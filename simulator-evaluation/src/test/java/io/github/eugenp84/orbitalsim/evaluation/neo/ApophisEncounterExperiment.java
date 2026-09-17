package io.github.eugenp84.orbitalsim.evaluation.neo;

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
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.StepResult;
import io.github.eugenp84.orbitalsim.data.horizons.HorizonsEpochState;

import java.util.List;
import java.util.Objects;

/**
 * Executes one deterministic headless Sun-Earth-Apophis propagation experiment.
 * <p>
 * Sun and Earth are mutually gravitating contributors.
 * Apophis is a restricted test body: it is accelerated by Sun and Earth but exerts no back-reaction.
 * The Earth and Apophis initial states are the common-epoch, Sun-centred geometric Horizons states normalised to SI by {@code simulator-data}.
 * The Sun is placed at the origin with zero velocity, which preserves the supplied relative initial states under a common translation and constant velocity
 * offset.
 * <p>
 * The experiment samples separation at the initial state and after every committed Velocity Verlet step.
 * It deliberately performs no interpolation. timestep convergence is evaluated separately before any refinement is added.
 *
 * @author Eugeniu PLATITA
 */
public final class ApophisEncounterExperiment
{

  private static final double APOPHIS_MASS_KILOGRAMS = 4.9e10;
  private static final double APOPHIS_RADIUS_METRES = 170.0;
  private static final double SECONDS_PER_DAY = 86_400.0;
  private static final int APOPHIS_INDEX = 2;
  private static final int EARTH_INDEX = 1;

  /**
   * Executes one experiment through the requested finite horizon.
   *
   * @param initialState paired Horizons Earth/Apophis state at one epoch
   * @param timeStepSeconds fixed Velocity Verlet step in seconds
   * @param horizonSeconds elapsed duration to propagate; must be an exact whole number of fixed timesteps
   * @return sampled closest-approach measurement for the completed run
   * @throws IllegalStateException if the simulation reports numerical breakdown
   */
  public EncounterMeasurement run(
      final HorizonsEpochState initialState,
      final double timeStepSeconds,
      final double horizonSeconds)
  {
    Objects.requireNonNull(initialState, "initialState must not be null");
    requirePositiveFinite(timeStepSeconds, "timeStepSeconds");
    requirePositiveFinite(horizonSeconds, "horizonSeconds");

    long requiredSteps = requireWholeStepHorizon(horizonSeconds, timeStepSeconds);
    SimulationEngine engine = createEngine(initialState, timeStepSeconds);
    MinimumAccumulator minimum = measurementAt(engine.snapshot());

    for (long step = 0L; step < requiredSteps; step++)
    {
      StepResult result = engine.step();
      if (result instanceof StepResult.NumericalBreakdown breakdown)
      {
        throw new IllegalStateException("Numerical breakdown at " + breakdown.attemptedTimeSeconds() + " s for body index " + breakdown.firstNonFiniteBodyIndex());
      }
      StepResult.Advanced advanced = (StepResult.Advanced) result;
      MinimumAccumulator candidate = measurementAt(advanced.snapshot());
      if (candidate.distanceMetres() < minimum.distanceMetres()) {
        minimum = candidate;
      }
    }

    return new EncounterMeasurement(
        requiredSteps,
        minimum.elapsedSeconds(),
        initialState.jdTdb(),
        minimum.distanceMetres(),
        initialState.jdTdb() + minimum.elapsedSeconds() / SECONDS_PER_DAY,
        minimum.relativeSpeedMetresPerSecond(),
        timeStepSeconds);
  }

  /**
   * Constructs the fixed restricted three-body model used by this experiment.
   * Apophis mass is retained as a representative physical definition value but cannot affect any acceleration because its gravity contribution is disabled.
   *
   * @param initialState paired Earth/Apophis initial state
   * @param timeStepSeconds fixed numerical timestep
   * @return newly initialised simulation engine
   */
  private static SimulationEngine createEngine(
      final HorizonsEpochState initialState,
      final double timeStepSeconds)
  {
    CelestialBody sun = new CelestialBody("sun", "Sun", CelestialBodyType.STAR, PhysicalConstants.SOLAR_MASS, PhysicalConstants.SOLAR_RADIUS);
    CelestialBody earth = new CelestialBody("earth", "Earth", CelestialBodyType.PLANET, PhysicalConstants.EARTH_MASS, PhysicalConstants.EARTH_RADIUS);
    CelestialBody apophis = new CelestialBody("apophis-99942", "99942 Apophis", CelestialBodyType.ASTEROID, APOPHIS_MASS_KILOGRAMS, APOPHIS_RADIUS_METRES);

    List<BodySnapshot> bodies = List.of(
        new BodySnapshot(sun, new StateVector(Vector3D.ZERO, Vector3D.ZERO)),
        new BodySnapshot(earth, initialState.earthState()),
        new BodySnapshot(apophis, initialState.apophisState()));

    GravitationalBodyConfig[] configs = {
        GravitationalBodyConfig.contributing(sun.mass()),
        GravitationalBodyConfig.contributing(earth.mass()),
        GravitationalBodyConfig.nonContributing(apophis.mass())
    };

    return new SimulationEngine(
        bodies,
        new NBodyAccelerationCalculator(configs),
        timeStepSeconds,
        new VelocityVerletIntegrator());
  }

  /**
   * Measures Earth-Apophis separation and relative speed in one committed snapshot.
   *
   * @param snapshot committed simulation snapshot
   * @return instantaneous encounter quantities
   */
  private static MinimumAccumulator measurementAt(final SimulationSnapshot snapshot)
  {
    StateVector earth = snapshot.bodies().get(EARTH_INDEX).state();
    StateVector apophis = snapshot.bodies().get(APOPHIS_INDEX).state();
    double distance = apophis.position().distanceTo(earth.position());
    double relativeSpeed = apophis.velocity().subtract(earth.velocity()).magnitude();
    return new MinimumAccumulator(distance, snapshot.simulationTimeSeconds(), relativeSpeed);
  }

  /**
   * Requires a horizon that can be reached without overshoot or a fractional final integration step.
   *
   * @param horizonSeconds requested propagation duration
   * @param timeStepSeconds fixed numerical timestep
   * @return exact number of required fixed steps
   */
  private static long requireWholeStepHorizon(
      final double horizonSeconds,
      final double timeStepSeconds)
  {
    double quotient = horizonSeconds / timeStepSeconds;
    long rounded = Math.round(quotient);
    if (rounded <= 0L || Math.abs(quotient - rounded) > 1.0e-12) {
      throw new IllegalArgumentException("horizonSeconds must be a positive whole number of timesteps");
    }
    return rounded;
  }

  /**
   * Requires a finite, strictly positive experiment parameter.
   *
   * @param value value to validate
   * @param name parameter name for diagnostics
   */
  private static void requirePositiveFinite(final double value, final String name) {
    if (!Double.isFinite(value) || value <= 0.0)
    {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  /**
   * Internal instantaneous measurement used while searching for the sampled minimum.
   *
   * @param distanceMetres Earth-Apophis separation
   * @param elapsedSeconds elapsed simulation time
   * @param relativeSpeedMetresPerSecond Earth-Apophis relative speed
   *
   * @author Eugeniu PLATITA
   */
  private record MinimumAccumulator(
      double distanceMetres,
      double elapsedSeconds,
      double relativeSpeedMetresPerSecond) {
  }
}
