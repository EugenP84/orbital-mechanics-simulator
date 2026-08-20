package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

/**
 * Velocity Verlet integration.
 * <p>
 * The method advances the system in four stages:
 * <pre>
 *     v(t + Δt/2) = v(t) + ½ · a(t) · Δt
 *     r(t + Δt)   = r(t) + v(t + Δt/2) · Δt
 *     a(t + Δt)   = acceleration evaluated at the new positions
 *     v(t + Δt)   = v(t + Δt/2) + ½ · a(t + Δt) · Δt
 * </pre>
 * Unlike Forward Euler, position is advanced using the half-step velocity, and
 * the velocity update uses the accelerations at both ends of the interval.
 * <p>
 * The method is second-order accurate and symplectic for the position-dependent
 * conservative systems supported by {@link AccelerationModel}. It does not
 * conserve energy exactly, but for stable timestep choices its long-term energy
 * error is typically bounded and oscillatory rather than exhibiting the
 * systematic secular drift characteristic of Forward Euler. It is widely used
 * for long-term integration of conservative mechanical systems, including
 * orbital problems, which makes it a useful contrast to Forward Euler in the
 * comparison required by Objective 6.
 * <p>
 * For constant acceleration, the Velocity Verlet update reproduces the
 * analytical constant-acceleration kinematic equations at the algorithmic
 * level: r = r₀ + v₀·t + ½·a·t² and v = v₀ + a·t, with no local truncation
 * error. In a floating-point implementation only arithmetic rounding remains.
 * This exactness is specific to constant acceleration; under gravity the
 * acceleration varies with position within the step, and the method's O(Δt³)
 * local truncation error applies as usual.
 * <p>
 * <strong>Acceleration evaluations.</strong> This implementation performs
 * <strong>two</strong> evaluations per step. Under the current
 * {@link Integrator#step} signature no previous acceleration is supplied to the
 * method, so a(t) must be recomputed even when the preceding step already
 * evaluated the same quantity. An alternative integration API that carried
 * a(t + Δt) between steps could reduce the steady-state cost to one new
 * evaluation per step, but that lies outside the contract selected by ADR-005.
 * The evaluation count is an implementation characteristic of this method
 * rather than part of the {@link Integrator} contract.
 * <p>
 * <strong>Trial states.</strong> The second evaluation is performed at trial
 * states containing the new positions. Because {@code AccelerationModel} is
 * contractually position-dependent only (ADR-005 §4), the velocity component of
 * these trial states is irrelevant to the acceleration calculation. The
 * half-step velocity is supplied because it is the algorithmically meaningful
 * intermediate velocity already available at this stage. The resulting
 * {@link StateVector} is therefore an evaluation carrier rather than a claim
 * that its position and velocity correspond to the same physical instant: the
 * position is at t + Δt while the velocity is at t + Δt/2.
 * <p>
 * The returned state is not guaranteed finite. A non-finite result indicates
 * numerical breakdown of the integration run rather than a defect, and is left
 * for the caller to detect and interpret (ADR-005 §9).
 * <p>
 * Instances are stateless and may be reused across steps and across systems.
 *
 * @author Eugeniu PLATITA
 */
public final class VelocityVerletIntegrator implements Integrator {

    @Override
    public StateVector[] step(
            StateVector[] currentStates,
            double timeStepSeconds,
            AccelerationModel accelerationModel) {

        IntegratorValidation.requireValidArguments(
                currentStates, timeStepSeconds, accelerationModel);

        int bodyCount = currentStates.length;
        double halfStep = 0.5 * timeStepSeconds;

        // Stage 1: acceleration at the current state
        Vector3D[] initialAccelerations = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(currentStates), bodyCount);

        // Stages 2 and 3: half-step velocities, then new positions.
        // The trial states pair a position at t + Δt with a velocity at
        // t + Δt/2; this is an evaluation carrier, not a physical snapshot.
        Vector3D[] halfStepVelocities = new Vector3D[bodyCount];
        StateVector[] trialStates = new StateVector[bodyCount];

        for (int i = 0; i < bodyCount; i++) {
            StateVector current = currentStates[i];

            halfStepVelocities[i] = current.velocity()
                    .add(initialAccelerations[i].multiply(halfStep));
            Vector3D nextPosition = current.position()
                    .add(halfStepVelocities[i].multiply(timeStepSeconds));

            trialStates[i] = new StateVector(nextPosition, halfStepVelocities[i]);
        }

        // Stage 4: acceleration at the new positions
        Vector3D[] finalAccelerations = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(trialStates), bodyCount);

        // Stage 5: complete the velocity update. The position is reused from
        // the trial state; Vector3D is immutable, so no copy is required.
        StateVector[] nextStates = new StateVector[bodyCount];

        for (int i = 0; i < bodyCount; i++) {
            Vector3D nextVelocity = halfStepVelocities[i]
                    .add(finalAccelerations[i].multiply(halfStep));

            nextStates[i] = new StateVector(trialStates[i].position(), nextVelocity);
        }

        return nextStates;
    }
}