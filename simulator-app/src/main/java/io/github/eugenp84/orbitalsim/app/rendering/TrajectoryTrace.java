package io.github.eugenp84.orbitalsim.app.rendering;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import javafx.scene.paint.Color;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Presentation-only definition of one rendered trajectory:
 * its stored world-space trail, the snapshot function selecting the tracked body's position, and the colour used to draw the path.
 *
 * @param trail            the trajectory history to record and draw; never null
 * @param positionSelector selects the tracked body's position from a snapshot, or empty; never null
 * @param colour           the colour used to draw the trajectory; never null
 *
 * @author Eugeniu PLATITA
 */
public record TrajectoryTrace(
        TrajectoryTrail trail,
        Function<SimulationSnapshot, Optional<Vector3D>> positionSelector,
        Color colour)
{
    public TrajectoryTrace
    {
        Objects.requireNonNull(trail, "trail must not be null");
        Objects.requireNonNull(positionSelector, "positionSelector must not be null");
        Objects.requireNonNull(colour, "colour must not be null");
    }
}