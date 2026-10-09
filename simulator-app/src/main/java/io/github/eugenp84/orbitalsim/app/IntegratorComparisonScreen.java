package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.IntegratorComparisonController;
import io.github.eugenp84.orbitalsim.app.controller.IntegratorComparisonState;
import io.github.eugenp84.orbitalsim.app.rendering.SimulationRenderer;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonExperiment;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorComparisonResult;
import io.github.eugenp84.orbitalsim.evaluation.integrator.IntegratorMethod;
import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Optional;

/**
 * Interactive JavaFX screen for controlled comparison of Forward Euler,
 * Velocity Verlet and classical RK4 on the canonical circular two-body experiment.
 * <p>
 * Only one numerical method is executed and rendered at a time.
 * Completed results are retained by the controller and displayed together so the user can compare energy conservation,
 * analytical trajectory error and acceleration-model evaluation count.
 * <p>
 * Presentation speed affects only how quickly fixed six-hour numerical steps are requested. It never changes the experiment's integration timestep.
 *
 * @author Eugeniu PLATITA
 */
public final class IntegratorComparisonScreen implements LevelScreen
{
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double MAX_FRAME_DELTA_SECONDS = 1.0 / 30.0;
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double SECONDS_PER_HOUR = 3_600.0;
    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_MARGIN_FACTOR = 1.15;
    private static final int TRAIL_MAXIMUM_POINTS = 1_500;
    private static final double TRAIL_SEGMENT_DIVISOR = 240.0;
    private static final double MIN_TIME_RATE_DAYS_PER_SECOND = 1.0;
    private static final double MAX_TIME_RATE_DAYS_PER_SECOND = 60.0;
    private static final double DEFAULT_TIME_RATE_DAYS_PER_SECOND = 14.0;
    private static final double TIME_RATE_SLIDER_WIDTH_PIXELS = 260.0;
    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_ROW_SPACING_PIXELS = 6.0;
    private static final double CONTROL_PANEL_PADDING_PIXELS = 10.0;

    private final IntegratorComparisonController controller;
    private final SimulationRenderer renderer;
    private final TrajectoryTrail trail;
    private final ViewTransform transform;
    private final Canvas canvas;
    private final Parent simulationRegion;
    private final Parent controlsRegion;
    private ComboBox<IntegratorMethod> methodSelector;
    private Button runPauseButton;
    private Button resetButton;
    private Slider timeRateSlider;
    private Label timeRateValueLabel;
    private Label stateLabel;
    private Label elapsedLabel;
    private Label currentEnergyLabel;
    private Label currentTrajectoryErrorLabel;
    private Label currentEvaluationsLabel;
    private Label eulerEnergyLabel;
    private Label eulerTrajectoryLabel;
    private Label eulerEvaluationsLabel;
    private Label verletEnergyLabel;
    private Label verletTrajectoryLabel;
    private Label verletEvaluationsLabel;
    private Label rk4EnergyLabel;
    private Label rk4TrajectoryLabel;
    private Label rk4EvaluationsLabel;

    private AnimationTimer animationTimer;

    /**
     * Builds the numerical-integrator comparison screen.
     */
    public IntegratorComparisonScreen()
    {
        this.controller = new IntegratorComparisonController();
        this.renderer = new SimulationRenderer();

        this.trail = new TrajectoryTrail(TRAIL_MAXIMUM_POINTS,PhysicalConstants.ASTRONOMICAL_UNIT / TRAIL_SEGMENT_DIVISOR);

        this.transform = ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                0.0,
                0.0,
                PhysicalConstants.ASTRONOMICAL_UNIT * VIEW_MARGIN_FACTOR);

