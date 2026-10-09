package io.github.eugenp84.orbitalsim.app.controller;

import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonExperiment;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonResult;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonRun;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorMethod;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives the interactive numerical-integrator comparison on behalf of the
 * application view.
 * <p>
 * The controller owns one incremental comparison run at a time and retains completed results for the three integration methods.
 * It controls only presentation lifecycle and playback rate; the experiment continues to own the physical configuration, fixed numerical timestep, duration and measurement definitions.
 * <p>
 * Wall-clock advancement is converted into simulated time and accumulated.
 * The underlying run is advanced only in complete fixed integration steps,
 * so changing presentation speed never changes the numerical timestep.
 * <p>
 * This controller is JavaFX-free. The view may therefore observe and drive it without introducing presentation dependencies into the evaluation layer.
 *
 * @author Eugeniu PLATITA
 */
public final class IntegratorComparisonController
{
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND = 14.0 * SECONDS_PER_DAY;

    private final IntegratorComparisonExperiment experiment;
    private final Map<IntegratorMethod, IntegratorComparisonResult>
            completedResults = new EnumMap<>(IntegratorMethod.class);

    private IntegratorMethod selectedMethod;
    private IntegratorComparisonRun run;
    private IntegratorComparisonState state;
    private double simulatedSecondsPerRealSecond;
    private double pendingSimulatedSeconds;

    /**
     * Creates a controller with Forward Euler selected and a fresh baseline run.
     */
    public IntegratorComparisonController() {
        this(new IntegratorComparisonExperiment());
    }

    /**
     * Creates a controller around the supplied comparison experiment.
     *
     * @param experiment controlled comparison experiment; never null
     */
    IntegratorComparisonController(final IntegratorComparisonExperiment experiment)
    {
        this.experiment = Objects.requireNonNull(experiment, "experiment must not be null");
        this.selectedMethod = IntegratorMethod.FORWARD_EULER;
        this.simulatedSecondsPerRealSecond = DEFAULT_SIMULATED_SECONDS_PER_REAL_SECOND;
        replaceRun();
    }

    /**
     * Advances the current comparison according to an elapsed wall-clock
     * interval.
     * <p>
     * Advancement occurs only while running.
     * Simulated time that is insufficient for a complete fixed integration step is retained for a later call.
     *
     * @param wallClockSeconds elapsed real time; non-negative and finite
     * @throws IllegalArgumentException if the interval is negative or non-finite, or if conversion to simulated time overflows
     */
    public void advanceByWallClock(final double wallClockSeconds)
    {
        if (!Double.isFinite(wallClockSeconds) || wallClockSeconds < 0.0)
        {
            throw new IllegalArgumentException("wallClockSeconds must be non-negative and finite, was: " + wallClockSeconds);
        }

        if (state != IntegratorComparisonState.RUNNING) {
            return;
        }

        final double simulatedSeconds = wallClockSeconds * simulatedSecondsPerRealSecond;

        if (!Double.isFinite(simulatedSeconds))
        {
            throw new IllegalArgumentException(
                    "wall-clock interval and presentation speed produce a "
                            + "non-finite duration: "
                            + wallClockSeconds
                            + " * "
                            + simulatedSecondsPerRealSecond);
        }

        pendingSimulatedSeconds += simulatedSeconds;

        if (!Double.isFinite(pendingSimulatedSeconds))
        {
            throw new IllegalArgumentException("accumulated simulated duration became non-finite");
        }

        while (pendingSimulatedSeconds >= IntegratorComparisonExperiment.TIME_STEP_SECONDS)
        {
            run.step();

            pendingSimulatedSeconds -= IntegratorComparisonExperiment.TIME_STEP_SECONDS;

            if (run.isComplete())
            {
                completeRun();
                return;
            }
        }
    }

