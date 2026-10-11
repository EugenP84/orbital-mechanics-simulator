package io.github.eugenp84.orbitalsim.core.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateVectorTest {

    @Test
    void holdsPositionAndVelocity() {
        Vector3D p = new Vector3D(1, 2, 3);
        Vector3D v = new Vector3D(4, 5, 6);

        StateVector state = new StateVector(p, v);

        // Components are immutable, so retaining the same references is safe;
        // no defensive copying is required or tested for
        assertSame(p, state.position());
        assertSame(v, state.velocity());
    }

    @Test
    void nullPositionIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new StateVector(null, Vector3D.ZERO));
    }

    @Test
    void nullVelocityIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new StateVector(Vector3D.ZERO, null));
    }

    @Test
    void statesWithSameComponentsAreEqual() {
        StateVector a = new StateVector(new Vector3D(1, 2, 3), new Vector3D(4, 5, 6));
        StateVector b = new StateVector(new Vector3D(1, 2, 3), new Vector3D(4, 5, 6));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}