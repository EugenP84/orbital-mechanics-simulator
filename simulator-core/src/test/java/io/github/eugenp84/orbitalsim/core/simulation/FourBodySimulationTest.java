package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.integrator.VelocityVerletIntegrator;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.physics.EnergyCalculator;
import io.github.eugenp84.orbitalsim.core.physics.GravitationalBodyConfig;
import io.github.eugenp84.orbitalsim.core.physics.NBodyAccelerationCalculator;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FourBodySimulationTest {

    private static final double G = PhysicalConstants.GRAVITATIONAL_CONSTANT;
    private static final double M_SUN = PhysicalConstants.SOLAR_MASS;
    private static final double M_EARTH = PhysicalConstants.EARTH_MASS;
    private static final double AU = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double DT = 3_600.0;          // 1 hour
    private static final double DURATION = 30.0 * 86_400.0;                          // 30 days

    @Test
    void fullyCoupledFourBodySystemConservesEnergyAndMomentum()
    {

        double[] masses = {
                M_SUN,
                M_EARTH,
                M_EARTH,
                M_EARTH
        };

        double[] radii = {
                AU,
                1.3 * AU,
                1.7 * AU
        };

        double[] angles = {
                0.0,
                2.0 * Math.PI / 3.0,
                4.0 * Math.PI / 3.0
        };

        StateVector[] states = new StateVector[4];
        Vector3D planetMomentum = Vector3D.ZERO;

        /*
         * Give each planet the circular-orbit speed appropriate to its initial distance from the star.
         * The different angular positions avoid a degenerate collinear geometry.
         */
        for (int i = 0; i < 3; i++) {

            double r = radii[i];
            double theta = angles[i];

            Vector3D position = new Vector3D(
                    r * Math.cos(theta),
                    r * Math.sin(theta),
                    0.0);

            double speed = Math.sqrt(G * M_SUN / r);

            Vector3D velocity = new Vector3D(
                    -speed * Math.sin(theta),
                    speed * Math.cos(theta),
                    0.0);

            states[i + 1] = new StateVector(position, velocity);

            planetMomentum = planetMomentum.add(velocity.multiply(M_EARTH));
        }

        /*
         * Give the star the compensating velocity required for zero total initial linear momentum.
         */
        states[0] = new StateVector(Vector3D.ZERO, planetMomentum.multiply(-1.0 / M_SUN));

        List<BodySnapshot> initialBodies = List.of(
                snapshot(
                        "sun",
                        "Sun",
                        CelestialBodyType.STAR,
                        M_SUN,
                        PhysicalConstants.SOLAR_RADIUS,
                        states[0]),

                snapshot(
                        "p1",
                        "Planet 1",
                        CelestialBodyType.PLANET,
                        M_EARTH,
                        PhysicalConstants.EARTH_RADIUS,
                        states[1]),

                snapshot(
                        "p2",
                        "Planet 2",
                        CelestialBodyType.PLANET,
                        M_EARTH,
                        PhysicalConstants.EARTH_RADIUS,
                        states[2]),

                snapshot(
                        "p3",
                        "Planet 3",
                        CelestialBodyType.PLANET,
                        M_EARTH,
                        PhysicalConstants.EARTH_RADIUS,
                        states[3])
        );

        /*
         * All four bodies contribute gravitationally.
         * This is therefore a fully coupled four-body simulation, not a restricted-body configuration.
         */
        GravitationalBodyConfig[] gravity = {
                GravitationalBodyConfig.contributing(M_SUN),
                GravitationalBodyConfig.contributing(M_EARTH),
                GravitationalBodyConfig.contributing(M_EARTH),
                GravitationalBodyConfig.contributing(M_EARTH)
        };

        double initialEnergy = EnergyCalculator.totalMechanicalEnergy(masses, states);

        Vector3D initialMomentum = totalMomentum(masses, states);

        SimulationEngine engine = new SimulationEngine(
                initialBodies,
                new NBodyAccelerationCalculator(gravity),
                DT,
                new VelocityVerletIntegrator());

        AdvanceResult result = engine.advanceBy(DURATION);

        /*
         * First establish the basic O2 requirement: the engine has actually propagated four bodies.
         */
        assertEquals(
                4,
                result.snapshot().bodies().size());

        assertEquals(
                720,
                result.stepsCommitted());

        StateVector[] finalStates =
                result.snapshot()
                        .bodies()
                        .stream()
                        .map(BodySnapshot::state)
                        .toArray(StateVector[]::new);

        double finalEnergy = EnergyCalculator.totalMechanicalEnergy(masses, finalStates);
        Vector3D finalMomentum = totalMomentum(masses, finalStates);
        double relativeEnergyDrift = Math.abs((finalEnergy - initialEnergy) / initialEnergy);

        /*
         * Initial total momentum is deliberately approximately zero, so dividing by it would be ill-conditioned.
         * Use a characteristic planetary momentum as the scale.
         */
        double momentumScale = M_EARTH * Math.sqrt(G * M_SUN / AU);

        double relativeMomentumResidual =
                finalMomentum
                        .subtract(initialMomentum)
                        .magnitude()
                        / momentumScale;

        assertTrue(
                relativeEnergyDrift < 1e-9,
                () -> "relative energy drift = "
                        + relativeEnergyDrift);

        assertTrue(
                relativeMomentumResidual < 1e-12,
                () -> "relative momentum residual = "
                        + relativeMomentumResidual);
    }

    private static BodySnapshot snapshot(
            String id,
            String name,
            CelestialBodyType type,
            double mass,
            double radius,
            StateVector state) {

        return new BodySnapshot(
                new CelestialBody(
                        id,
                        name,
                        type,
                        mass,
                        radius),
                state);
    }

    private static Vector3D totalMomentum(double[] masses, StateVector[] states)
    {
        Vector3D total = Vector3D.ZERO;

        for (int i = 0; i < masses.length; i++) {
            total = total.add(
                    states[i].velocity().multiply(masses[i]));
        }

        return total;
    }
}