    /**
     * Reports whether the selected integration method may be changed.
     *
     * @return true unless the current run is running or paused
     */
    public boolean canSelectMethod()
    {
        return state == IntegratorComparisonState.READY
                || state == IntegratorComparisonState.COMPLETED;
    }

    /**
     * Returns an immutable view of the results completed during this application
     * session.
     *
     * @return completed results keyed by integration method
     */
    public Map<IntegratorMethod, IntegratorComparisonResult> completedResults()
    {
        return Collections.unmodifiableMap(completedResults);
    }

    /**
     * Returns the measurements accumulated by the current run.
     *
     * @return current comparison measurements
     */
    public IntegratorComparisonResult currentResult()
    {
        return run.result();
    }

    /**
     * Returns the current presentation lifecycle state.
     *
     * @return comparison state
     */
    public IntegratorComparisonState state()
    {
        return state;
    }

    /**
     * Returns the completed result for one integration method, if that method has completed during this application session.
     *
     * @param method integration method
     * @return completed result, or empty when no completed run is retained
     */
    public Optional<IntegratorComparisonResult> completedResult(final IntegratorMethod method)
    {
        Objects.requireNonNull(method, "method must not be null");

        return Optional.ofNullable(completedResults.get(method));
    }

    /**
     * Replaces the current run with a fresh run of the selected method.
     * <p>
     * Previously completed results are retained.
     */
    public void reset()
    {
        replaceRun();
    }

    /**
     * Selects the integration method and installs a fresh run for that method.
     *
     * @param method method to select
     * @throws IllegalStateException if the current run is running or paused
     */
    public void selectMethod(final IntegratorMethod method)
    {
        Objects.requireNonNull(method, "method must not be null");

        if (!canSelectMethod())
        {
            throw new IllegalStateException("Integrator cannot be changed while a comparison run is active");
        }

        selectedMethod = method;
        replaceRun();
    }

    /**
     * Returns the currently selected integration method.
     *
     * @return selected method
     */
    public IntegratorMethod selectedMethod()
    {
        return selectedMethod;
    }

    /**
     * Sets the presentation speed without changing the fixed numerical timestep.
     *
     * @param value simulated seconds requested per real second; positive and finite
     * @throws IllegalArgumentException if the value is not positive and finite
     */
    public void setSimulatedSecondsPerRealSecond(final double value)
    {
        if (!Double.isFinite(value) || value <= 0.0)
        {
            throw new IllegalArgumentException("simulated-seconds-per-real-second must be positive and finite, " + "was: " + value);
        }

        simulatedSecondsPerRealSecond = value;
    }

    /**
     * Returns the current presentation speed.
     *
     * @return simulated seconds requested per real second
     */
    public double simulatedSecondsPerRealSecond()
    {
        return simulatedSecondsPerRealSecond;
    }

    /**
     * Returns the latest committed simulation state for rendering.
     *
     * @return current simulation snapshot
     */
    public SimulationSnapshot snapshot()
    {
        return run.snapshot();
    }

    /**
     * Applies the legal start, pause or resume transition.
     * <p>
     * A completed run is terminal until reset or another method is selected.
     */
    public void toggleRun()
    {
        state = switch (state)
        {
            case READY -> IntegratorComparisonState.RUNNING;
            case RUNNING -> IntegratorComparisonState.PAUSED;
            case PAUSED -> IntegratorComparisonState.RUNNING;
            case COMPLETED -> IntegratorComparisonState.COMPLETED;
        };
    } // end of toggleRun()

    /**
     * Records the completed result and enters the completed state.
     */
    private void completeRun() {
        completedResults.put(selectedMethod, run.result());
        state = IntegratorComparisonState.COMPLETED;
        pendingSimulatedSeconds = 0.0;
    }

    /**
     * Installs a fresh baseline run for the selected method.
     */
    private void replaceRun()
    {
        run = experiment.createRun(selectedMethod);
        state = IntegratorComparisonState.READY;
        pendingSimulatedSeconds = 0.0;
    }
}