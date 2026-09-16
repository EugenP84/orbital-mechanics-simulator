package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.Level3Controller;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.PhysicalConstants;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.Level3Scenario;
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
 * The Level 3 (Gravitational Perturbation) screen: the learner varies the perturbing planet's mass
 * and initial stellar distance and observes how strongly it perturbs an Earth-like target orbit.
 * <p>
 * The generic presentation lives in {@link SimulationViewCore}. This screen supplies Level 3's two
 * configuration controls and the running maximum target eccentricity, which is the learner-facing
 * perturbation measure.
 * <p>
 * Level 3 uses a fixed three-AU viewport for every configuration and throughout every run. Keeping
 * the visual scale constant makes perturbation experiments directly comparable and avoids treating
 * the configured initial perturber distance as a bound on later motion. The view does not
 * automatically follow or fit bodies that move outside this extent.
 * <p>
 * The trajectory trail follows the fixed Earth-like target rather than the configurable perturber.
 * Configuration controls define an attempt and are therefore locked while the scenario is running
 * or paused. Reconfiguration creates a fresh attempt and clears the previous trajectory.
 *
 * @author Eugeniu PLATITA
 */
public final class Level3Screen implements LevelScreen
{
    private static final String INTEGRATOR_VALUE = "Velocity Verlet (fixed)";

    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double VIEW_RADIUS_METRES =
            3.0 * PhysicalConstants.ASTRONOMICAL_UNIT;

    private static final double JUPITER_MASS =
            317.8 * PhysicalConstants.EARTH_MASS;
    private static final double ASTRONOMICAL_UNIT =
            PhysicalConstants.ASTRONOMICAL_UNIT;

    private static final double MASS_STEP_JUPITERS = 0.1;
    private static final double DISTANCE_STEP_AU = 0.1;

    private static final double CONFIGURATION_EPSILON = 1.0e-9;

    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_ROW_SPACING_PIXELS = 6.0;
    private static final double CONTROL_PANEL_PADDING_PIXELS = 10.0;
    private static final double PARAMETER_SLIDER_WIDTH_PIXELS = 260.0;

    private static final int TRAIL_MAXIMUM_POINTS = 1_500;
    private static final double TRAIL_SEGMENT_DIVISOR = 240.0;

    private final Level3Controller controller;
    private final SimulationViewCore core;
    private final Parent simulationRegion;
    private final Parent controlsRegion;

    private Slider massSlider;
    private Slider distanceSlider;
    private Label massValueLabel;
    private Label distanceValueLabel;
    private Label maxEccentricityLabel;

    /** Builds the Level 3 screen: its controller, view core, and simulation and controls regions. */
    public Level3Screen()
    {
        this.controller = new Level3Controller(new Level3Scenario());

        final TrajectoryTrail trail = new TrajectoryTrail(
                TRAIL_MAXIMUM_POINTS,
                PhysicalConstants.ASTRONOMICAL_UNIT / TRAIL_SEGMENT_DIVISOR);

        final ViewTransform transform = ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                0.0,
                0.0,
                VIEW_RADIUS_METRES);

