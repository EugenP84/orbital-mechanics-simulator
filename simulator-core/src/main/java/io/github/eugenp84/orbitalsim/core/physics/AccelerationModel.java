package io.github.eugenp84.orbitalsim.core.physics;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;

/**
 * Supplies the acceleration acting on each body of a system at an arbitrary
 * supplied state.
 * <p>
 * Numerical integrators depend on this abstraction rather than on any
 * particular implementation. Multi-stage methods evaluate it several times
 * within a single step, at intermediate trial states that are not committed
 * simulation state (ADR-004, ADR-005). The production implementation is
 * {@link NBodyAccelerationCalculator}, which supplies Newtonian gravitational
 * acceleration; the abstraction itself is mathematical, and simpler models are
 * legitimate — a constant-acceleration implementation, for instance, allows an
 * integrator to be tested independently of gravity.
 * <p>
 * <strong>Model scope.</strong> An implementation must determine acceleration
 * from body positions and its own fixed configuration only. Velocity is
 * present in the supplied {@link StateVector} because integrators operate on
 * complete state snapshots, but it must not influence the computed
 * acceleration.
 * <p>
 * This restriction exists because standard Velocity Verlet evaluates
 * acceleration at the updated position before completing its velocity update,
 * and therefore assumes acceleration is independent of velocity. Permitting
 * velocity-dependent models would leave Forward Euler and Runge–Kutta 4 valid
 * while invalidating Velocity Verlet over the same model, defeating the
 * substitutability of the three integration strategies (ADR-005 §4).
 * <p>
 * <strong>Array handling.</strong> The supplied array is a read-only snapshot
 * container. An implementation must not modify it or replace any of its
 * elements: a multi-stage integrator may still be using that array for a later
 * stage of the same step.
 *
 * @author Eugeniu PLATITA
 */
public interface AccelerationModel {

    /**
     * Acceleration on each body at the supplied states.
     * <p>
     * An implementation may reject state that its own model cannot evaluate —
     * coincident bodies, for instance, or a configuration whose result is not
     * representable. The exception type is left to the implementation, since
     * what constitutes invalid geometry is model-specific.
     *
     * @param states state of each body; the array and every element must be
     *               non-null, and the array must not be modified by the
     *               implementation
     * @return a non-null array containing exactly one acceleration in m·s⁻²
     *         per supplied state, in corresponding body order, with no null
     *         elements and with finite components throughout
     */
    Vector3D[] accelerationsAt(StateVector[] states);
}