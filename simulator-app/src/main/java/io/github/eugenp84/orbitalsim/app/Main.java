package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.rendering.SimulationRenderer;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the orbital mechanics simulator.
 * <p>
 * This first increment is a deliberately minimal vertical slice:
 * the application launches, assembles a real {@link Level1Attempt} through the
 * learning layer, and statically renders that attempt's initial Sun-Earth configuration.
 * No animation is started yet — motion is a separate increment —
 * so this class proves the integration boundaries between the JavaFX layer, the learning layer and the simulation core.
 * <p>
 * All JavaFX concerns are confined to the {@code simulator-app} module.
 * The learning and core layers stay free of any presentation dependency:
 * this class consumes their immutable snapshots and never reaches into their runtime state.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application
{
    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator — Level 1";

    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;

    // The initial view frames a circle slightly larger than the orbital radius
    // so the whole orbit is visible with a margin at the viewport edge.
    private static final double VIEW_MARGIN_FACTOR = 1.15;

    /**
     * Standard Java entry point. Delegates to the JavaFX toolkit,
     * which constructs the application and calls {@link #start(Stage)} on the JavaFX Application Thread.
     *
     * @param args command-line arguments, forwarded unchanged to JavaFX
     */
    public static void main(final String[] args)
    {
        launch(args);
    }

    /**
     * Builds the scene and renders the initial Level 1 state once.
     * <p>
     * The attempt is created at the analytic circular speed so the initial configuration is the scenario's canonical target orbit.
     * Only the initial snapshot is drawn; the session is not started here.
     *
     * @param stage the primary stage supplied by JavaFX; never null
     */
    @Override
    public void start(final Stage stage)
    {
        final Level1Scenario scenario = new Level1Scenario();
        final Level1Attempt attempt = scenario.createAttempt(scenario.circularSpeedMetresPerSecond());
        final SimulationSnapshot initialState = attempt.session().snapshot();

        final ViewTransform transform = ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                0.0,
                0.0,
                attempt.orbitalRadiusMetres() * VIEW_MARGIN_FACTOR);

        final Canvas canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);

        final GraphicsContext graphics = canvas.getGraphicsContext2D();

        final SimulationRenderer renderer = new SimulationRenderer();

        renderer.render(graphics, initialState, transform);

        final StackPane root = new StackPane(canvas);

        stage.setScene(
                new Scene(
                        root,
                        CANVAS_WIDTH_PIXELS,
                        CANVAS_HEIGHT_PIXELS));

        stage.setTitle(WINDOW_TITLE);
        stage.show();
    }
}