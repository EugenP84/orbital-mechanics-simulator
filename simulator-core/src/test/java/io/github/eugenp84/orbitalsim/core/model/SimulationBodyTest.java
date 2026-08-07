package io.github.eugenp84.orbitalsim.core.model;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationBodyTest {

    private static final CelestialBody EARTH =
            new CelestialBody("earth", "Earth", CelestialBodyType.PLANET, 5.9722e24, 6.371e6);

    private static final StateVector INITIAL =
            new StateVector(new Vector3D(1.496e11, 0, 0), new Vector3D(0, 2.978e4, 0));

    @Test
    void holdsDefinitionAndInitialState() {
        SimulationBody body = new SimulationBody(EARTH, INITIAL);
        assertSame(EARTH, body.definition());
        assertSame(INITIAL, body.state());
    }

    @Test
    void updateStateReplacesSnapshot() {
        SimulationBody body = new SimulationBody(EARTH, INITIAL);
        StateVector next = new StateVector(new Vector3D(1.5e11, 1e9, 0), new Vector3D(-100, 2.97e4, 0));

        body.updateState(next);

        assertSame(next, body.state());
        assertSame(EARTH, body.definition()); // definition untouched
    }

    @Test
    void nullDefinitionIsRejected() {
        assertThrows(NullPointerException.class, () -> new SimulationBody(null, INITIAL));
    }

    @Test
    void nullStateUpdateIsRejected() {
        SimulationBody body = new SimulationBody(EARTH, INITIAL);
        assertThrows(NullPointerException.class, () -> body.updateState(null));
    }

    @Test
    void definitionRemainsSharedAcrossBodies() {
        // ADR-002: identical definitions reused across integrator runs
        SimulationBody run1 = new SimulationBody(EARTH, INITIAL);
        SimulationBody run2 = new SimulationBody(EARTH, INITIAL);

        run1.updateState(new StateVector(Vector3D.ZERO, Vector3D.ZERO));

        assertSame(run1.definition(), run2.definition());
        assertSame(INITIAL, run2.state()); // run2 unaffected by run1's update
    }
}