package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

import java.util.Objects;

/**
 * Shared contract checks for {@link Integrator} implementations.
 * <p>
 * Extracted once genuine duplication existed: Forward Euler verifies the
 * acceleration model once per step, Velocity Verlet twice, and Runge–Kutta 4
 * four times. A package-private helper is used rather than an abstract base
 * class, because the integrators share an interface rather than an is-a
 * relationship, and a base class would reimpose the constructor assumptions
 * that ADR-005 §3 deliberately avoids.
 */
final class IntegratorValidation {

    private IntegratorValidation() {
        // Static helper — not instantiable
    }

    /**
     * Verifies the caller-supplied arguments of
     * {@link Integrator#step(StateVector[], double, AccelerationModel)}.
     *
     * @throws NullPointerException     if any argument or state element is null
     * @throws IllegalArgumentException if the timestep is not positive and
     *         finite
     */
    static void requireValidArguments(
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
     * Verifies the {@link AccelerationModel} return contract (ADR-005 §4).
     * Failures indicate a collaborator defect rather than a caller error, so
     * {@link IllegalStateException} is raised.
     * <p>
     * The array is returned so that the check can wrap the model invocation
     * directly, keeping the validation adjacent to the call it guards.
     *
     * @param accelerations  the model's return value
     * @param expectedLength the number of states supplied to the model
     * @return {@code accelerations}, unchanged
     * @throws IllegalStateException if the array is null, has the wrong length,
     *         contains a null element, or contains a non-finite component
     */
    static Vector3D[] requireValidAccelerations(Vector3D[] accelerations, int expectedLength) {
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
        return accelerations;
    }
}