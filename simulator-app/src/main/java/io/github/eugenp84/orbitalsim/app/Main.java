package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.AttemptCreationResult;
import io.github.eugenp84.orbitalsim.app.controller.SimulationController;
import io.github.eugenp84.orbitalsim.app.rendering.SimulationRenderer;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;

/**
 * JavaFX entry point for the orbital mechanics simulator.
 * <p>
 * This increment adds the orbital-speed control — the principal Level 1 learner input — on a two-row
 * control panel above the canvas: the attempt row (orbital speed, Start/Pause, Reset) and the
 * playback/feedback row (time rate, status, integrator, elapsed).
 * <p>
 * The orbital-speed slider owns a single contract: whenever it is enabled, its value is the speed of
 * the currently configured, not-yet-started attempt. It commits a new attempt when a value settles
 * (drag end, track click or keyboard), not on every intermediate drag event, and only when the
 * settled fraction actually differs from the installed one. The learner-facing resolution is fixed
 * at {@code SPEED_FRACTION_STEP}; quantisation to that step is authoritative and independent of the
 * JavaFX slider's own snapping, so thumb, label and controller always represent the same value.
 * Start is therefore a pure lifecycle action — it starts the attempt the slider already represents.
 * <p>
 * Advancement and rendering run on the JavaFX Application Thread; the frame loop runs continuously
 * and advances only while running.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application
{
    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator — Level 1";
    private static final String INTEGRATOR_LABEL = "Integrator: Velocity Verlet (fixed)";

    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_MARGIN_FACTOR = 1.15;

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    // A stalled frame, debugger or GC pause must not become one huge simulated-time request. Excess
    // wall-clock time is discarded, not replayed. A presentation safeguard, not a physics rule.
    private static final double MAX_FRAME_DELTA_SECONDS = 1.0 / 30.0;

    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double MIN_TIME_RATE_DAYS_PER_SECOND = 1.0;
    private static final double MAX_TIME_RATE_DAYS_PER_SECOND = 60.0;

    // The orbital-speed slider spans 0.5-1.5 x circular: it crosses the escape threshold at
    // sqrt(2) ~ 1.414, so one control exposes bound ellipses, the circular target and unbound escape.
    private static final double MIN_SPEED_FRACTION = 0.5;
    private static final double MAX_SPEED_FRACTION = 1.5;
    private static final double DEFAULT_SPEED_FRACTION = 1.0;

    // The learner-facing resolution of the orbital-speed control. Quantising to this step is the
    // authoritative configuration rule; the JavaFX slider's own snapping only assists the feel.
    private static final double SPEED_FRACTION_STEP = 0.01;

    // Smaller than one quantisation step, so it absorbs floating-point noise between values that
    // should be equal without ever hiding a real change of selection.
    private static final double SPEED_FRACTION_EPSILON = 1.0e-6;
    private static final double METRES_PER_SECOND_IN_KILOMETRES = 1_000.0;

    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_ROW_SPACING_PIXELS = 6.0;
    private static final double CONTROL_PANEL_PADDING_PIXELS = 10.0;
    private static final double TIME_RATE_SLIDER_WIDTH_PIXELS = 160.0;
    private static final double ORBITAL_SPEED_SLIDER_WIDTH_PIXELS = 280.0;

    // ~1500 points at roughly orbital-radius/240 spacing shows about one full circular orbit of
    // path; distance sampling keeps that consistent across time rates.
    private static final int TRAIL_MAXIMUM_POINTS = 1_500;
    private static final double TRAIL_SEGMENT_DIVISOR = 240.0;

    private AnimationTimer animationTimer;
    private SimulationController controller;
    private Button runPauseButton;
    private Slider orbitalSpeedSlider;
    private Label orbitalSpeedValueLabel;
    private Label stateLabel;
    private Label elapsedLabel;

    private TrajectoryTrail trail;

    private VBox buildControlPanel()
    {
        runPauseButton = new Button();
        runPauseButton.setOnAction(event ->
        {
            controller.toggleRun();
            updateControlStates();
        });

        final Button resetButton = new Button("Reset");
        resetButton.setOnAction(event ->
        {
            controller.reset();
            trail.clear();
            updateControlStates();
        });

        orbitalSpeedValueLabel = new Label();
        orbitalSpeedSlider = new Slider(MIN_SPEED_FRACTION, MAX_SPEED_FRACTION, DEFAULT_SPEED_FRACTION);
        orbitalSpeedSlider.setPrefWidth(ORBITAL_SPEED_SLIDER_WIDTH_PIXELS);
        orbitalSpeedSlider.setBlockIncrement(SPEED_FRACTION_STEP);
        orbitalSpeedSlider.setMajorTickUnit(SPEED_FRACTION_STEP);
        orbitalSpeedSlider.setMinorTickCount(0);
        orbitalSpeedSlider.setSnapToTicks(true);

        // Live label feedback on every value change; commit a new attempt only once the value has
        // settled (drag end, track click or keyboard), so a drag does not build discarded attempts.
        orbitalSpeedSlider.valueProperty().addListener((observable, oldValue, newValue) ->
        {
            updateOrbitalSpeedLabel(newValue.doubleValue());
            if (!orbitalSpeedSlider.isValueChanging())
            {
                commitSelectedOrbitalSpeed();
            }
        });
        orbitalSpeedSlider.valueChangingProperty().addListener((observable, wasChanging, isChanging) ->
        {
            if (wasChanging && !isChanging)
            {
                commitSelectedOrbitalSpeed();
            }
        });
        updateOrbitalSpeedLabel(orbitalSpeedSlider.getValue());

        final HBox attemptRow = new HBox(
                CONTROL_BAR_SPACING_PIXELS,
                new Label("Initial orbital speed:"),
                orbitalSpeedSlider,
                orbitalSpeedValueLabel,
                runPauseButton,
                resetButton);
        attemptRow.setAlignment(Pos.CENTER_LEFT);

        final Label timeRateValueLabel = new Label();
        final double initialDaysPerSecond =
                controller.simulatedSecondsPerRealSecond() / SECONDS_PER_DAY;
        final Slider timeRateSlider = new Slider(
                MIN_TIME_RATE_DAYS_PER_SECOND, MAX_TIME_RATE_DAYS_PER_SECOND, initialDaysPerSecond);
        timeRateSlider.setPrefWidth(TIME_RATE_SLIDER_WIDTH_PIXELS);
        timeRateSlider.valueProperty().addListener((observable, oldValue, newValue) ->
        {
            final double daysPerSecond = newValue.doubleValue();
            controller.setSimulatedSecondsPerRealSecond(daysPerSecond * SECONDS_PER_DAY);
            timeRateValueLabel.setText(formatTimeRate(daysPerSecond));
        });
        timeRateValueLabel.setText(formatTimeRate(initialDaysPerSecond));

        stateLabel = new Label();
        elapsedLabel = new Label();

        final HBox playbackRow = new HBox(
                CONTROL_BAR_SPACING_PIXELS,
                new Label("Time rate:"),
                timeRateSlider,
                timeRateValueLabel,
                stateLabel,
                new Label(INTEGRATOR_LABEL),
                elapsedLabel);
        playbackRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlPanel = new VBox(CONTROL_ROW_SPACING_PIXELS, attemptRow, playbackRow);
        controlPanel.setPadding(new Insets(CONTROL_PANEL_PADDING_PIXELS));
        return controlPanel;
    }

    private void commitSelectedOrbitalSpeed()
    {
        if (!orbitalSpeedSelectionChanged())
        {
            return;
        }
        final double fraction = quantiseSpeedFraction(orbitalSpeedSlider.getValue());
        final double speed = fraction * controller.circularSpeedMetresPerSecond();

        final AttemptCreationResult result = controller.configureAttempt(speed);
        if (result == AttemptCreationResult.CREATED)
        {
            if (Double.compare(orbitalSpeedSlider.getValue(), fraction) != 0)
            {
                orbitalSpeedSlider.setValue(fraction);
            }
            trail.clear();
            updateControlStates();
            return;
        }

        orbitalSpeedSlider.setValue(controller.configuredSpeedFractionOfCircular());
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
            public void handle(final long now)
            {
                if (previousFrameNanos == 0L)
                {
                    previousFrameNanos = now;
                    return;
                }

                final long elapsedNanos = now - previousFrameNanos;
                previousFrameNanos = now;
                if (elapsedNanos <= 0L)
                {
                    return;
                }

                final double elapsedWallClockSeconds = elapsedNanos / NANOS_PER_SECOND;
                final double boundedWallClockSeconds =
                        Math.min(elapsedWallClockSeconds, MAX_FRAME_DELTA_SECONDS);

                controller.advanceByWallClock(boundedWallClockSeconds);

                final SimulationSnapshot snapshot = controller.snapshot();
                if (controller.state() == ScenarioLifecycleState.RUNNING)
                {
                    planetPositionOf(snapshot).ifPresent(trail::record);
                }
                renderer.render(graphics, snapshot, transform, trail.points());
                updateControlStates();
            }
        };
    }
    private static String elapsedTextFor(final OptionalDouble elapsedSeconds)
    {
        if (elapsedSeconds.isEmpty())
        {
            return "Elapsed: —";
        }
        return String.format(
                Locale.ROOT,
                "Elapsed: %.1f days",
                elapsedSeconds.getAsDouble() / SECONDS_PER_DAY);
    }

    private static String formatTimeRate(final double daysPerSecond)
    {
        return String.format(Locale.ROOT, "%.0f days/s", daysPerSecond);
    }

    private static String learnerTextFor(final ScenarioTerminationReason reason)
    {
        return switch (reason)
        {
            case POLICY_COMPLETION -> "stable orbit achieved";
            case POLICY_FAILURE -> "the orbit did not meet the level criteria";
            case ASSESSMENT_INVALID -> "the orbit could not be assessed reliably";
            case SIMULATION_BREAKDOWN -> "the simulation became numerically unstable";
        };
    }

    /**
     * Standard Java entry point.
     *
     * @param args command-line arguments, forwarded unchanged to JavaFX
     */
    public static void main(final String[] args)
    {
        launch(args);
    }

    private static boolean orbitalSpeedSelectable(final ScenarioLifecycleState state)
    {
        // The initial condition may only be chosen before an attempt runs or after it has finished;
        // it is locked while running or paused, since changing it means a new attempt.
        return state == ScenarioLifecycleState.NOT_STARTED
                || state == ScenarioLifecycleState.COMPLETED
                || state == ScenarioLifecycleState.FAILED;
    }

    private boolean orbitalSpeedSelectionChanged()
    {
        final double selectedFraction = quantiseSpeedFraction(orbitalSpeedSlider.getValue());
        final double configuredFraction = controller.configuredSpeedFractionOfCircular();
        return Math.abs(selectedFraction - configuredFraction) > SPEED_FRACTION_EPSILON;
    }

    private static Optional<Vector3D> planetPositionOf(final SimulationSnapshot snapshot)
    {
        return snapshot.bodies().stream()
                .filter(body -> body.definition().type() == CelestialBodyType.PLANET)
                .map(body -> body.state().position())
                .findFirst();
    }

    private static double quantiseSpeedFraction(final double fraction)
    {
        return Math.round(fraction / SPEED_FRACTION_STEP) * SPEED_FRACTION_STEP;
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

    /**
     * Builds the Level 1 scene, renders its initial state and starts the frame loop. The scenario is
     * not started here: the learner starts it with the control.
     *
     * @param stage the primary stage supplied by JavaFX; never null
     */
    @Override
    public void start(final Stage stage)
    {
        final Level1Scenario scenario = new Level1Scenario();
        controller = new SimulationController(scenario);
        trail = new TrajectoryTrail(TRAIL_MAXIMUM_POINTS, scenario.orbitalRadiusMetres() / TRAIL_SEGMENT_DIVISOR);

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
        root.setTop(buildControlPanel());
        root.setCenter(canvas);

        renderer.render(graphics, controller.snapshot(), transform, trail.points());
        updateControlStates();

        stage.setScene(new Scene(root));
        stage.setTitle(WINDOW_TITLE);
        stage.show();

        animationTimer = createAnimationTimer(renderer, graphics, transform);
        animationTimer.start();
    }
    private static String statusTextFor(
            final ScenarioLifecycleState state,
            final Optional<ScenarioTerminationReason> reason)
    {
        if (reason.isEmpty())
        {
            return "Status: " + statusWordFor(state);
        }
        return "Status: " + statusWordFor(state) + " — " + learnerTextFor(reason.get());
    }

    private static String statusWordFor(final ScenarioLifecycleState state)
    {
        return switch (state)
        {
            case NOT_STARTED -> "Ready";
            case RUNNING -> "Running";
            case PAUSED -> "Paused";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
        };
    }

    /**
     * Stops the frame loop when the JavaFX application shuts down.
     */
    @Override
    public void stop()
    {
        if (animationTimer != null)
        {
            animationTimer.stop();
        }
    }

    private void updateControlStates()
    {
        final ScenarioLifecycleState current = controller.state();
        runPauseButton.setText(runPauseLabelFor(current));
        runPauseButton.setDisable(!controller.canToggleRun());
        orbitalSpeedSlider.setDisable(!orbitalSpeedSelectable(current));
        stateLabel.setText(statusTextFor(current, controller.terminationReason()));
        elapsedLabel.setText(elapsedTextFor(controller.elapsedSimulatedSeconds()));
    }

    private void updateOrbitalSpeedLabel(final double fraction)
    {
        final double quantised = quantiseSpeedFraction(fraction);
        final double kilometresPerSecond =
                quantised * controller.circularSpeedMetresPerSecond() / METRES_PER_SECOND_IN_KILOMETRES;
        orbitalSpeedValueLabel.setText(
                String.format(Locale.ROOT, "%.2f × circular (%.1f km/s)", quantised, kilometresPerSecond));
    }
}