package io.github.eugenp84.orbitalsim.core.simulation;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBody;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TwoBodyInitialStateTest
{
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void preservesRelativePositionAndVelocity()
    {
        final CelestialBody body1 = body("body-1", 4.0);
        final CelestialBody body2 = body("body-2", 1.0);

        final Vector3D relativePosition = new Vector3D(10.0, 2.0, -1.0);
        final Vector3D relativeVelocity = new Vector3D(-3.0, 5.0, 2.0);

        final List<BodySnapshot> bodies = TwoBodyInitialState.centreOfMass(
                body1,
                body2,
                relativePosition,
                relativeVelocity);

        final Vector3D actualRelativePosition =
                bodies.get(1).state().position()
                        .subtract(bodies.get(0).state().position());

        final Vector3D actualRelativeVelocity =
                bodies.get(1).state().velocity()
                        .subtract(bodies.get(0).state().velocity());

        assertVectorEquals(relativePosition, actualRelativePosition);
        assertVectorEquals(relativeVelocity, actualRelativeVelocity);
    }

    @Test
    void placesCentreOfMassAtOrigin()
    {
        final CelestialBody body1 = body("body-1", 4.0);
        final CelestialBody body2 = body("body-2", 1.0);

        final List<BodySnapshot> bodies = TwoBodyInitialState.centreOfMass(
                body1,
                body2,
                new Vector3D(10.0, 2.0, -1.0),
                new Vector3D(-3.0, 5.0, 2.0));

        final Vector3D weightedPosition =
                bodies.get(0).state().position().multiply(body1.mass())
                        .add(bodies.get(1).state().position().multiply(body2.mass()));

        assertVectorEquals(Vector3D.ZERO, weightedPosition);
    }

    @Test
    void givesSystemZeroTotalMomentum()
    {
        final CelestialBody body1 = body("body-1", 4.0);
        final CelestialBody body2 = body("body-2", 1.0);

        final List<BodySnapshot> bodies = TwoBodyInitialState.centreOfMass(
                body1,
                body2,
                new Vector3D(10.0, 2.0, -1.0),
                new Vector3D(-3.0, 5.0, 2.0));

        final Vector3D momentum =
                bodies.get(0).state().velocity().multiply(body1.mass())
                        .add(bodies.get(1).state().velocity().multiply(body2.mass()));

        assertVectorEquals(Vector3D.ZERO, momentum);
    }

    private static void assertVectorEquals(
            final Vector3D expected,
            final Vector3D actual)
    {
        assertEquals(expected.x(), actual.x(), TOLERANCE);
        assertEquals(expected.y(), actual.y(), TOLERANCE);
        assertEquals(expected.z(), actual.z(), TOLERANCE);
    }

    private static CelestialBody body(final String id, final double mass)
    {
        return new CelestialBody(
                id,
                id,
                CelestialBodyType.PLANET,
                mass,
                1.0);
    }
}