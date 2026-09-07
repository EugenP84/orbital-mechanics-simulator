package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.rendering.SimulationRenderer;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Attempt;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioSession;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the orbital mechanics simulator.
 * <p>
 * This increment assembles a real Level 1 attempt and drives it from the JavaFX presentation loop.
 * Wall-clock time is converted into requested simulated time, which is passed only to {@link ScenarioSession};
 * the session remains the sole advancement boundary and therefore preserves its learning cadence and policy evaluation semantics.
 * <p>
 * Rendering and simulation advancement currently occur on the JavaFX Application Thread.
 * For the small Level 1 two-body system this deliberately avoids unnecessary synchronisation.
 * Threading should be reconsidered only if later measurement shows that simulation work interferes with presentation.
 * <p>
 * The presentation speed used here is provisional. It changes how quickly simulated time is requested relative to wall-clock time.
 * It does not alter the fixed numerical integration timestep.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application
{
    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator — Level 1";
    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_MARGIN_FACTOR = 1.15;
    private static final double SECONDS_PER_DAY = 86_400.0;

    /**
     * Temporary presentation rate for the animated Level 1 prototype.
     * <p>
     * Fourteen simulated days per real second gives approximately 56 six-hour numerical steps per real second, which is sufficient for visibly smooth motion without changing the engine's configured timestep.
     * A user-facing and scenario-calibrated speed control belongs to the controls increment.
     */
    private static final double SIMULATED_SECONDS_PER_REAL_SECOND = 14.0 * SECONDS_PER_DAY;

    /**
     * Maximum wall-clock interval consumed from one JavaFX frame.
     * <p>
     * A stalled frame, debugger pause or garbage-collection pause must not be converted into one very large simulated-time request.
     * Excess wall-clock time is deliberately discarded rather than replayed on later frames.
     */
    private static final double MAX_FRAME_DELTA_SECONDS = 1.0 / 30.0;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private AnimationTimer animationTimer;

    /**
     * Standard Java entry point.
     *
     * @param args command-line arguments, forwarded unchanged to JavaFX
     */
    public static void main(final String[] args)
    {
        launch(args);
    }

    /**
     * Builds the Level 1 scene, renders its initial state and starts animation.
     *
     * @param stage the primary stage supplied by JavaFX
     */
    @Override
    public void start(final Stage stage)
    {
        final Level1Scenario scenario = new Level1Scenario();
        final Level1Attempt attempt = scenario.createAttempt(scenario.circularSpeedMetresPerSecond());
        final ScenarioSession session = attempt.session();
        final ViewTransform transform = ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                0.0,
                0.0,
                attempt.orbitalRadiusMetres() * VIEW_MARGIN_FACTOR);

        final Canvas canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);
        final GraphicsContext graphics = canvas.getGraphicsContext2D();
        final SimulationRenderer renderer = new SimulationRenderer();

        // Show the committed initial state before any simulated time passes.
        renderer.render(graphics, session.snapshot(), transform);

        final StackPane root = new StackPane(canvas);

        stage.setScene(
                new Scene(
                        root,
                        CANVAS_WIDTH_PIXELS,
                        CANVAS_HEIGHT_PIXELS));

        stage.setTitle(WINDOW_TITLE);
        stage.show();

        session.start();

        animationTimer = createAnimationTimer(
                session,
                renderer,
                graphics,
                transform);

        animationTimer.start();
    } // end of start() ------------------------------

    /**
     * Creates the JavaFX frame loop that converts elapsed wall-clock time into simulated time, advances the scenario and paints its latest committed snapshot.
     */
    private AnimationTimer createAnimationTimer(
            final ScenarioSession session,
            final SimulationRenderer renderer,
            final GraphicsContext graphics,
            final ViewTransform transform)
    {
        return new AnimationTimer()
        {
            private long previousFrameNanos;

            @Override
            public void handle(final long now)
            {
                // The first timestamp establishes the wall-clock baseline.
                // There is no meaningful elapsed interval to advance yet.
                if (previousFrameNanos == 0L)
                {
                    previousFrameNanos = now;
                    return;
                }

                final long elapsedNanos = now - previousFrameNanos;
                previousFrameNanos = now;

                if (elapsedNanos <= 0L) {
                    return;
                }

                final double elapsedWallClockSeconds =
                        elapsedNanos / NANOS_PER_SECOND;

                final double boundedWallClockSeconds = Math.min(elapsedWallClockSeconds, MAX_FRAME_DELTA_SECONDS);
                final double requestedSimulatedSeconds = boundedWallClockSeconds * SIMULATED_SECONDS_PER_REAL_SECOND;

                if (session.state() == ScenarioLifecycleState.RUNNING)
                {
                    session.advanceBy(requestedSimulatedSeconds);
                }

                // Render after advancement so the frame always represents the most recently committed simulation state.
                renderer.render(
                        graphics,
                        session.snapshot(),
                        transform);

                // A policy decision or numerical breakdown may have terminated the scenario during advanceBy().
                // The terminal state is drawn once above before presentation stops.
                if (session.state() == ScenarioLifecycleState.COMPLETED || session.state() == ScenarioLifecycleState.FAILED)
                {
                    stop();
                }
            }
        };
    } // ------- end of createAnimationTimer() --------------=================================

    /**
     * Stops presentation when the JavaFX application is shutting down.
     */
    @Override
    public void stop()
    {
        if (animationTimer != null)
        {
            animationTimer.stop();
        }
    }
}