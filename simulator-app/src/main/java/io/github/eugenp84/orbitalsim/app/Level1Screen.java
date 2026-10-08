package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.AttemptCreationResult;
import io.github.eugenp84.orbitalsim.app.controller.SimulationController;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateAnalysis;
import io.github.eugenp84.orbitalsim.core.orbital.OrbitalStateCalculator;
import io.github.eugenp84.orbitalsim.core.simulation.BodySnapshot;
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
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Optional;

/**
 * The Level 1 screen: the principal learner input is the orbital-speed slider, with an adaptive hint, wired to a {@link SimulationController}.
 * <p>
 * The generic presentation lives in {@link SimulationViewCore}.
 * This screen supplies Level 1's own controls, and exposes two regions the host composes:
 * a simulation region (the canvas) and a vertical controls region (orbital-speed control, Start/Reset, hint, and the shared simulation readouts placed from the core's individually exposed widgets).
 * <p>
 * The orbital-speed slider owns a single contract: whenever it is enabled, its value is the speed of the currently configured, not-yet-started attempt.
 * It commits a new attempt when a value settles (drag end, track click or keyboard), and only when the settled fraction differs from the installed one.
 * Quantisation to {@code SPEED_FRACTION_STEP} is authoritative and independent of the JavaFX slider's own snapping. Start is a pure lifecycle action.
 *
 * @author Eugeniu PLATITA
 */
public final class Level1Screen implements LevelScreen
{
    private static final String INTEGRATOR_VALUE = "Velocity Verlet (fixed)";

    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_MARGIN_FACTOR = 1.15;

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
    private static final double ORBITAL_SPEED_SLIDER_WIDTH_PIXELS = 260.0;

    // The minimum spacing is approximately 1/240 of the reference orbital radius. The point cap
    // comfortably retains at least one visually smooth L1 trajectory at the supported presentation rates.
    private static final int TRAIL_MAXIMUM_POINTS = 1_500;
    private static final double TRAIL_SEGMENT_DIVISOR = 240.0;

    private final SimulationController controller;
    private final SimulationViewCore core;
    private final Parent simulationRegion;
    private final Parent controlsRegion;

    private Slider orbitalSpeedSlider;
    private Label orbitalSpeedValueLabel;
    private Label hintLabel;
    private Label eccentricityLabel;
    private Label semiMajorAxisLabel;
    private Label periapsisLabel;
    private Label apoapsisLabel;
    private Label currentDistanceLabel;

