package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.Level0Controller;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.model.CelestialBodyType;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level0Scenario;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.Locale;
import java.util.Optional;

/**
 * The Level 0 (Planet Creation) screen: the learner sets a planet's mass, radius, orbital distance and tangential speed, sees it created and orbiting, and the scenario completes at the first observation.
 * <p>
 * The generic presentation lives in {@link SimulationViewCore}.
 * this screen supplies Level 0's four parameter sliders and the circular-speed reference, and exposes a simulation region (the canvas) and a vertical controls region.
 * Tangential speed is shown as an absolute value in kilometres per second,
 * not as a fraction of circular speed — introducing velocity as a physical quantity, distinct from Level 1's speed-relative task.
 * <p>
 * The slider value labels track the current selection live; the circular-speed reference describes the
 * currently configured attempt and is updated only on a successful commit, so it is labelled accordingly and does not appear to change mid-drag before the attempt is rebuilt.
 * The view uses a fixed scale across the supported orbital-distance range so different configurations remain visually comparable.
 * Changing orbital distance re-scales the trail sampling but does not re-frame the viewport.
 *
 * @author Eugeniu PLATITA
 */
public final class Level0Screen implements LevelScreen
{
    private static final String INTEGRATOR_VALUE = "Velocity Verlet";
    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_RADIUS_AU = 2.3;
    private static final double EARTH_MASS = PhysicalConstants.EARTH_MASS;
    private static final double EARTH_RADIUS = PhysicalConstants.EARTH_RADIUS;
    private static final double ASTRONOMICAL_UNIT = PhysicalConstants.ASTRONOMICAL_UNIT;
    private static final double METRES_PER_SECOND_IN_KILOMETRES = 1_000.0;

    // Pedagogical interaction ranges (not validity bounds). Mass and radius in Earth units;
    // distance in astronomical units; tangential speed as an absolute value in kilometres per second.
    private static final double MIN_MASS_EARTHS = 0.1;
    private static final double MAX_MASS_EARTHS = 10.0;
    private static final double DEFAULT_MASS_EARTHS = 1.0;
    private static final double MIN_RADIUS_EARTHS = 0.5;
    private static final double MAX_RADIUS_EARTHS = 5.0;
    private static final double DEFAULT_RADIUS_EARTHS = 1.0;
    private static final double MIN_DISTANCE_AU = 0.3;
    private static final double MAX_DISTANCE_AU = 2.0;
    private static final double DEFAULT_DISTANCE_AU = 1.0;
    private static final double MIN_SPEED_KILOMETRES_PER_SECOND = 5.0;
    private static final double MAX_SPEED_KILOMETRES_PER_SECOND = 50.0;
    private static final double VIEW_RADIUS_METRES = 1.15 * MAX_DISTANCE_AU * PhysicalConstants.ASTRONOMICAL_UNIT;

    // Settled-value comparison tolerances, each far smaller than a meaningful slider step, so they absorb floating-point noise without hiding a real change of selection.
    private static final double MASS_EPSILON_KILOGRAMS = 1.0;
    private static final double RADIUS_EPSILON_METRES = 1.0;
    private static final double DISTANCE_EPSILON_METRES = 1.0;
    private static final double SPEED_EPSILON_METRES_PER_SECOND = 1.0e-3;
    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_ROW_SPACING_PIXELS = 6.0;
    private static final double CONTROL_PANEL_PADDING_PIXELS = 10.0;
    private static final double PARAMETER_SLIDER_WIDTH_PIXELS = 260.0;
    private static final int TRAIL_MAXIMUM_POINTS = 1_500;
    private static final double TRAIL_SEGMENT_DIVISOR = 240.0;

    private final Level0Controller controller;
    private final TrajectoryTrail trail;
    private final SimulationViewCore core;
    private final Parent simulationRegion;
    private final Parent controlsRegion;

    private Slider massSlider;
    private Slider radiusSlider;
    private Slider distanceSlider;
    private Slider speedSlider;
    private Label massValueLabel;
    private Label radiusValueLabel;
    private Label distanceValueLabel;
    private Label speedValueLabel;
    private Label circularReferenceLabel;

