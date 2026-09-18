package io.github.eugenp84.orbitalsim.evaluation.integrator;

import io.github.eugenp84.orbitalsim.core.integrator.EulerIntegrator;
import io.github.eugenp84.orbitalsim.core.integrator.Integrator;
import io.github.eugenp84.orbitalsim.core.integrator.RungeKutta4Integrator;
import io.github.eugenp84.orbitalsim.core.integrator.VelocityVerletIntegrator;

/**
 * Numerical integration methods included in the controlled comparison experiment.
 *
 * @author Eugeniu PLATITA
 */
public enum IntegratorMethod
{
    FORWARD_EULER("Forward Euler"),
    VELOCITY_VERLET("Velocity Verlet"),
    RUNGE_KUTTA_4("RK4");

    private final String displayName;

    IntegratorMethod(final String displayName) {
        this.displayName = displayName;
    }

    /** Creates the integration strategy represented by this method. */
    public Integrator createIntegrator()
    {
        return switch (this)
        {
            case FORWARD_EULER -> new EulerIntegrator();
            case VELOCITY_VERLET -> new VelocityVerletIntegrator();
            case RUNGE_KUTTA_4 -> new RungeKutta4Integrator();
        };
    }

    /** Learner-facing name of this integration method. */
    public String displayName() {
        return displayName;
    }
}