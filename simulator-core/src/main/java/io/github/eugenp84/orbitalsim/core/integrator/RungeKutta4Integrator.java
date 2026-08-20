package io.github.eugenp84.orbitalsim.core.integrator;

import io.github.eugenp84.orbitalsim.core.math.StateVector;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.physics.AccelerationModel;

/**
 * Classical fourth-order Runge–Kutta integration.
 * <p>
 * Unlike Forward Euler and Velocity Verlet, this method treats the system as a
 * single first-order problem dy/dt = f(y) with y = (r, v) and
 * f(r, v) = (v, a(r)). Each stage therefore produces a complete derivative —
 * a velocity derivative and an acceleration derivative — and the update is a
 * weighted average of four such derivatives:
 * <pre>
 *     k₁ = ( v,                a(r) )
 *     k₂ = ( v + ½·k₁ₐ·Δt,     a(r + ½·k₁ᵥ·Δt) )
 *     k₃ = ( v + ½·k₂ₐ·Δt,     a(r + ½·k₂ᵥ·Δt) )
 *     k₄ = ( v + k₃ₐ·Δt,       a(r + k₃ᵥ·Δt) )
 *
 *     r(t + Δt) = r + (Δt/6)·(k₁ᵥ + 2·k₂ᵥ + 2·k₃ᵥ + k₄ᵥ)
 *     v(t + Δt) = v + (Δt/6)·(k₁ₐ + 2·k₂ₐ + 2·k₃ₐ + k₄ₐ)
 * </pre>
 * Every stage is constructed from the state at the start of the step, not
 * cumulatively from the preceding stage. The velocity derivatives require no
 * model evaluation, since the derivative of position is velocity; only the
 * acceleration derivatives do.
 * <p>
 * The method is fourth-order accurate: local truncation error is O(Δt⁵) and
 * global error O(Δt⁴), substantially more accurate per step than either of the
 * other implemented methods. It is not symplectic, and consequently does not
 * inherit the bounded long-term energy-error behaviour typically associated
 * with symplectic schemes such as Velocity Verlet; it may exhibit secular
 * energy drift over long integrations of conservative systems. The resulting
 * trade-off between high local accuracy and long-term qualitative behaviour is
 * one of the questions the comparison in Objective 6 is intended to
 * investigate.
 * <p>
 * <strong>Acceleration evaluations.</strong> This implementation performs
 * <strong>four</strong> evaluations per step, one per stage. That count is
 * intrinsic to classical RK4 rather than a consequence of the
 * {@link Integrator#step} contract, and is an implementation characteristic of
 * this method rather than part of that contract.
 * <p>
 * <strong>Trial states.</strong> Stages two through four evaluate the model at
 * trial states that are not the committed state of any body (ADR-004). Unlike
 * the deliberately staggered carrier constructed by
 * {@link VelocityVerletIntegrator}, each RK4 trial state is a complete
 * numerical approximation to position and velocity at the same stage time. It
 * is not necessarily the exact physical state at that instant, but its
 * components are temporally aligned within the RK4 construction. The velocity
 * component is nonetheless not read by a conforming {@link AccelerationModel},
 * which is contractually position-dependent only (ADR-005 §4); it is supplied
 * because it is the stage approximation the algorithm constructs.
 * <p>
 * The returned state is not guaranteed finite. A non-finite result indicates
 * numerical breakdown of the integration run rather than a defect, and is left
 * for the caller to detect and interpret (ADR-005 §9).
 * <p>
 * Instances are stateless and may be reused across steps and across systems.
 */
public final class RungeKutta4Integrator implements Integrator {

    @Override
    public StateVector[] step(
            StateVector[] currentStates,
            double timeStepSeconds,
            AccelerationModel accelerationModel) {

        IntegratorValidation.requireValidArguments(
                currentStates, timeStepSeconds, accelerationModel);

        int bodyCount = currentStates.length;
        double halfStep = 0.5 * timeStepSeconds;

        // Stage 1: derivative at the current state.
        // The velocity derivative is the current velocity itself.
        Vector3D[] k1Acceleration = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(currentStates), bodyCount);
        Vector3D[] k1Velocity = velocitiesOf(currentStates);

        // Stage 2: derivative at the midpoint reached using k1
        StateVector[] stage2States = advance(currentStates, k1Velocity, k1Acceleration, halfStep);
        Vector3D[] k2Acceleration = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(stage2States), bodyCount);
        Vector3D[] k2Velocity = velocitiesOf(stage2States);

        // Stage 3: derivative at the midpoint reached using k2
        StateVector[] stage3States = advance(currentStates, k2Velocity, k2Acceleration, halfStep);
        Vector3D[] k3Acceleration = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(stage3States), bodyCount);
        Vector3D[] k3Velocity = velocitiesOf(stage3States);

        // Stage 4: derivative at the endpoint reached using k3
        StateVector[] stage4States =
                advance(currentStates, k3Velocity, k3Acceleration, timeStepSeconds);
        Vector3D[] k4Acceleration = IntegratorValidation.requireValidAccelerations(
                accelerationModel.accelerationsAt(stage4States), bodyCount);
        Vector3D[] k4Velocity = velocitiesOf(stage4States);

        // Weighted average of the four derivatives: (Δt/6)·(k₁ + 2k₂ + 2k₃ + k₄)
        double sixthStep = timeStepSeconds / 6.0;
        StateVector[] nextStates = new StateVector[bodyCount];

        for (int i = 0; i < bodyCount; i++) {
            StateVector current = currentStates[i];

            Vector3D velocitySum = weightedSum(
                    k1Velocity[i], k2Velocity[i], k3Velocity[i], k4Velocity[i]);
            Vector3D accelerationSum = weightedSum(
                    k1Acceleration[i], k2Acceleration[i], k3Acceleration[i], k4Acceleration[i]);

            Vector3D nextPosition = current.position().add(velocitySum.multiply(sixthStep));
            Vector3D nextVelocity = current.velocity().add(accelerationSum.multiply(sixthStep));

            nextStates[i] = new StateVector(nextPosition, nextVelocity);
        }

        return nextStates;
    }

    /**
     * Builds the trial state for a stage: the state at the start of the step
     * advanced by {@code interval} using the supplied derivative. Position
     * advances by the velocity derivative and velocity by the acceleration
     * derivative, so both components of the resulting state belong to the same
     * stage time.
     * <p>
     * Each stage is advanced from {@code currentStates}, never from the
     * preceding trial state; advancing cumulatively is a common RK4
     * implementation error.
     */
    private static StateVector[] advance(
            StateVector[] currentStates,
            Vector3D[] velocityDerivative,
            Vector3D[] accelerationDerivative,
            double interval) {

        StateVector[] trialStates = new StateVector[currentStates.length];

        for (int i = 0; i < currentStates.length; i++) {
            StateVector current = currentStates[i];

            trialStates[i] = new StateVector(
                    current.position().add(velocityDerivative[i].multiply(interval)),
                    current.velocity().add(accelerationDerivative[i].multiply(interval)));
        }
        return trialStates;
    }

    private static Vector3D[] velocitiesOf(StateVector[] states) {
        Vector3D[] velocities = new Vector3D[states.length];
        for (int i = 0; i < states.length; i++) {
            velocities[i] = states[i].velocity();
        }
        return velocities;
    }

    /** k₁ + 2·k₂ + 2·k₃ + k₄ */
    private static Vector3D weightedSum(Vector3D k1, Vector3D k2, Vector3D k3, Vector3D k4) {
        return k1.add(k2.multiply(2.0)).add(k3.multiply(2.0)).add(k4);
    }
}