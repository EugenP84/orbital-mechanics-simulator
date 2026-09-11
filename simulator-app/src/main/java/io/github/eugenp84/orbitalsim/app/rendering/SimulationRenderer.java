package io.github.eugenp84.orbitalsim.app.rendering;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Collection;
import java.util.Objects;

/**
 * Paints a committed {@link SimulationSnapshot}, and an optional trajectory trail, onto a JavaFX
 * canvas.
 * <p>
 * The renderer is the only component aware of both the simulation's world coordinates and JavaFX
 * drawing. It delegates every metre-to-pixel conversion to a {@link ViewTransform} and confines all JavaFX drawing calls here,
 * so the learning and core layers stay free of any presentation dependency.
 * It holds no state between calls: the trail is owned elsewhere and supplied per frame.
 * <p>
 * Bodies are drawn as fixed pixel radii keyed to their type, not their physical radius, since a
 * physically-to-scale planet would be far smaller than one pixel;
 * the discs are display symbols marking position. The trail is drawn under the bodies so the current positions stay legible.
 * <p>
 * The world XY plane is drawn; any z-component is not projected, consistent with the 2D view.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationRenderer
{
    private static final double STAR_DISPLAY_RADIUS_PIXELS = 14.0;
    private static final double PLANET_DISPLAY_RADIUS_PIXELS = 6.0;
    private static final double ASTEROID_DISPLAY_RADIUS_PIXELS = 4.0;

    private static final double LABEL_GAP_PIXELS = 6.0;
    private static final double TRAIL_LINE_WIDTH_PIXELS = 1.5;

    private static final Color BACKGROUND_COLOUR = Color.web("#05070f");
    private static final Color STAR_COLOUR = Color.web("#ffd54a");
    private static final Color PLANET_COLOUR = Color.web("#4aa3ff");
    private static final Color ASTEROID_COLOUR = Color.web("#b0b6c0");
    private static final Color LABEL_COLOUR = Color.web("#c7ccd6");
    private static final Color TRAIL_COLOUR = Color.web("#4aa3ff", 0.55);

    private static Color colourFor(final CelestialBodyType type)
    {
        return switch (type)
        {
            case STAR -> STAR_COLOUR;
            case PLANET -> PLANET_COLOUR;
            case ASTEROID -> ASTEROID_COLOUR;
        };
    }

    private static double displayRadiusPixels(final CelestialBodyType type)
    {
        return switch (type)
        {
            case STAR -> STAR_DISPLAY_RADIUS_PIXELS;
            case PLANET -> PLANET_DISPLAY_RADIUS_PIXELS;
            case ASTEROID -> ASTEROID_DISPLAY_RADIUS_PIXELS;
        };
    }

    private static void drawBackground(final GraphicsContext graphics, final ViewTransform transform)
    {
        graphics.setFill(BACKGROUND_COLOUR);
        graphics.fillRect(0.0, 0.0, transform.canvasWidth(), transform.canvasHeight());
    }

    private static void drawBody(
            final GraphicsContext graphics,
            final BodySnapshot body,
            final ViewTransform transform)
    {
        final Vector3D position = body.state().position();
        final double screenX = transform.toScreenX(position.x());
        final double screenY = transform.toScreenY(position.y());
        final double radius = displayRadiusPixels(body.definition().type());

        graphics.setFill(colourFor(body.definition().type()));
        graphics.fillOval(screenX - radius, screenY - radius, 2.0 * radius, 2.0 * radius);

        graphics.setFill(LABEL_COLOUR);
        graphics.fillText(body.definition().name(), screenX + radius + LABEL_GAP_PIXELS, screenY);
    }

    private static void drawTrail(
            final GraphicsContext graphics,
            final Collection<Vector3D> trailPoints,
            final ViewTransform transform)
    {
        if (trailPoints.isEmpty())
        {
            return;
        }
        graphics.setStroke(TRAIL_COLOUR);
        graphics.setLineWidth(TRAIL_LINE_WIDTH_PIXELS);

        boolean havePrevious = false;
        double previousX = 0.0;
        double previousY = 0.0;
        for (final Vector3D point : trailPoints)
        {
            final double screenX = transform.toScreenX(point.x());
            final double screenY = transform.toScreenY(point.y());
            if (havePrevious)
            {
                graphics.strokeLine(previousX, previousY, screenX, screenY);
            }
            previousX = screenX;
            previousY = screenY;
            havePrevious = true;
        }
    }

    /**
     * Paints the whole frame: clears the viewport, draws the trail under the bodies, then draws each body in the snapshot's order.
     *
     * @param graphics    the canvas graphics context to draw on; never null
     * @param snapshot    the committed state to render; never null
     * @param transform   the metre-to-pixel mapping for the current view; never null
     * @param trailPoints the trail's world points, oldest to newest; never null, may be empty
     */
    public void render(
            final GraphicsContext graphics,
            final SimulationSnapshot snapshot,
            final ViewTransform transform,
            final Collection<Vector3D> trailPoints)
    {
        Objects.requireNonNull(graphics, "graphics must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(transform, "transform must not be null");
        Objects.requireNonNull(trailPoints, "trailPoints must not be null");

        drawBackground(graphics, transform);
        drawTrail(graphics, trailPoints, transform);
        for (final BodySnapshot body : snapshot.bodies())
        {
            drawBody(graphics, body, transform);
        }
    }
}