package io.github.eugenp84.orbitalsim.evaluation.integrator;

import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;
import io.github.eugenp84.orbitalsim.core.physics.EnergyCalculator;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationEngine;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.StepResult;
import io.github.eugenp84.orbitalsim.core.simulation.TwoBodyInitialState;
import java.util.List;
import java.util.Objects;

/**
 * Represents one incremental execution of the controlled numerical-integrator comparison experiment.
 * <p>
 * A run owns one simulation engine, one selected integration method and the measurements accumulated from its committed states.
 * Each call to {@link #step()} advances by exactly one fixed integration timestep until the configured number of steps has been completed.
 * <p>
 * The physical configuration is a fully coupled solar-mass and Earth-mass two-body system at one astronomical unit, initialised for circular relative motion in the centre-of-mass inertial frame.
 * Trajectory error is measured against the analytical circular solution of the relative two-body motion.
 *
 * @author Eugeniu PLATITA
 */
public final class IntegratorComparisonRun
{
    private static final String STAR_ID = "comparison-star";
    private static final String PLANET_ID = "comparison-planet";
    private static final double STAR_MASS = PhysicalConstants.SOLAR_MASS;
    private static final double STAR_RADIUS = PhysicalConstants.SOLAR_RADIUS;
    private static final double PLANET_MASS = PhysicalConstants.EARTH_MASS;
    private static final double PLANET_RADIUS = PhysicalConstants.EARTH_RADIUS;
    private static final double ORBITAL_RADIUS = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double GRAVITATIONAL_PARAMETER = PhysicalConstants.GRAVITATIONAL_CONSTANT * (STAR_MASS + PLANET_MASS);
    private static final double ANGULAR_SPEED = Math.sqrt(GRAVITATIONAL_PARAMETER / (ORBITAL_RADIUS * ORBITAL_RADIUS * ORBITAL_RADIUS));
    private static final double CIRCULAR_SPEED = Math.sqrt(GRAVITATIONAL_PARAMETER / ORBITAL_RADIUS);
    private static final double[] MASSES = {STAR_MASS, PLANET_MASS};

    private static final CelestialBody STAR = new CelestialBody(
            STAR_ID,
            "Star",
            CelestialBodyType.STAR,
            STAR_MASS,
            STAR_RADIUS);

    private static final CelestialBody PLANET = new CelestialBody(
            PLANET_ID,
            "Planet",
            CelestialBodyType.PLANET,
            PLANET_MASS,
            PLANET_RADIUS);

    private final IntegratorMethod method;
    private final long targetSteps;
    private final CountingAccelerationModel accelerationModel;
    private final SimulationEngine engine;
    private final double initialEnergy;
    private double maxRelativeEnergyDeviation;
    private double maxTrajectoryErrorMetres;

    /**
     * Creates a fresh comparison run from the canonical initial conditions.
     *
     * @param method          integration method to evaluate
     * @param timeStepSeconds fixed integration timestep in seconds
     * @param targetSteps     number of steps required to complete the run
     * @throws IllegalArgumentException if timestep or step count is invalid
     */
    IntegratorComparisonRun(
            final IntegratorMethod method,
            final double timeStepSeconds,
            final long targetSteps)
    {
        this.method = Objects.requireNonNull(method, "method must not be null");

        if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0) {
            throw new IllegalArgumentException("timeStepSeconds must be positive and finite, was: " + timeStepSeconds);
        }

        if (targetSteps <= 0L)
        {
            throw new IllegalArgumentException("targetSteps must be positive, was: " + targetSteps);
        }

        this.targetSteps = targetSteps;
        this.accelerationModel = new CountingAccelerationModel(coupledTwoBodyGravity());
        final Integrator integrator = method.createIntegrator();

        this.engine = new SimulationEngine(
                initialBodies(),
                accelerationModel,
                timeStepSeconds,
                integrator);

