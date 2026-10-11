package io.github.eugenp84.orbitalsim.evaluation.integrator;

/**
 * Quantitative result of one completed controlled numerical-integrator run.
 *
 * @param method                       integration method used
 * @param completedSteps               successfully committed integration steps
 * @param simulationTimeSeconds        elapsed simulated time in seconds
 * @param maxRelativeEnergyDeviation   greatest |E(t) - E(0)| / |E(0)|
 * @param maxTrajectoryErrorMetres     greatest relative-position error against the analytical circular orbit, in metres
 * @param accelerationEvaluations      acceleration-model invocations
 *
 * @author Eugeniu PLATITA
 */
public record IntegratorComparisonResult(
        IntegratorMethod method,
        long completedSteps,
        double simulationTimeSeconds,
        double maxRelativeEnergyDeviation,
        double maxTrajectoryErrorMetres,
        long accelerationEvaluations)
{
    /** Validates a completed comparison result. */
    public IntegratorComparisonResult
    {
        if (method == null) {
            throw new NullPointerException("method must not be null");
        }
        if (completedSteps < 0L)
        {
            throw new IllegalArgumentException("completedSteps must be non-negative, was: " + completedSteps);
        }
        if (!Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0.0)
        {
            throw new IllegalArgumentException("simulationTimeSeconds must be finite and non-negative, was: " + simulationTimeSeconds);
        }
        if (!Double.isFinite(maxRelativeEnergyDeviation) || maxRelativeEnergyDeviation < 0.0)
        {
            throw new IllegalArgumentException("maxRelativeEnergyDeviation must be finite and non-negative, was: " + maxRelativeEnergyDeviation);
        }
        if (!Double.isFinite(maxTrajectoryErrorMetres) || maxTrajectoryErrorMetres < 0.0)
        {
            throw new IllegalArgumentException("maxTrajectoryErrorMetres must be finite and non-negative, was: " + maxTrajectoryErrorMetres);
        }
        if (accelerationEvaluations < 0L) {
            throw new IllegalArgumentException("accelerationEvaluations must be non-negative, was: " + accelerationEvaluations);
        }
    }
}