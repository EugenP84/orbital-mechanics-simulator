package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.SimulationController;
import io.github.eugenp84.orbitalsim.app.rendering.SimulationRenderer;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.util.Locale;
import java.util.Optional;

/**
 * JavaFX entry point for the orbital mechanics simulator.
 * <p>
 * This increment adds the presentation controls: a run/pause toggle, a reset,
 * and a speed slider. Scenario logic is delegated to a {@link SimulationController};
 * this class only builds the scene, runs the frame loop,
 * and reflects the controller's lifecycle state in the controls.
 * <p>
 * Advancement and rendering run on the JavaFX Application Thread.
 * For the small Level 1 two-body system this deliberately avoids synchronisation; threading
 * would be reconsidered only if measurement showed simulation work disturbing presentation.
 * The frame loop runs continuously and advances the scenario only while it is running,
 * so pause simply stops advancing and reset — which swaps in a new attempt — resumes cleanly even after the previous attempt terminated.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application {

    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator — Level 1";
    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_MARGIN_FACTOR = 1.15;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    // A stalled frame, debugger or GC pause must not become one huge simulated-time request.
    // Excess wall-clock time is discarded, not replayed.
    // This is a presentation safeguard, not a physics rule.
    private static final double MAX_FRAME_DELTA_SECONDS = 1.0 / 30.0;
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double MIN_SPEED_DAYS_PER_SECOND = 1.0;
    private static final double MAX_SPEED_DAYS_PER_SECOND = 60.0;
    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_BAR_PADDING_PIXELS = 10.0;
    private static final double SPEED_SLIDER_WIDTH_PIXELS = 200.0;
    private AnimationTimer animationTimer;
    private SimulationController controller;
    private Button runPauseButton;
    private Label stateLabel;

    /**
     * Standard Java entry point.
     *
     * @param args command-line arguments, forwarded unchanged to JavaFX
     */
    public static void main(final String[] args) {
        launch(args);
    }

    /**
     * Builds the Level 1 scene, renders its initial state, and starts the frame loop.
     * The scenario is not started here: the user starts it with the control.
     *
     * @param stage the primary stage supplied by JavaFX; never null
     */
    @Override
    public void start(final Stage stage)
    {
        final Level1Scenario scenario = new Level1Scenario();
        controller = new SimulationController(scenario);

        final ViewTransform transform = ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                0.0,
                0.0,
                scenario.orbitalRadiusMetres() * VIEW_MARGIN_FACTOR);

        final Canvas canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);
        final GraphicsContext graphics = canvas.getGraphicsContext2D();
        final SimulationRenderer renderer = new SimulationRenderer();

        final BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setBottom(buildControlBar());

        // Show the committed initial state before any simulated time passes.
        renderer.render(graphics, controller.snapshot(), transform);
        updateControlStates();

        stage.setScene(new Scene(root));
        stage.setTitle(WINDOW_TITLE);
        stage.show();

        animationTimer = createAnimationTimer(renderer, graphics, transform);
        animationTimer.start();
    }

    /**
     * Stops the frame loop when the JavaFX application shuts down.
     */
    @Override
    public void stop()
    {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    private HBox buildControlBar()
    {
        runPauseButton = new Button();
        runPauseButton.setOnAction(event -> {
            controller.toggleRun();
            updateControlStates();
        });

        final Button resetButton = new Button("Reset");
        resetButton.setOnAction(event -> {
            controller.reset();
            updateControlStates();
        });

        final double initialDaysPerSecond = controller.simulatedSecondsPerRealSecond() / SECONDS_PER_DAY;
        final Label speedValueLabel = new Label(formatSpeed(initialDaysPerSecond));
        final Slider speedSlider = new Slider(MIN_SPEED_DAYS_PER_SECOND, MAX_SPEED_DAYS_PER_SECOND, initialDaysPerSecond);
        speedSlider.setPrefWidth(SPEED_SLIDER_WIDTH_PIXELS);
        speedSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            final double daysPerSecond = newValue.doubleValue();
            controller.setSimulatedSecondsPerRealSecond(daysPerSecond * SECONDS_PER_DAY);
            speedValueLabel.setText(formatSpeed(daysPerSecond));
        });

        stateLabel = new Label();

        final HBox controlBar = new HBox(
                CONTROL_BAR_SPACING_PIXELS,
                runPauseButton,
                resetButton,
                new Label("Speed:"),
                speedSlider,
                speedValueLabel,
                stateLabel);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(CONTROL_BAR_PADDING_PIXELS));
        return controlBar;
    }

    private AnimationTimer createAnimationTimer(
            final SimulationRenderer renderer,
            final GraphicsContext graphics,
            final ViewTransform transform)
    {

        return new AnimationTimer()
        {
            private long previousFrameNanos;

            @Override
            public void handle(final long now) {
                // The first timestamp establishes the wall-clock baseline; there is no
                // meaningful interval to advance yet.
                if (previousFrameNanos == 0L) {
                    previousFrameNanos = now;
                    return;
                }

                final long elapsedNanos = now - previousFrameNanos;
                previousFrameNanos = now;
                if (elapsedNanos <= 0L) {
                    return;
                }

                final double elapsedWallClockSeconds = elapsedNanos / NANOS_PER_SECOND;
                final double boundedWallClockSeconds = Math.min(elapsedWallClockSeconds, MAX_FRAME_DELTA_SECONDS);

                controller.advanceByWallClock(boundedWallClockSeconds);

                // Render after advancement so the frame shows the most recently committed state, including the final frame of a scenario that terminated this tick.
                renderer.render(graphics, controller.snapshot(), transform);
                updateControlStates();
            }
        };
    }

    private void updateControlStates() {
        final ScenarioLifecycleState current = controller.state();
        runPauseButton.setText(runPauseLabelFor(current));
        runPauseButton.setDisable(!controller.canToggleRun());
        stateLabel.setText(statusTextFor(current, controller.terminationReason()));
    }

    private static String statusTextFor(
            final ScenarioLifecycleState state,
            final Optional<ScenarioTerminationReason> reason) {

        if (reason.isEmpty()) {
            return "Status: " + statusWordFor(state);
        }
        return "Status: " + statusWordFor(state) + " — " + learnerTextFor(reason.get());
    }

    private static String statusWordFor(final ScenarioLifecycleState state) {
        return switch (state) {
            case NOT_STARTED -> "Ready";
            case RUNNING -> "Running";
            case PAUSED -> "Paused";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
        };
    }

    private static String learnerTextFor(final ScenarioTerminationReason reason) {
        return switch (reason) {
            case POLICY_COMPLETION -> "stable orbit achieved";
            case POLICY_FAILURE -> "the orbit did not stay stable";
            case ASSESSMENT_INVALID -> "the orbit could not be assessed reliably";
            case SIMULATION_BREAKDOWN -> "the simulation became numerically unstable";
        };
    }

    private static String formatSpeed(final double daysPerSecond)
    {
        return String.format(Locale.ROOT, "%.0f days/s", daysPerSecond);
    }

    private static String runPauseLabelFor(final ScenarioLifecycleState state)
    {
        return switch (state)
        {
            case NOT_STARTED, COMPLETED, FAILED -> "Start";
            case RUNNING -> "Pause";
            case PAUSED -> "Resume";
        };
    }
}