    /** Builds the Level 1 screen: its controller, view core, and the simulation and controls regions. */
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
                INTEGRATOR_VALUE);

        final VBox orbitalOverlay = buildOrbitalOverlay();
        final StackPane simulationPane = new StackPane(canvas, orbitalOverlay);
        StackPane.setAlignment(orbitalOverlay, Pos.TOP_LEFT);
        StackPane.setMargin(orbitalOverlay, new Insets(18.0));
        this.simulationRegion = simulationPane;
        this.controlsRegion = buildControlsPanel();
    }

    @Override
    public void activate()
    {
        core.initialiseView();
        core.startLoop();
    }

    private VBox buildControlsPanel()
    {
        orbitalSpeedValueLabel = new Label();
        orbitalSpeedSlider = new Slider(MIN_SPEED_FRACTION, MAX_SPEED_FRACTION, DEFAULT_SPEED_FRACTION);
        orbitalSpeedSlider.setPrefWidth(ORBITAL_SPEED_SLIDER_WIDTH_PIXELS);
        orbitalSpeedSlider.setBlockIncrement(SPEED_FRACTION_STEP);
        orbitalSpeedSlider.setMajorTickUnit(SPEED_FRACTION_STEP);
        orbitalSpeedSlider.setMinorTickCount(0);
        orbitalSpeedSlider.setSnapToTicks(true);

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

        hintLabel = new Label();
        hintLabel.setWrapText(true);

        final HBox buttonRow = new HBox(CONTROL_BAR_SPACING_PIXELS, core.runPauseButton(), core.resetButton());
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlsPanel = new VBox(
                CONTROL_ROW_SPACING_PIXELS,
                sectionHeading("LEVEL 1 — STABLE ORBIT"),
                new Label("Initial orbital speed"),
                orbitalSpeedSlider,
                orbitalSpeedValueLabel,
                buttonRow,
                hintLabel,
                new Separator(),
                sectionHeading("SIMULATION"),
                new Label("Time rate"),
                core.timeRateControl(),
                core.timeRateValueLabel(),
                labelledValue("Status", core.statusLabel()),
                labelledValue("Integrator", core.integratorLabel()),
                labelledValue("Elapsed", core.elapsedLabel()));
        controlsPanel.setPadding(new Insets(CONTROL_PANEL_PADDING_PIXELS));
        return controlsPanel;
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
    public Parent controlsView()
    {
        return controlsRegion;
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

    private static VBox labelledValue(final String caption, final Label value)
    {
        value.setWrapText(true);
        return new VBox(new Label(caption), value);
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
        refreshOrbitalOverlay();
    }

    /** Creates a passive overlay; it does not intercept canvas mouse input. */
    private VBox buildOrbitalOverlay()
    {
        eccentricityLabel = orbitalReadout();
        semiMajorAxisLabel = orbitalReadout();
        periapsisLabel = orbitalReadout();
        apoapsisLabel = orbitalReadout();
        currentDistanceLabel = orbitalReadout();

        final Label title = new Label("ORBITAL PARAMETERS");
        title.setStyle("-fx-text-fill: #aebfd7; -fx-font-weight: bold;");

        final VBox overlay = new VBox(7.0, title, new Separator(),
                eccentricityLabel, semiMajorAxisLabel, periapsisLabel,
                apoapsisLabel, currentDistanceLabel);
        overlay.setPadding(new Insets(14.0));
        overlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        overlay.setPrefWidth(245.0);
        overlay.setMouseTransparent(true);
        overlay.setStyle("-fx-background-color: rgba(25, 37, 54, 0.84); "
                + "-fx-background-radius: 8; -fx-border-radius: 8; "
                + "-fx-border-color: rgba(145, 170, 205, 0.35);");
        return overlay;
    }

    private static Label orbitalReadout()
    {
        final Label label = new Label("—");
        label.setStyle("-fx-text-fill: #f0f4fb; -fx-font-size: 12px;");
        return label;
    }

    /** Reads only the committed state; no engine or scenario state is mutated. */
    private void refreshOrbitalOverlay()
    {
        final SimulationSnapshot snapshot = controller.snapshot();
        final Optional<BodySnapshot> star = snapshot.bodies().stream()
                .filter(body -> body.definition().type() == CelestialBodyType.STAR)
                .findFirst();
        final Optional<BodySnapshot> planet = snapshot.bodies().stream()
                .filter(body -> body.definition().type() == CelestialBodyType.PLANET)
                .findFirst();

        if (star.isEmpty() || planet.isEmpty())
        {
            showUnavailableOrbit();
            return;
        }

        final BodySnapshot primary = star.orElseThrow();
        final BodySnapshot secondary = planet.orElseThrow();
        final Vector3D relativePosition = secondary.state().position()
                .subtract(primary.state().position());
        final Vector3D relativeVelocity = secondary.state().velocity()
                .subtract(primary.state().velocity());
        final double mu = PhysicalConstants.GRAVITATIONAL_CONSTANT
                * (primary.definition().mass() + secondary.definition().mass());

        try
        {
            final OrbitalStateAnalysis orbit = OrbitalStateCalculator.analyse(
                    relativePosition, relativeVelocity, mu);
            final double eccentricity = orbit.eccentricity();
            eccentricityLabel.setText(String.format(
                    Locale.ROOT, "Eccentricity (e): %.4f", eccentricity));
            currentDistanceLabel.setText("Current distance: "
                    + formatAu(relativePosition.magnitude()));

            // Bound, non-radial Keplerian osculating ellipse only.
            if (orbit.specificOrbitalEnergy() < 0.0
                    && !orbit.isRadial() && orbit.semiMajorAxis().isPresent())
            {
                final double a = orbit.semiMajorAxis().getAsDouble();
                semiMajorAxisLabel.setText("Semi-major axis: " + formatAu(a));
                periapsisLabel.setText("Periapsis: " + formatAu(a * (1.0 - eccentricity)));
                apoapsisLabel.setText("Apoapsis: " + formatAu(a * (1.0 + eccentricity)));
            }
            else
            {
                semiMajorAxisLabel.setText("Semi-major axis: N/A (bound orbit)");
                periapsisLabel.setText("Periapsis: N/A (bound orbit)");
                apoapsisLabel.setText("Apoapsis: N/A (bound orbit)");
            }
        }
        catch (IllegalArgumentException invalidState)
        {
            showUnavailableOrbit();
        }
    }

    private static String formatAu(final double metres)
    {
        return Double.isFinite(metres) && metres >= 0.0
                ? String.format(Locale.ROOT, "%.4f AU",
                metres / PhysicalConstants.ASTRONOMICAL_UNIT)
                : "N/A";
    }

    private void showUnavailableOrbit()
    {
        eccentricityLabel.setText("Eccentricity: N/A");
        semiMajorAxisLabel.setText("Semi-major axis: N/A");
        periapsisLabel.setText("Periapsis: N/A");
        apoapsisLabel.setText("Apoapsis: N/A");
        currentDistanceLabel.setText("Current distance: N/A");
    }

    private static Label sectionHeading(final String text)
    {
        final Label heading = new Label(text);
        heading.setStyle("-fx-font-weight: bold;");
        return heading;
    }

    @Override
    public Parent simulationView()
    {
        return simulationRegion;
    }

    private void updateOrbitalSpeedLabel(final double fraction)
    {
        final double quantised = quantiseSpeedFraction(fraction);
        final double kilometresPerSecond = quantised * controller.circularSpeedMetresPerSecond() / METRES_PER_SECOND_IN_KILOMETRES;
        orbitalSpeedValueLabel.setText(String.format(Locale.ROOT, "%.2f × circular (%.1f km/s)", quantised, kilometresPerSecond));
    }
}