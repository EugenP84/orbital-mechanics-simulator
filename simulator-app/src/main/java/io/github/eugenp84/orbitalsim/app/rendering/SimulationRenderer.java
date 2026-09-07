package io.github.eugenp84.orbitalsim.app.rendering;

import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Objects;

/**
 * Paints a committed {@link SimulationSnapshot} onto a JavaFX canvas.
 * <p>
 * The renderer is the only component aware of both the simulation's world coordinates and JavaFX drawing.
 * It delegates every metre-to-pixel conversion to a {@link ViewTransform} and confines all JavaFX drawing calls here,
 * so the learning and core layers remain free of any presentation dependency.
 * <p>
 * Bodies are drawn as fixed pixel radii keyed to their type, not to their physical radius.
 * At an orbital-scale view a physically-to-scale Earth would be far smaller than one pixel,
 * so the drawn discs are display symbols marking each body's position, not scale models of the bodies themselves.
 * <p>
 * The world XY plane is drawn; any z-component of a position is not projected,
 * according to the 2D view described in the project design.
 * The renderer holds no state between calls: each invocation paints one snapshot in full.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationRenderer
{

    // Display radii in pixels. These are presentation symbols, not physical radii scaled to the view.
    private static final double STAR_DISPLAY_RADIUS_PIXELS = 14.0;
    private static final double PLANET_DISPLAY_RADIUS_PIXELS = 6.0;
    private static final double ASTEROID_DISPLAY_RADIUS_PIXELS = 4.0;

    private static final double LABEL_GAP_PIXELS = 6.0; // Horizontal gap in pixels between a body's disc and its text label.

    private static final Color BACKGROUND_COLOUR = Color.web("#05070f");
    private static final Color STAR_COLOUR = Color.web("#ffd54a");
    private static final Color PLANET_COLOUR = Color.web("#4aa3ff");
    private static final Color ASTEROID_COLOUR = Color.web("#b0b6c0");
    private static final Color LABEL_COLOUR = Color.web("#c7ccd6");

    /**
     * Paints the whole snapshot: clears the viewport to the background colour, then draws every body in the snapshot's body order.
     *
     * @param graphics  the canvas graphics context to draw on; never null
     * @param snapshot  the committed state to render; never null
     * @param transform the metre-to-pixel mapping for the current view; never null
     */
    public void render(
            final GraphicsContext graphics,
            final SimulationSnapshot snapshot,
            final ViewTransform transform)
    {
        Objects.requireNonNull(graphics, "graphics must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(transform, "transform must not be null");

        drawBackground(graphics, transform);

        for (final BodySnapshot body : snapshot.bodies())
        {
            drawBody(graphics, body, transform);
        }
    } // end of render() ---------------------------------------------

    private static void drawBackground(final GraphicsContext graphics, final ViewTransform transform)
    {
        graphics.setFill(BACKGROUND_COLOUR);
        graphics.fillRect(
                0.0,
                0.0,
                transform.canvasWidth(),
                transform.canvasHeight());
    } // ------ end of drawBackground() -------------------------------------------

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
        graphics.fillOval(
                screenX - radius,
                screenY - radius,
                2.0 * radius,
                2.0 * radius);

        graphics.setFill(LABEL_COLOUR);
        graphics.fillText(
                body.definition().name(),
                screenX + radius + LABEL_GAP_PIXELS,
                screenY);
    }

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
}