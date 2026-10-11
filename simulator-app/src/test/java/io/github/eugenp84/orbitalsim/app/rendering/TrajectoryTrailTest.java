package io.github.eugenp84.orbitalsim.app.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrajectoryTrail}. The trail carries no JavaFX dependency, so these run without a JavaFX toolkit.
 *
 * @author Eugeniu PLATITA
 */
class TrajectoryTrailTest
{
    private static final double MIN_SEGMENT = 10.0;

    @Test
    void aNewTrailHasNoPoints()
    {
        assertTrue(new TrajectoryTrail(100, MIN_SEGMENT).points().isEmpty());
    }

    @Test
    void theFirstRecordedPositionIsAlwaysKept()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        trail.record(new Vector3D(0.0, 0.0, 0.0));
        assertEquals(1, trail.points().size());
    }

    @Test
    void aPositionCloserThanTheMinimumSegmentIsNotRecorded()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        trail.record(new Vector3D(0.0, 0.0, 0.0));
        trail.record(new Vector3D(MIN_SEGMENT / 2.0, 0.0, 0.0));
        assertEquals(1, trail.points().size());
    }

    @Test
    void aPositionBeyondTheMinimumSegmentIsRecorded()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        trail.record(new Vector3D(0.0, 0.0, 0.0));
        trail.record(new Vector3D(2.0 * MIN_SEGMENT, 0.0, 0.0));
        assertEquals(2, trail.points().size());
    }

    @Test
    void theBufferIsBoundedAndDropsTheOldest()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(2, 1.0);
        trail.record(new Vector3D(0.0, 0.0, 0.0));
        trail.record(new Vector3D(10.0, 0.0, 0.0));
        trail.record(new Vector3D(20.0, 0.0, 0.0));

        final List<Vector3D> retained = new ArrayList<>(trail.points());
        assertEquals(2, retained.size());
        assertEquals(new Vector3D(10.0, 0.0, 0.0), retained.get(0));
        assertEquals(new Vector3D(20.0, 0.0, 0.0), retained.get(1));
    }

    @Test
    void clearRemovesEveryPoint()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        trail.record(new Vector3D(0.0, 0.0, 0.0));
        trail.record(new Vector3D(2.0 * MIN_SEGMENT, 0.0, 0.0));
        trail.clear();
        assertTrue(trail.points().isEmpty());
    }

    @Test
    void recordRejectsNull()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        assertThrows(NullPointerException.class, () -> trail.record(null));
    }

    @Test
    void constructionRejectsBadBounds()
    {
        assertThrows(IllegalArgumentException.class, () -> new TrajectoryTrail(0, MIN_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> new TrajectoryTrail(100, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new TrajectoryTrail(100, Double.NaN));
    }

    @Test
    void setMinimumSegmentMetresRejectsBadValuesAndClears()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        trail.record(new Vector3D(0.0, 0.0, 0.0));
        trail.record(new Vector3D(2.0 * MIN_SEGMENT, 0.0, 0.0));

        trail.setMinimumSegmentMetres(2.0 * MIN_SEGMENT);
        assertTrue(trail.points().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> trail.setMinimumSegmentMetres(0.0));
        assertThrows(IllegalArgumentException.class, () -> trail.setMinimumSegmentMetres(Double.NaN));
    }

    @Test
    void setMinimumSegmentMetresRejectsAnOverflowingSquare()
    {
        final TrajectoryTrail trail = new TrajectoryTrail(100, MIN_SEGMENT);
        assertThrows(IllegalArgumentException.class,
                () -> trail.setMinimumSegmentMetres(Double.MAX_VALUE));
    }
}