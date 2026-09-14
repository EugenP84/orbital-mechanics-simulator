package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.AttemptCreationResult;
import io.github.eugenp84.orbitalsim.app.controller.SimulationController;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Hint;
import io.github.eugenp84.orbitalsim.learning.scenario.Level1Scenario;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Optional;

/**
 * The Level 1 screen: the principal learner input is the orbital-speed slider, with an adaptive hint row, wired to a {@link SimulationController}.
 * <p>
 * The generic presentation — the animation loop, trajectory trail, rendering, and the common controls
 * (run/pause, reset, time rate, status, integrator, elapsed) — lives in {@link SimulationViewCore}.
 * This screen supplies only Level 1's own controls and places the core's exposed buttons and playback row into the Level 1 layout.
 * <p>
 * The orbital-speed slider owns a single contract: whenever it is enabled, its value is the speed of the currently configured, not-yet-started attempt.
 * It commits a new attempt when a value settles (drag end, track click or keyboard),
 * not on every intermediate drag event, and only when the settled fraction actually differs from the installed one.
 * Quantisation to {@code SPEED_FRACTION_STEP} is authoritative and independent of the JavaFX slider's own snapping,
 * so thumb, label and controller always represent the same value. Start is a pure lifecycle action.
 *
 * @author Eugeniu PLATITA
 */
public final class Level1Screen implements LevelScreen
{
    private static final String INTEGRATOR_LABEL = "Integrator: Velocity Verlet (fixed)";

    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_MARGIN_FACTOR = 1.15;

    // The orbital-speed slider spans 0.5-1.5 x circular: it crosses the escape threshold at sqrt(2) ~ 1.414,
    // so one control exposes bound ellipses, the circular target and unbound escape.
    private static final double MIN_SPEED_FRACTION = 0.5;
    private static final double MAX_SPEED_FRACTION = 1.5;
    private static final double DEFAULT_SPEED_FRACTION = 1.0;

    // The learner-facing resolution of the orbital-speed control.
    // Quantising to this step is the authoritative configuration rule; the JavaFX slider's own snapping only assists the feel.
    private static final double SPEED_FRACTION_STEP = 0.01;

    // Smaller than one quantisation step, so it absorbs floating-point noise between values that
    // should be equal without ever hiding a real change of selection.
    private static final double SPEED_FRACTION_EPSILON = 1.0e-6;
    private static final double METRES_PER_SECOND_IN_KILOMETRES = 1_000.0;

    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_ROW_SPACING_PIXELS = 6.0;
    private static final double CONTROL_PANEL_PADDING_PIXELS = 10.0;
    private static final double ORBITAL_SPEED_SLIDER_WIDTH_PIXELS = 280.0;

    // The minimum spacing is approximately 1/240 of the reference orbital radius. The point cap
    // comfortably retains at least one visually smooth L1 trajectory at the supported presentation rates.
    private static final int TRAIL_MAXIMUM_POINTS = 1_500;
    private static final double TRAIL_SEGMENT_DIVISOR = 240.0;

    private final SimulationController controller;
    private final SimulationViewCore core;
    private final Parent root;

    private Slider orbitalSpeedSlider;
    private Label orbitalSpeedValueLabel;
    private Label hintLabel;

    /** Builds the Level 1 screen: its controller, view core, control panel and canvas. */
    public Level1Screen()
    {
        final Level1Scenario scenario = new Level1Scenario();
        this.controller = new SimulationController(scenario);

        final TrajectoryTrail trail = new TrajectoryTrail(TRAIL_MAXIMUM_POINTS, scenario.orbitalRadiusMetres() / TRAIL_SEGMENT_DIVISOR);

        final ViewTransform transform = ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                0.0,
                0.0,
                scenario.orbitalRadiusMetres() * VIEW_MARGIN_FACTOR);

        final Canvas canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);

        this.core = new SimulationViewCore(
                controller,
                canvas,
                transform,
                trail,
                this::refreshLevelControls,
                Level1Screen::learnerTextFor,
                Level1Screen::planetPositionOf,
                INTEGRATOR_LABEL);

        final BorderPane pane = new BorderPane();
        pane.setTop(buildControlPanel());
        pane.setCenter(canvas);
        this.root = pane;
    }

    @Override
    public void activate()
    {
        core.initialiseView();
        core.startLoop();
    }

    private VBox buildControlPanel()
    {
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
                core.runPauseButton(),
                core.resetButton());
        attemptRow.setAlignment(Pos.CENTER_LEFT);

        hintLabel = new Label();
        final HBox hintRow = new HBox(CONTROL_BAR_SPACING_PIXELS, hintLabel);
        hintRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlPanel = new VBox(CONTROL_ROW_SPACING_PIXELS, attemptRow, core.playbackRow(), hintRow);
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
            core.afterAttemptReplaced();
            return;
        }

        orbitalSpeedSlider.setValue(controller.configuredSpeedFractionOfCircular());
    }

    @Override
    public void deactivate()
    {
        core.stopLoop();
    }

    private static String hintTextFor(
            final ScenarioLifecycleState state,
            final Optional<Hint> hint)
    {
        final boolean attemptActive = state == ScenarioLifecycleState.RUNNING
                || state == ScenarioLifecycleState.PAUSED;
        if (!attemptActive || hint.isEmpty())
        {
            return "";
        }
        return "Hint: " + hint.get().message();
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

    private void refreshLevelControls()
    {
        final ScenarioLifecycleState current = controller.state();
        orbitalSpeedSlider.setDisable(!orbitalSpeedSelectable(current));
        hintLabel.setText(hintTextFor(current, controller.latestHint()));
    }

    private void updateOrbitalSpeedLabel(final double fraction)
    {
        final double quantised = quantiseSpeedFraction(fraction);
        final double kilometresPerSecond = quantised * controller.circularSpeedMetresPerSecond() / METRES_PER_SECOND_IN_KILOMETRES;
        orbitalSpeedValueLabel.setText(String.format(Locale.ROOT, "%.2f × circular (%.1f km/s)", quantised, kilometresPerSecond));
    }

    @Override
    public Parent view()
    {
        return root;
    }
}