        final Canvas canvas =
                new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);

        this.core = new SimulationViewCore(
                controller,
                canvas,
                transform,
                trail,
                this::refreshLevelControls,
                Level3Screen::learnerTextFor,
                this::targetPositionOf,
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

    private Slider buildParameterSlider(
            final double min,
            final double max,
            final double value,
            final double step)
    {
        final Slider slider = new Slider(min, max, value);
        slider.setPrefWidth(PARAMETER_SLIDER_WIDTH_PIXELS);
        slider.setBlockIncrement(step);
        slider.setMajorTickUnit(step);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        return slider;
    }

    private VBox buildControlPanel()
    {
        massSlider = buildParameterSlider(
                controller.minPerturberMassKilograms() / JUPITER_MASS,
                controller.maxPerturberMassKilograms() / JUPITER_MASS,
                controller.configuredPerturberMassKilograms() / JUPITER_MASS,
                MASS_STEP_JUPITERS);

        distanceSlider = buildParameterSlider(
                controller.minPerturberDistanceMetres() / ASTRONOMICAL_UNIT,
                controller.maxPerturberDistanceMetres() / ASTRONOMICAL_UNIT,
                controller.configuredPerturberDistanceMetres() / ASTRONOMICAL_UNIT,
                DISTANCE_STEP_AU);

        massValueLabel = new Label();
        distanceValueLabel = new Label();
        maxEccentricityLabel = new Label();

        registerParameterListeners(massSlider);
        registerParameterListeners(distanceSlider);

        updateSliderValueLabels();
        updateMaxEccentricityLabel();

        final HBox buttonRow =
                new HBox(
                        CONTROL_BAR_SPACING_PIXELS,
                        core.runPauseButton(),
                        core.resetButton());
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlPanel = new VBox(
                CONTROL_ROW_SPACING_PIXELS,
                sectionHeading("LEVEL 3 — GRAVITATIONAL PERTURBATION"),
                new Label("Perturber mass"),
                massSlider,
                massValueLabel,
                new Label("Perturber distance"),
                distanceSlider,
                distanceValueLabel,
                labelledValue("Maximum target eccentricity", maxEccentricityLabel),
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

    private void commitConfiguration()
    {
        final double massKilograms =
                quantise(massSlider.getValue(), MASS_STEP_JUPITERS) * JUPITER_MASS;
        final double distanceMetres =
                quantise(distanceSlider.getValue(), DISTANCE_STEP_AU)
                        * ASTRONOMICAL_UNIT;

        if (!configurationChanged(massKilograms, distanceMetres))
        {
            return;
        }

        controller.configureAttempt(massKilograms, distanceMetres);

        final double configuredMassJupiters =
                controller.configuredPerturberMassKilograms() / JUPITER_MASS;
        final double configuredDistanceAu =
                controller.configuredPerturberDistanceMetres() / ASTRONOMICAL_UNIT;

        if (Double.compare(massSlider.getValue(), configuredMassJupiters) != 0)
        {
            massSlider.setValue(configuredMassJupiters);
        }
        if (Double.compare(distanceSlider.getValue(), configuredDistanceAu) != 0)
        {
            distanceSlider.setValue(configuredDistanceAu);
        }

        core.afterAttemptReplaced();
        updateSliderValueLabels();
        updateMaxEccentricityLabel();
    }

    private boolean configurationChanged(
            final double massKilograms,
            final double distanceMetres)
    {
        final double selectedMassJupiters = massKilograms / JUPITER_MASS;
        final double configuredMassJupiters =
                controller.configuredPerturberMassKilograms() / JUPITER_MASS;

        final double selectedDistanceAu = distanceMetres / ASTRONOMICAL_UNIT;
        final double configuredDistanceAu =
                controller.configuredPerturberDistanceMetres() / ASTRONOMICAL_UNIT;

        return Math.abs(selectedMassJupiters - configuredMassJupiters)
                > CONFIGURATION_EPSILON
                || Math.abs(selectedDistanceAu - configuredDistanceAu)
                > CONFIGURATION_EPSILON;
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
            case POLICY_COMPLETION -> "observation period completed";
            case POLICY_FAILURE -> "physical contact occurred";
            case ASSESSMENT_INVALID -> "the experiment could not be assessed reliably";
            case SIMULATION_BREAKDOWN -> "the simulation became numerically unstable";
        };
    }

    private static boolean parametersSelectable(final ScenarioLifecycleState state)
    {
        return state == ScenarioLifecycleState.NOT_STARTED
                || state == ScenarioLifecycleState.COMPLETED
                || state == ScenarioLifecycleState.FAILED;
    }

    private static double quantise(final double value, final double step)
    {
        return Math.round(value / step) * step;
    }

    private void refreshLevelControls()
    {
        final boolean selectable = parametersSelectable(controller.state());
        massSlider.setDisable(!selectable);
        distanceSlider.setDisable(!selectable);
        updateMaxEccentricityLabel();
    }

    private void registerParameterListeners(final Slider slider)
    {
        slider.valueProperty().addListener((observable, oldValue, newValue) ->
        {
            updateSliderValueLabels();
            if (!slider.isValueChanging())
            {
                commitConfiguration();
            }
        });

        slider.valueChangingProperty().addListener(
                (observable, wasChanging, isChanging) ->
                {
                    if (wasChanging && !isChanging)
                    {
                        commitConfiguration();
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

    private Optional<Vector3D> targetPositionOf(final SimulationSnapshot snapshot)
    {
        return snapshot.bodies().stream()
                .filter(body -> body.definition().id().equals(controller.targetId()))
                .map(body -> body.state().position())
                .findFirst();
    }

    private void updateMaxEccentricityLabel()
    {
        maxEccentricityLabel.setText(
                String.format(
                        Locale.ROOT,
                        "%.4f",
                        controller.maxEccentricity()));
    }

    private void updateSliderValueLabels()
    {
        massValueLabel.setText(
                String.format(
                        Locale.ROOT,
                        "%.1f Jupiter masses",
                        quantise(massSlider.getValue(), MASS_STEP_JUPITERS)));

        distanceValueLabel.setText(
                String.format(
                        Locale.ROOT,
                        "%.1f AU",
                        quantise(distanceSlider.getValue(), DISTANCE_STEP_AU)));
    }
}