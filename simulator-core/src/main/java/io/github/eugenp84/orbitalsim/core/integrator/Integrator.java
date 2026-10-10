package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

/**
 * A numerical method for advancing a system of bodies through one timestep.
 * <p>
 * Implementations are pure transformations of immutable state snapshots:
 * they consume the current state of every body and return the next state of every body, holding no state between calls and never touching runtime simulation objects.
 * Committing a returned snapshot is the responsibility of the simulation engine, and occurs only after a step returns successfully
 * <p>
 * The acceleration model is supplied per call rather than bound to an instance.
 * This keeps each strategy stateless and free of lifecycle assumptions, which is what allows the three methods to be compared head-to-head over one physical model
 * and one initial snapshot (Objective 6).
 * <p>
 * <strong>Array handling.</strong> The supplied array is a read-only snapshot container;
 * implementations must not modify it or replace any of its elements.
 * The returned array is distinct from the supplied one, has the same length, and preserves body order.
 * Individual returned states need not be newly allocated: {@link StateVector} is immutable, so returning an unchanged input element is safe.
 *
 * @author Eugeniu PLATITA
 */
public interface Integrator
{
    /**
     * Advances every body by one timestep.
     *
     * @param currentStates     state of each body at the start of the step; must not be modified by the implementation
     * @param timeStepSeconds   numerical step size in seconds; must be positive and finite. This is the integration step, not a presentation speed multiplier
     * @param accelerationModel supplies acceleration at any state required by the method
     * @return state of each body after the step, in corresponding body order; a zero-length array for a zero-length system
     * @throws NullPointerException     if any argument or state element is null
     * @throws IllegalArgumentException if {@code timeStepSeconds} is not positive and finite
     * @throws IllegalStateException    if the acceleration model violates its return contract
     */
    StateVector[] step(
            StateVector[] currentStates,
            double timeStepSeconds,
            AccelerationModel accelerationModel);
}