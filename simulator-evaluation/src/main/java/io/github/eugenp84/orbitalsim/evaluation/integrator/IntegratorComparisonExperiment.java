package io.github.eugenp84.orbitalsim.evaluation.integrator;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import java.util.Objects;

/**
 * Defines and executes the controlled experiment used to compare the three numerical integration methods.
 * <p>
 * The baseline experiment uses a fixed six-hour integration timestep for 1,461 steps, corresponding to 365.25 simulated days.
 * Every run starts from the same canonical circular two-body initial state.
 * <p>
 * A complete experiment may be executed headlessly through {@link #run}, while {@link #createRun} exposes the same experiment as an incremental run for presentation without duplicating its numerical or measurement logic.
 *
 * @author Eugeniu PLATITA
 */
public final class IntegratorComparisonExperiment {
    public static final double TIME_STEP_SECONDS = 21_600.0;
    public static final long COMPARISON_STEPS = 1_461L;

    /**
     * Returns the analytical circular relative position used by the comparison.
     *
     * @param simulationTimeSeconds elapsed simulation time in seconds
     * @return analytical position of the planet relative to the star
     */
    static Vector3D analyticalRelativePosition(final double simulationTimeSeconds)
    {
        return IntegratorComparisonRun.analyticalRelativePosition(simulationTimeSeconds);
    }

    /**
     * Creates a fresh incremental baseline run for the selected integration method.
     *
     * @param method integration method to evaluate
     * @return fresh run at the baseline timestep and duration
     */
    public IntegratorComparisonRun createRun(final IntegratorMethod method)
    {
        Objects.requireNonNull(method, "method must not be null");
        return new IntegratorComparisonRun(method, TIME_STEP_SECONDS, COMPARISON_STEPS);
    }

    /**
     * Runs the baseline approximately one-orbit comparison for one integrator.
     *
     * @param method integration method to evaluate
     * @return quantitative result of the completed run
     */
    public IntegratorComparisonResult run(final IntegratorMethod method)
    {
        Objects.requireNonNull(method, "method must not be null");
        return run(method, TIME_STEP_SECONDS, COMPARISON_STEPS);
    }

    /**
     * Runs the controlled comparison for a specified fixed timestep and number of steps.
     *
     * @param method          integration method to evaluate
     * @param timeStepSeconds fixed integration timestep in seconds
     * @param steps           number of integration steps to execute
     * @return quantitative result of the completed run
     * @throws IllegalArgumentException if timestep or step count is invalid
     * @throws IllegalStateException if the simulation suffers numerical breakdown
     */
    public IntegratorComparisonResult run(final IntegratorMethod method, final double timeStepSeconds, final long steps)
    {
        Objects.requireNonNull(method, "method must not be null");

        final IntegratorComparisonRun run = new IntegratorComparisonRun(method, timeStepSeconds, steps);
        while (!run.isComplete()) {
            run.step();
        }
        return run.result();
    }
}