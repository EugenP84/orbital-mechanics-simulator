package io.github.eugenp84.orbitalsim.app.rendering;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Objects;

/**
 * A bounded, presentation-only history of a body's recent world positions, for drawing its path.
 * <p>
 * This is app-layer state with no bearing on the simulation: the physics,
 * the engine and the learning layer never see it.
 * Points are held in world metres, so the trail is defined physically and renders correctly whatever view transform is applied to it.
 * <p>
 * Two bounds keep it finite and legible. A point is recorded only once the body has moved at least a
 * minimum distance from the last recorded point, so the trail has a uniform spatial density
 * regardless of frame rate or simulation speed; and the number of retained points is capped,
 * the oldest dropped first, so a long run cannot grow the buffer without limit.
 * It is cleared whenever a new attempt begins, so one attempt's path never bleeds into the next.
 * <p>
 * The 2D world plane is tracked; any z-component is ignored, consistent with the 2D view.
 *
 * @author Eugeniu PLATITA
 */
public final class TrajectoryTrail
{
    private final int maximumPoints;
    private final double minimumSegmentSquaredMetres;
    private final Deque<Vector3D> points = new ArrayDeque<>();

    private Vector3D lastRecorded;

    /**
     * @param maximumPoints        the greatest number of points retained; positive
     * @param minimumSegmentMetres the least distance the body must move before another point is recorded; positive and finite
     * @throws IllegalArgumentException if either bound is out of range
     */
    public TrajectoryTrail(final int maximumPoints, final double minimumSegmentMetres)
    {
        if (maximumPoints <= 0)
        {
            throw new IllegalArgumentException("maximumPoints must be positive, was: " + maximumPoints);
        }
        if (!Double.isFinite(minimumSegmentMetres) || minimumSegmentMetres <= 0.0)
        {
            throw new IllegalArgumentException(
                    "minimumSegmentMetres must be positive and finite, was: " + minimumSegmentMetres);
        }
        this.maximumPoints = maximumPoints;
        this.minimumSegmentSquaredMetres = minimumSegmentMetres * minimumSegmentMetres;
    }

    /** Removes every recorded point, so the next recording starts a fresh path. */
    public void clear()
    {
        points.clear();
        lastRecorded = null;
    }

    /** The recorded points, oldest to newest, for rendering. */
    public Collection<Vector3D> points()
    {
        return Collections.unmodifiableCollection(points);
    }

    /**
     * Records the body's current world position if it is the first point, or if the body has moved at least the minimum segment distance from the last recorded point.
     * Dropping the oldest point when full keeps the buffer bounded.
     *
     * @param position the body's current world position in metres; never null
     */
    public void record(final Vector3D position)
    {
        Objects.requireNonNull(position, "position must not be null");
        if (lastRecorded != null
                && squaredPlanarDistance(position, lastRecorded) < minimumSegmentSquaredMetres)
        {
            return;
        }
        points.addLast(position);
        lastRecorded = position;
        if (points.size() > maximumPoints)
        {
            points.removeFirst();
        }
    }

    private static double squaredPlanarDistance(final Vector3D a, final Vector3D b)
    {
        final double dx = a.x() - b.x();
        final double dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }
}