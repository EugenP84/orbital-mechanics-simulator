package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

import java.util.Objects;

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
 * step and global error O(Δt).
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

        requireValidArguments(currentStates, timeStepSeconds, accelerationModel);

        Vector3D[] accelerations = accelerationModel.accelerationsAt(currentStates);
        requireValidAccelerations(accelerations, currentStates.length);

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

    private static void requireValidArguments(
            StateVector[] currentStates,
            double timeStepSeconds,
            AccelerationModel accelerationModel) {

        Objects.requireNonNull(currentStates, "currentStates must not be null");
        Objects.requireNonNull(accelerationModel, "accelerationModel must not be null");

        if (timeStepSeconds <= 0.0 || !Double.isFinite(timeStepSeconds)) {
            throw new IllegalArgumentException(
                    "timeStepSeconds must be positive and finite, was: " + timeStepSeconds);
        }
        for (int i = 0; i < currentStates.length; i++) {
            Objects.requireNonNull(currentStates[i],
                    "state at index " + i + " must not be null");
        }
    }

    /**
     * Verifies the {@link AccelerationModel} return contract. Failures here
     * indicate a collaborator defect rather than a caller error, so
     * {@link IllegalStateException} is raised (ADR-005 §6).
     */
    private static void requireValidAccelerations(Vector3D[] accelerations, int expectedLength) {
        if (accelerations == null) {
            throw new IllegalStateException("AccelerationModel returned null");
        }
        if (accelerations.length != expectedLength) {
            throw new IllegalStateException(
                    "AccelerationModel returned " + accelerations.length
                            + " accelerations for " + expectedLength + " states");
        }
        for (int i = 0; i < accelerations.length; i++) {
            Vector3D acceleration = accelerations[i];

            if (acceleration == null) {
                throw new IllegalStateException(
                        "AccelerationModel returned null acceleration at index " + i);
            }
            if (!Double.isFinite(acceleration.x())
                    || !Double.isFinite(acceleration.y())
                    || !Double.isFinite(acceleration.z())) {
                throw new IllegalStateException(
                        "AccelerationModel returned non-finite acceleration at index " + i);
            }
        }
    }
}