    /** Builds the Level 0 screen: its controller, view core, and the simulation and controls regions. */
    public Level0Screen()
    {
        this.controller = new Level0Controller(new Level0Scenario());

        this.trail = new TrajectoryTrail(TRAIL_MAXIMUM_POINTS, controller.configuredDistanceMetres() / TRAIL_SEGMENT_DIVISOR);

        //final ViewTransform transform = transformForDistance(controller.configuredDistanceMetres());
        final ViewTransform transform = ViewTransform.fitting(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS,0.0,0.0, VIEW_RADIUS_AU * ASTRONOMICAL_UNIT);

        final Canvas canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);

        this.core = new SimulationViewCore(
                controller,
                canvas,
                transform,
                trail,
                this::refreshLevelControls,
                Level0Screen::learnerTextFor,
                Level0Screen::planetPositionOf,
                INTEGRATOR_VALUE);

        this.simulationRegion = new StackPane(canvas);
        this.controlsRegion = buildControlPanel();
    }

    @Override
    public void activate()
    {
        core.initialiseView();
        core.startLoop();
    }

    private VBox buildControlPanel()
    {
        massSlider = buildParameterSlider(MIN_MASS_EARTHS, MAX_MASS_EARTHS, DEFAULT_MASS_EARTHS);
        radiusSlider = buildParameterSlider(MIN_RADIUS_EARTHS, MAX_RADIUS_EARTHS, DEFAULT_RADIUS_EARTHS);
        distanceSlider = buildParameterSlider(MIN_DISTANCE_AU, MAX_DISTANCE_AU, DEFAULT_DISTANCE_AU);

        final double defaultSpeedKilometres = controller.configuredTangentialSpeedMetresPerSecond() / METRES_PER_SECOND_IN_KILOMETRES;
        speedSlider = buildParameterSlider(MIN_SPEED_KILOMETRES_PER_SECOND, MAX_SPEED_KILOMETRES_PER_SECOND, defaultSpeedKilometres);

        massValueLabel = new Label();
        radiusValueLabel = new Label();
        distanceValueLabel = new Label();
        speedValueLabel = new Label();
        circularReferenceLabel = new Label();

        registerParameterListeners(massSlider, false);
        registerParameterListeners(radiusSlider, false);
        registerParameterListeners(distanceSlider, true);
        registerParameterListeners(speedSlider, false);

        updateSliderValueLabels();
        updateCircularReferenceLabel();

        final HBox buttonRow = new HBox(CONTROL_BAR_SPACING_PIXELS, core.runPauseButton(), core.resetButton());
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlPanel = new VBox(
                CONTROL_ROW_SPACING_PIXELS,
                sectionHeading("LEVEL 0 — CREATE A PLANET"),
                new Label("Mass"), massSlider, massValueLabel,
                new Label("Radius"), radiusSlider, radiusValueLabel,
                new Label("Orbital distance"), distanceSlider, distanceValueLabel,
                new Label("Tangential speed"), speedSlider, speedValueLabel,
                circularReferenceLabel,
                buttonRow,
                new Separator(),
                sectionHeading("SIMULATION"),
                new Label("Time rate"),
                core.timeRateControl(),
                core.timeRateValueLabel(),
                labelledValue("Status", core.statusLabel()),
                labelledValue("Integrator", core.integratorLabel()),
                labelledValue("Elapsed", core.elapsedLabel()));
        controlPanel.setPadding(new Insets(CONTROL_PANEL_PADDING_PIXELS));
        return controlPanel;
    }

    private Slider buildParameterSlider(final double min, final double max, final double value)
    {
        final Slider slider = new Slider(min, max, value);
        slider.setPrefWidth(PARAMETER_SLIDER_WIDTH_PIXELS);
        return slider;
    }

    private void commitConfiguration(final boolean distanceChanged)
    {
        final double massKilograms = massSlider.getValue() * EARTH_MASS;
        final double radiusMetres = radiusSlider.getValue() * EARTH_RADIUS;
        final double distanceMetres = distanceSlider.getValue() * ASTRONOMICAL_UNIT;
        final double speedMetresPerSecond = speedSlider.getValue() * METRES_PER_SECOND_IN_KILOMETRES;

        if (!configurationChanged(massKilograms, radiusMetres, distanceMetres, speedMetresPerSecond))
        {
            return;
        }

        controller.configureAttempt(massKilograms, radiusMetres, distanceMetres, speedMetresPerSecond);

        if (distanceChanged)
        {
            trail.setMinimumSegmentMetres(controller.configuredDistanceMetres() / TRAIL_SEGMENT_DIVISOR);
        }
        core.afterAttemptReplaced();
        updateSliderValueLabels();
        updateCircularReferenceLabel();
    }

    private boolean configurationChanged(
            final double massKilograms,
            final double radiusMetres,
            final double distanceMetres,
            final double speedMetresPerSecond)
    {
        return Math.abs(massKilograms - controller.configuredMassKilograms()) > MASS_EPSILON_KILOGRAMS
                || Math.abs(radiusMetres - controller.configuredRadiusMetres()) > RADIUS_EPSILON_METRES
                || Math.abs(distanceMetres - controller.configuredDistanceMetres()) > DISTANCE_EPSILON_METRES
                || Math.abs(speedMetresPerSecond - controller.configuredTangentialSpeedMetresPerSecond())
                > SPEED_EPSILON_METRES_PER_SECOND;
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

    private static VBox labelledValue(final String caption, final Label value)
    {
        value.setWrapText(true);
        return new VBox(new Label(caption), value);
    }

    private static String learnerTextFor(final ScenarioTerminationReason reason)
    {
        return switch (reason)
        {
            case POLICY_COMPLETION -> "planet created";
            case POLICY_FAILURE -> "the level could not be completed";
            case ASSESSMENT_INVALID -> "the configuration could not be assessed";
            case SIMULATION_BREAKDOWN -> "the simulation became numerically unstable";
        };
    }

    private static boolean parametersSelectable(final ScenarioLifecycleState state)
    {
        // Parameters define the attempt: selectable before it runs or after it finishes, locked while
        // running or paused (matching Level 1's configuration semantics).
        return state == ScenarioLifecycleState.NOT_STARTED
                || state == ScenarioLifecycleState.COMPLETED
                || state == ScenarioLifecycleState.FAILED;
    }

    private static Optional<Vector3D> planetPositionOf(final SimulationSnapshot snapshot)
    {
        return snapshot.bodies().stream()
                .filter(body -> body.definition().type() == CelestialBodyType.PLANET)
                .map(body -> body.state().position())
                .findFirst();
    }

    private void refreshLevelControls()
    {
        final boolean selectable = parametersSelectable(controller.state());
        massSlider.setDisable(!selectable);
        radiusSlider.setDisable(!selectable);
        distanceSlider.setDisable(!selectable);
        speedSlider.setDisable(!selectable);
        updateCircularReferenceLabel();
    }

    private void registerParameterListeners(final Slider slider, final boolean distanceChanged)
    {
        slider.valueProperty().addListener((observable, oldValue, newValue) ->
        {
            updateSliderValueLabels();
            if (!slider.isValueChanging())
            {
                commitConfiguration(distanceChanged);
            }
        });
        slider.valueChangingProperty().addListener((observable, wasChanging, isChanging) ->
        {
            if (wasChanging && !isChanging)
            {
                commitConfiguration(distanceChanged);
            }
        });
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

//    private ViewTransform transformForDistance(final double distanceMetres)
//    {
//        return ViewTransform.fitting(
//                CANVAS_WIDTH_PIXELS,
//                CANVAS_HEIGHT_PIXELS,
//                0.0,
//                0.0,
//                distanceMetres * VIEW_MARGIN_FACTOR);
//    }

    private void updateCircularReferenceLabel()
    {
        final double circularKilometres = controller.circularSpeedReferenceMetresPerSecond() / METRES_PER_SECOND_IN_KILOMETRES;
        circularReferenceLabel.setText(String.format(Locale.ROOT, "Circular speed (configured): %.1f km/s", circularKilometres));
    }

    private void updateSliderValueLabels()
    {
        massValueLabel.setText(String.format(Locale.ROOT, "%.2f Earth masses", massSlider.getValue()));
        radiusValueLabel.setText(String.format(Locale.ROOT, "%.2f Earth radii", radiusSlider.getValue()));
        distanceValueLabel.setText(String.format(Locale.ROOT, "%.2f AU", distanceSlider.getValue()));
        speedValueLabel.setText(String.format(Locale.ROOT, "%.1f km/s", speedSlider.getValue()));
    }
}