        this.initialEnergy = totalMechanicalEnergy(engine.snapshot());
    }

    /**
     * Returns the analytical circular relative position at the supplied elapsed simulation time.
     *
     * @param simulationTimeSeconds elapsed simulation time in seconds
     * @return analytical position of the planet relative to the star
     */
    static Vector3D analyticalRelativePosition(final double simulationTimeSeconds)
    {
        if (!Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0.0) {
            throw new IllegalArgumentException("simulationTimeSeconds must be finite and non-negative, was: " + simulationTimeSeconds);
        }

        final double angle = ANGULAR_SPEED * simulationTimeSeconds;

        return new Vector3D(ORBITAL_RADIUS * Math.cos(angle),ORBITAL_RADIUS * Math.sin(angle),0.0);
    }

    /**
     * Reports whether the configured number of steps has been completed.
     *
     * @return true when the run is complete
     */
    public boolean isComplete()
    {
        return engine.snapshot().completedSteps() >= targetSteps;
    }

    /**
     * Returns the measurements accumulated through the current committed state.
     * <p>
     * Before the first step the maximum deviations and acceleration-evaluation
     * count are zero.
     *
     * @return current comparison measurements
     */
    public IntegratorComparisonResult result()
    {
        final SimulationSnapshot snapshot = engine.snapshot();

        return new IntegratorComparisonResult(
                method,
                snapshot.completedSteps(),
                snapshot.simulationTimeSeconds(),
                maxRelativeEnergyDeviation,
                maxTrajectoryErrorMetres,
                accelerationModel.evaluationCount());
    }

    /**
     * Returns the latest committed simulation state.
     *
     * @return current simulation snapshot
     */
    public SimulationSnapshot snapshot() {
        return engine.snapshot();
    }

    /**
     * Advances the comparison by one fixed integration timestep and updates the accumulated measurements from the resulting committed state.
     *
     * @return the committed simulation snapshot after the step
     * @throws IllegalStateException if the run is already complete or the
     *         simulation suffers numerical breakdown
     */
    public SimulationSnapshot step()
    {
        if (isComplete()) {
            throw new IllegalStateException("Comparison run has already completed " + targetSteps + " steps");
        }

        final StepResult stepResult = engine.step();

        return switch (stepResult)
        {
            case StepResult.Advanced advanced ->
            {
                final SimulationSnapshot snapshot = advanced.snapshot();
                updateMeasurements(snapshot);
                yield snapshot;
            }

            case StepResult.NumericalBreakdown breakdown ->
                    throw new IllegalStateException("Numerical breakdown at attempted simulation time " + breakdown.attemptedTimeSeconds() + " s, body index " + breakdown.firstNonFiniteBodyIndex());
        };
    }

    /**
     * Creates the fully coupled Newtonian gravitational model for the canonical two-body comparison system.
     *
     * @return acceleration model in which both bodies contribute gravity
     */
    private static AccelerationModel coupledTwoBodyGravity()
    {
        return new NBodyAccelerationCalculator(
                new GravitationalBodyConfig[]{
                        GravitationalBodyConfig.contributing(STAR_MASS),
                        GravitationalBodyConfig.contributing(PLANET_MASS)
                });
    }

    /**
     * Creates the canonical centre-of-mass initial state.
     *
     * @return initial body snapshots in star-then-planet order
     */
    private static List<BodySnapshot> initialBodies() {
        final Vector3D relativePosition = new Vector3D(ORBITAL_RADIUS, 0.0, 0.0);
        final Vector3D relativeVelocity = new Vector3D(0.0, CIRCULAR_SPEED, 0.0);

        return TwoBodyInitialState.centreOfMass(STAR, PLANET, relativePosition, relativeVelocity);
    }

    /**
     * Calculates relative total-mechanical-energy deviation from the initial energy.
     *
     * @param snapshot current committed simulation state
     * @return dimensionless relative energy deviation
     */
    private double relativeEnergyDeviation(final SimulationSnapshot snapshot)
    {
        final double currentEnergy = totalMechanicalEnergy(snapshot);
        return Math.abs(currentEnergy - initialEnergy) / Math.abs(initialEnergy);
    }

    /**
     * Calculates total mechanical energy of the canonical coupled system.
     *
     * @param snapshot committed simulation state
     * @return total mechanical energy in joules
     */
    private static double totalMechanicalEnergy(final SimulationSnapshot snapshot)
    {
        final StateVector[] states = snapshot.bodies().stream()
                .map(BodySnapshot::state)
                .toArray(StateVector[]::new);

        return EnergyCalculator.totalMechanicalEnergy(MASSES, states);
    }

    /**
     * Calculates numerical relative-position error against the analytical
     * circular orbit.
     *
     * @param snapshot current committed simulation state
     * @return relative-position error in metres
     */
    private static double trajectoryErrorMetres(final SimulationSnapshot snapshot)
    {
        final Vector3D numericalRelativePosition = snapshot.bodies().get(1).state().position().subtract(snapshot.bodies().get(0).state().position());
        final Vector3D exactRelativePosition = analyticalRelativePosition(snapshot.simulationTimeSeconds());
        return numericalRelativePosition.distanceTo(exactRelativePosition);
    }

    /**
     * Updates maximum measurements from one committed simulation state.
     *
     * @param snapshot newly committed simulation state
     */
    private void updateMeasurements(final SimulationSnapshot snapshot)
    {
        maxRelativeEnergyDeviation = Math.max(maxRelativeEnergyDeviation, relativeEnergyDeviation(snapshot));
        maxTrajectoryErrorMetres = Math.max(maxTrajectoryErrorMetres, trajectoryErrorMetres(snapshot));
    }

    /**
     * Counts actual acceleration-model invocations while leaving the delegated
     * physical calculation unchanged.
     *
     * @author Eugeniu PLATITA
     */
    private static final class CountingAccelerationModel implements AccelerationModel
    {
        private final AccelerationModel delegate;
        private long evaluationCount;

        /**
         * Creates a counting wrapper.
         *
         * @param delegate physical acceleration model to delegate to
         */
        private CountingAccelerationModel(final AccelerationModel delegate)
        {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        /**
         * Delegates acceleration calculation and records the invocation.
         *
         * @param states body states at which accelerations are evaluated
         * @return accelerations returned by the delegated physical model
         */
        @Override
        public Vector3D[] accelerationsAt(final StateVector[] states)
        {
            evaluationCount++;
            return delegate.accelerationsAt(states);
        }

        /**
         * Returns the number of acceleration-model invocations.
         *
         * @return invocation count
         */
        private long evaluationCount() {
            return evaluationCount;
        }
    }
}