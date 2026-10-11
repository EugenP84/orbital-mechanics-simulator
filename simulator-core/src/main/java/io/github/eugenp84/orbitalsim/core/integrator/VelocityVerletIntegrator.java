package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

/**
 * Velocity Verlet integrator.
 * <p>
 * Each step computes:
 * <pre>
 *     v(t + Δt/2) = v(t) + ½ · a(t) · Δt
 *     r(t + Δt)   = r(t) + v(t + Δt/2) · Δt
 *     a(t + Δt)   = acceleration at the new positions
 *     v(t + Δt)   = v(t + Δt/2) + ½ · a(t + Δt) · Δt
 * </pre>
 * The method is second-order accurate and symplectic for the position-dependent accelerations supplied by {@link AccelerationModel}.
 * Energy is not conserved exactly, but for a stable timestep the energy error typically stays bounded and oscillatory.
 * <p>
 * Each step evaluates the acceleration model twice. The {@link Integrator#step} signature does not carry a(t + Δt) from one step to the next, so a(t) is recomputed at the start of every step.
 * <p>
 * The returned states may be non-finite if the integration breaks down numerically. Detecting this is the caller's responsibility.
 * <p>
 * Instances are stateless and can be shared.
 *
 * @author Eugeniu PLATITA
 */
public final class VelocityVerletIntegrator implements Integrator
{
    @Override
    public StateVector[] step(
            StateVector[] currentStates,
            double timeStepSeconds,
            AccelerationModel accelerationModel) {

        IntegratorValidation.requireValidArguments(currentStates, timeStepSeconds, accelerationModel);

        int bodyCount = currentStates.length;
        double halfStep = 0.5 * timeStepSeconds;

        // Stage 1: acceleration at the current state
        Vector3D[] initialAccelerations = IntegratorValidation.requireValidAccelerations(accelerationModel.accelerationsAt(currentStates), bodyCount);

        // Stages 2 and 3: half-step velocities, then new positions.
        // The trial states pair a position at t + Δt with a velocity at t + Δt/2; this is an evaluation carrier, not a physical snapshot.
        Vector3D[] halfStepVelocities = new Vector3D[bodyCount];
        StateVector[] trialStates = new StateVector[bodyCount];

        for (int i = 0; i < bodyCount; i++) {
            StateVector current = currentStates[i];
            halfStepVelocities[i] = current.velocity().add(initialAccelerations[i].multiply(halfStep));
            Vector3D nextPosition = current.position().add(halfStepVelocities[i].multiply(timeStepSeconds));
            trialStates[i] = new StateVector(nextPosition, halfStepVelocities[i]);
        }

        // Stage 4: acceleration at the new positions
        Vector3D[] finalAccelerations = IntegratorValidation.requireValidAccelerations(accelerationModel.accelerationsAt(trialStates), bodyCount);

        // Stage 5: complete the velocity update. The position is reused from the trial state; Vector3D is immutable, so no copy is required.
        StateVector[] nextStates = new StateVector[bodyCount];

        for (int i = 0; i < bodyCount; i++) {
            Vector3D nextVelocity = halfStepVelocities[i].add(finalAccelerations[i].multiply(halfStep));
            nextStates[i] = new StateVector(trialStates[i].position(), nextVelocity);
        }

        return nextStates;
    }
}