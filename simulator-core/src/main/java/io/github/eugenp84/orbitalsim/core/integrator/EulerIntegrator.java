package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

/**
 * Forward (explicit) Euler integration.
 * <p>
 * Both state components are advanced from their current values, using the
 * acceleration evaluated at the current state:
 * <pre>
 *     r(n+1) = r(n) + v(n) · Δt
 *     v(n+1) = v(n) + a(n) · Δt
 * </pre>
 * Because position is advanced using the <em>old</em> velocity, acceleration
 * does not influence position until the following step. This is a defining
 * characteristic of Forward Euler and contributes to the method's poor
 * long-term energy behaviour in orbital systems, making it a useful baseline
 * for comparison (Objective 6).
 * <p>
 * This is not semi-implicit (symplectic) Euler, which advances velocity first
 * and uses the updated velocity for the position update. The two differ
 * markedly in energy behaviour, so the distinction is maintained explicitly
 * (ADR-005 §11).
 * <p>
 * The method is first-order accurate: local truncation error is O(Δt²) per
 * step and global error O(Δt). It performs <strong>one</strong> acceleration
 * evaluation per step. That count is an implementation characteristic of this
 * method rather than part of the {@link Integrator} contract, and forms the
 * baseline for the computational comparison in Objective 6.
 * <p>
 * The returned state is not guaranteed finite. A non-finite result indicates
 * numerical breakdown of the integration run rather than a defect, and is left
 * for the caller to detect and interpret (ADR-005 §9).
 * <p>
 * Instances are stateless and may be reused across steps and across systems.
 */
public final class EulerIntegrator implements Integrator {

    @Override
    public StateVector[] step(
            StateVector[] currentStates,
            double timeStepSeconds,
            AccelerationModel accelerationModel) {

        IntegratorValidation.requireValidArguments(
                currentStates, timeStepSeconds, accelerationModel);

        Vector3D[] accelerations = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(currentStates), currentStates.length);

        StateVector[] nextStates = new StateVector[currentStates.length];

        for (int i = 0; i < currentStates.length; i++) {
            StateVector current = currentStates[i];

            Vector3D nextPosition = current.position()
                    .add(current.velocity().multiply(timeStepSeconds));
            Vector3D nextVelocity = current.velocity()
                    .add(accelerations[i].multiply(timeStepSeconds));

            nextStates[i] = new StateVector(nextPosition, nextVelocity);
        }

        return nextStates;
    }
}