        this.canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);
        this.simulationRegion = new StackPane(canvas);
        this.controlsRegion = buildControlsPanel();
    }

    /**
     * Activates the screen and starts its presentation loop.
     */
    @Override
    public void activate()
    {
        renderCurrent();
        refreshControls();
        startLoop();
    }

    /**
     * Builds the comparison controls and retained-results panel.
     *
     * @return controls region
     */
    private VBox buildControlsPanel()
    {
        methodSelector = new ComboBox<>(FXCollections.observableArrayList(IntegratorMethod.values()));
        methodSelector.setValue(controller.selectedMethod());
        methodSelector.setConverter(new javafx.util.StringConverter<>()
        {
            @Override
            public IntegratorMethod fromString(final String text)
            {
                return IntegratorMethod.valueOf(text);
            }

            @Override
            public String toString(final IntegratorMethod method)
            {
                return method == null ? "" : method.displayName();
            }
        });

        methodSelector.setOnAction(event ->
        {
            final IntegratorMethod selected = methodSelector.getValue();

            if (selected != null && selected != controller.selectedMethod())
            {
                controller.selectMethod(selected);
                trail.clear();
                renderCurrent();
                refreshControls();
            }
        });

        runPauseButton = new Button();
        runPauseButton.setOnAction(event ->
        {
            controller.toggleRun();
            refreshControls();
        });

        resetButton = new Button("Reset");
        resetButton.setOnAction(event ->
        {
            controller.reset();
            trail.clear();
            renderCurrent();
            refreshControls();
        });

        timeRateValueLabel = new Label();
        timeRateSlider = new Slider(
                MIN_TIME_RATE_DAYS_PER_SECOND,
                MAX_TIME_RATE_DAYS_PER_SECOND,
                DEFAULT_TIME_RATE_DAYS_PER_SECOND);
        timeRateSlider.setPrefWidth(TIME_RATE_SLIDER_WIDTH_PIXELS);

        timeRateSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                {
                    final double daysPerSecond = newValue.doubleValue();

                    controller.setSimulatedSecondsPerRealSecond(daysPerSecond * SECONDS_PER_DAY);
                    timeRateValueLabel.setText(formatTimeRate(daysPerSecond));
                });

        controller.setSimulatedSecondsPerRealSecond(DEFAULT_TIME_RATE_DAYS_PER_SECOND * SECONDS_PER_DAY);
        timeRateValueLabel.setText(formatTimeRate(DEFAULT_TIME_RATE_DAYS_PER_SECOND));

        stateLabel = new Label();
        elapsedLabel = new Label();
        currentEnergyLabel = new Label();
        currentTrajectoryErrorLabel = new Label();
        currentEvaluationsLabel = new Label();
        eulerEnergyLabel = new Label();
        eulerTrajectoryLabel = new Label();
        eulerEvaluationsLabel = new Label();
        verletEnergyLabel = new Label();
        verletTrajectoryLabel = new Label();
        verletEvaluationsLabel = new Label();
        rk4EnergyLabel = new Label();
        rk4TrajectoryLabel = new Label();
        rk4EvaluationsLabel = new Label();

        final HBox buttonRow = new HBox(
                CONTROL_BAR_SPACING_PIXELS,
                runPauseButton,
                resetButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlsPanel = new VBox(
                CONTROL_ROW_SPACING_PIXELS,
                sectionHeading("NUMERICAL INTEGRATOR COMPARISON"),
                new Label("Integrator"),
                methodSelector,
                buttonRow,
                new Separator(),
                sectionHeading("SIMULATION"),
                new Label("Time rate"),
                timeRateSlider,
                timeRateValueLabel,
                labelledValue("Status", stateLabel),
                labelledValue("Elapsed", elapsedLabel),
                labelledValue("Fixed timestep", new Label(formatFixedTimeStep())),
                new Separator(),
                sectionHeading("CURRENT RUN"),
                labelledValue("Max relative energy deviation", currentEnergyLabel), labelledValue("Max trajectory error vs analytical", currentTrajectoryErrorLabel),
                labelledValue("Acceleration evaluations", currentEvaluationsLabel), new Separator(), sectionHeading("COMPLETED COMPARISON"),
                comparisonRow(
                        "Integrator",
                        "Energy deviation",
                        "Trajectory error",
                        "Evaluations"),
                comparisonRow(
                        IntegratorMethod.FORWARD_EULER.displayName(),
                        eulerEnergyLabel,
                        eulerTrajectoryLabel,
                        eulerEvaluationsLabel),
                comparisonRow(
                        IntegratorMethod.VELOCITY_VERLET.displayName(),
                        verletEnergyLabel,
                        verletTrajectoryLabel,
                        verletEvaluationsLabel),
                comparisonRow(
                        IntegratorMethod.RUNGE_KUTTA_4.displayName(),
                        rk4EnergyLabel,
                        rk4TrajectoryLabel,
                        rk4EvaluationsLabel));

        controlsPanel.setPadding(new Insets(CONTROL_PANEL_PADDING_PIXELS));

        return controlsPanel;
    }

    /**
     * Builds one textual comparison row.
     *
     * @param method method name
     * @param energy energy heading
     * @param trajectory trajectory heading
     * @param evaluations evaluation-count heading
     * @return row
     */
    private static HBox comparisonRow(
            final String method,
            final String energy,
            final String trajectory,
            final String evaluations)
    {
        return comparisonRow(
                method,
                new Label(energy),
                new Label(trajectory),
                new Label(evaluations));
    }

    /**
     * Builds one comparison-results row.
     *
     * @param method method name
     * @param energy energy value label
     * @param trajectory trajectory-error value label
     * @param evaluations acceleration-evaluation value label
     * @return row
     */
    private static HBox comparisonRow(
            final String method,
            final Label energy,
            final Label trajectory,
            final Label evaluations)
    {
        final Label methodLabel = new Label(method);

        methodLabel.setPrefWidth(125.0);
        energy.setPrefWidth(110.0);
        trajectory.setPrefWidth(110.0);
        evaluations.setPrefWidth(80.0);

        return new HBox(
                CONTROL_ROW_SPACING_PIXELS,
                methodLabel,
                energy,
                trajectory,
                evaluations);
    }

    /**
     * Creates the JavaFX animation timer.
     *
     * @return fresh timer
     */
    private AnimationTimer createTimer()
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
                final double boundedWallClockSeconds = Math.min(elapsedWallClockSeconds, MAX_FRAME_DELTA_SECONDS);
                final SimulationSnapshot before = controller.snapshot();
                controller.advanceByWallClock(boundedWallClockSeconds);
                final SimulationSnapshot after = controller.snapshot();

                if (after.completedSteps() > before.completedSteps())
                {
                    planetPositionOf(after).ifPresent(trail::record);
                }

                renderCurrent();
                refreshControls();
            }
        };
    } // ------- end of createTimer() ------------------------------

    /**
     * Deactivates the screen and stops its presentation loop.
     */
    @Override
    public void deactivate() {
        stopLoop();
    }

    /**
     * Formats the fixed numerical timestep for display.
     *
     * @return learner-facing timestep text
     */
    private static String formatFixedTimeStep()
    {
        final double hours = IntegratorComparisonExperiment.TIME_STEP_SECONDS / SECONDS_PER_HOUR;
        return String.format(Locale.ROOT, "%.0f hours", hours);
    }

    /**
     * Formats a non-negative relative energy deviation adaptively.
     *
     * @param value dimensionless deviation
     * @return formatted value
     */
    private static String formatRelativeDeviation(final double value)
    {
        if (value == 0.0) {
            return "0";
        }

        if (value >= 0.001)
        {
            return String.format(Locale.ROOT, "%.4f", value);
        }

        return String.format(Locale.ROOT, "%.3e", value);
    }

    /**
     * Formats a trajectory error adaptively in metres or kilometres.
     *
     * @param metres trajectory error in metres
     * @return formatted distance
     */
    private static String formatTrajectoryError(final double metres)
    {
        if (metres < 1_000.0)
        {
            return String.format(Locale.ROOT, "%.2f m", metres);
        }

        final double kilometres = metres / 1_000.0;

        if (kilometres < 1_000.0)
        {
            return String.format(Locale.ROOT, "%.2f km", kilometres);
        }

        return String.format(Locale.ROOT,"%.3e km", kilometres);
    }

    /**
     * Formats the presentation rate.
     *
     * @param daysPerSecond simulated days per real second
     * @return formatted rate
     */
    private static String formatTimeRate(final double daysPerSecond)
    {
        return String.format(Locale.ROOT,"%.0f days / second", daysPerSecond);
    }

    /**
     * Builds a caption-value pair.
     *
     * @param caption caption text
     * @param value value label
     * @return vertical caption-value region
     */
    private static VBox labelledValue(
            final String caption,
            final Label value)
    {
        value.setWrapText(true);

        return new VBox(new Label(caption), value);
    }

    /**
     * Returns the current planet position.
     *
     * @param snapshot committed simulation snapshot
     * @return planet position, or empty if no planet exists
     */
    private static Optional<Vector3D> planetPositionOf(final SimulationSnapshot snapshot)
    {
        return snapshot.bodies().stream()
                .filter(
                        body ->
                                body.definition().type()
                                        == CelestialBodyType.PLANET)
                .map(body -> body.state().position())
                .findFirst();
    }

    /**
     * Refreshes the current-run measurement labels.
     */
    private void refreshCurrentResult()
    {
        final IntegratorComparisonResult result =  controller.currentResult();

        currentEnergyLabel.setText(formatRelativeDeviation(result.maxRelativeEnergyDeviation()));

        currentTrajectoryErrorLabel.setText(formatTrajectoryError(result.maxTrajectoryErrorMetres()));

        currentEvaluationsLabel.setText(Long.toString(result.accelerationEvaluations()));
    }

    /**
     * Refreshes all generic and comparison-specific controls.
     */
    private void refreshControls()
    {
        final IntegratorComparisonState state = controller.state();

        runPauseButton.setText(runPauseText(state));
        runPauseButton.setDisable(state == IntegratorComparisonState.COMPLETED);
        methodSelector.setDisable(!controller.canSelectMethod());
        stateLabel.setText(stateText(state));
        elapsedLabel.setText(String.format(Locale.ROOT,"%.1f days",controller.snapshot().simulationTimeSeconds() / SECONDS_PER_DAY));
        refreshCurrentResult();
        refreshCompletedResults();
    }

    /**
     * Refreshes the retained completed-results rows.
     */
    private void refreshCompletedResults()
    {
        refreshCompletedResult(
                IntegratorMethod.FORWARD_EULER,
                eulerEnergyLabel,
                eulerTrajectoryLabel,
                eulerEvaluationsLabel);

        refreshCompletedResult(
                IntegratorMethod.VELOCITY_VERLET,
                verletEnergyLabel,
                verletTrajectoryLabel,
                verletEvaluationsLabel);

        refreshCompletedResult(
                IntegratorMethod.RUNGE_KUTTA_4,
                rk4EnergyLabel,
                rk4TrajectoryLabel,
                rk4EvaluationsLabel);
    }

    /**
     * Refreshes one retained comparison row.
     *
     * @param method integration method
     * @param energy energy label
     * @param trajectory trajectory-error label
     * @param evaluations acceleration-evaluation label
     */
    private void refreshCompletedResult(
            final IntegratorMethod method,
            final Label energy,
            final Label trajectory,
            final Label evaluations)
    {
        final Optional<IntegratorComparisonResult> result = controller.completedResult(method);

        if (result.isEmpty())
        {
            energy.setText("—");
            trajectory.setText("—");
            evaluations.setText("—");
            return;
        }

        final IntegratorComparisonResult completed = result.orElseThrow();

        energy.setText(formatRelativeDeviation(completed.maxRelativeEnergyDeviation()));
        trajectory.setText(formatTrajectoryError(completed.maxTrajectoryErrorMetres()));
        evaluations.setText(Long.toString(completed.accelerationEvaluations()));
    }

    /**
     * Renders the latest committed simulation state.
     */
    private void renderCurrent()
    {
        renderer.render(
                canvas.getGraphicsContext2D(),
                controller.snapshot(),
                transform,
                trail.points());
    }

    /**
     * Returns the run/pause button text for the current state.
     *
     * @param state comparison lifecycle state
     * @return button text
     */
    private static String runPauseText(final IntegratorComparisonState state)
    {
        return switch (state)
        {
            case READY -> "Start";
            case RUNNING -> "Pause";
            case PAUSED -> "Resume";
            case COMPLETED -> "Start";
        };
    }

    /**
     * Builds a bold section heading.
     *
     * @param text heading text
     * @return heading label
     */
    private static Label sectionHeading(final String text)
    {
        final Label heading = new Label(text);
        heading.setStyle("-fx-font-weight: bold;");
        return heading;
    }

    /**
     * Returns the comparison state text.
     *
     * @param state comparison lifecycle state
     * @return display text
     */
    private static String stateText(final IntegratorComparisonState state)
    {
        return switch (state)
        {
            case READY -> "Ready";
            case RUNNING -> "Running";
            case PAUSED -> "Paused";
            case COMPLETED -> "Completed";
        };
    }

    /**
     * Starts the animation loop.
     */
    private void startLoop()
    {
        if (animationTimer != null) {
            throw new IllegalStateException("animation loop is already started");
        }

        animationTimer = createTimer();
        animationTimer.start();
    }

    /**
     * Stops the animation loop if active.
     */
    private void stopLoop()
    {
        if (animationTimer != null)
        {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    /**
     * Returns the comparison controls region.
     *
     * @return controls view
     */
    @Override
    public Parent controlsView() {
        return controlsRegion;
    }

    /**
     * Returns the comparison simulation region.
     *
     * @return simulation view
     */
    @Override
    public Parent simulationView() {
        return simulationRegion;
    }
}