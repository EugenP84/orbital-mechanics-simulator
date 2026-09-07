package io.github.eugenp84.orbitalsim.app.rendering;

/**
 * Maps the simulation's world coordinates onto canvas pixels for rendering.
 * <p>
 * The physics runs in metres in a frame whose y-axis points up (ADR-002, ADR-003);
 * a JavaFX {@code Canvas} addresses pixels from the top-left with the y-axis pointing down.
 * This transform is the single place that reconciles the two:
 * it applies a uniform scale, recentres the world on the viewport, and flips the y-axis.
 * Confining every unit conversion here lets the renderer work only in pixels and the engine only in metres, so neither depends on the
 * other's coordinate system.
 * <p>
 * The mapping is linear and uniform — one scale for both axes — which is correct while the view shows a single orbit at one characteristic scale, such as the
 * Sun-Earth prototype. The non-linear distance compression the NEO module will require, where bodies separated by very different distances must all stay
 * visible, is a deliberately separate concern and is not introduced here.
 * <p>
 * Instances are immutable value objects: a pan or zoom yields a new transform rather than mutating an existing one,
 * so a renderer may hold a reference for the span of a frame without it changing underneath it.
 *
 * @param canvasWidth    viewport width in pixels; positive and finite
 * @param canvasHeight   viewport height in pixels; positive and finite
 * @param worldCentreX   world x shown at the viewport centre, in metres; finite
 * @param worldCentreY   world y shown at the viewport centre, in metres; finite
 * @param pixelsPerMetre uniform scale factor; positive and finite
 *
 * @author Eugeniu PLATITA
 */
public record ViewTransform(
        double canvasWidth,
        double canvasHeight,
        double worldCentreX,
        double worldCentreY,
        double pixelsPerMetre)
{

    // Fraction of the shorter viewport half-extent that the requested world radius fills, leaving a margin so bodies near the edge are not clipped.
    private static final double VIEWPORT_FILL_FRACTION = 0.9;

    public ViewTransform
    {
        requirePositiveFinite(canvasWidth, "canvasWidth");
        requirePositiveFinite(canvasHeight, "canvasHeight");
        requirePositiveFinite(pixelsPerMetre, "pixelsPerMetre");
        requireFinite(worldCentreX, "worldCentreX");
        requireFinite(worldCentreY, "worldCentreY");
    }

    /**
     * Builds a transform that centres the world point {@code (worldCentreX, worldCentreY)} in the viewport and scales so that a circle of radius
     * {@code worldRadiusToShow} about that point fits within the shorter viewport dimension, less a fixed margin.
     * <p>
     * The shorter dimension is used so the requested radius is fully visible on both axes whatever the window's aspect ratio.
     *
     * @param canvasWidth       viewport width in pixels; positive and finite
     * @param canvasHeight      viewport height in pixels; positive and finite
     * @param worldCentreX      world x shown at the centre, in metres; finite
     * @param worldCentreY      world y shown at the centre, in metres; finite
     * @param worldRadiusToShow world radius to make visible, in metres; positive and finite
     * @return a transform framing the requested region
     * @throws IllegalArgumentException if any argument is out of range
     */
    public static ViewTransform fitting(
            final double canvasWidth,
            final double canvasHeight,
            final double worldCentreX,
            final double worldCentreY,
            final double worldRadiusToShow) {

        // Validated here, before the division, so an invalid extent yields a clear message rather than a derived non-finite scale rejected by the constructor.
        requirePositiveFinite(canvasWidth, "canvasWidth");
        requirePositiveFinite(canvasHeight, "canvasHeight");
        requirePositiveFinite(worldRadiusToShow, "worldRadiusToShow");

        final double halfMinExtent = Math.min(canvasWidth, canvasHeight) / 2.0;
        final double pixelsPerMetre = VIEWPORT_FILL_FRACTION * halfMinExtent / worldRadiusToShow;
        return new ViewTransform(
                canvasWidth, canvasHeight, worldCentreX, worldCentreY, pixelsPerMetre);
    }

    /**
     * Converts a length in metres to pixels under this scale.
     * Applies to any quantity measured in world units — a true-to-scale body radius,
     * say — as distinct from a position, which also needs recentring and the y-flip.
     *
     * @param worldLength a length in metres
     * @return the corresponding length in pixels
     */
    public double scaleLength(final double worldLength) {
        return worldLength * pixelsPerMetre;
    }

    /**
     * Maps a world x-coordinate in metres to a canvas x-coordinate in pixels.
     *
     * @param worldX world x in metres
     * @return canvas x in pixels
     */
    public double toScreenX(final double worldX) {
        return canvasWidth / 2.0 + (worldX - worldCentreX) * pixelsPerMetre;
    }

    /**
     * Maps a world y-coordinate in metres to a canvas y-coordinate in pixels.
     * The sign is inverted because world y points up while canvas y points down,
     * so a body above the centre in the world is drawn above the centre on screen.
     *
     * @param worldY world y in metres
     * @return canvas y in pixels
     */
    public double toScreenY(final double worldY)
    {
        return canvasHeight / 2.0 - (worldY - worldCentreY) * pixelsPerMetre;
    }

    private static void requireFinite(final double value, final String name)
    {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, was: " + value);
        }
    }

    private static void requirePositiveFinite(final double value, final String name)
    {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite, was: " + value);
        }
    }
}