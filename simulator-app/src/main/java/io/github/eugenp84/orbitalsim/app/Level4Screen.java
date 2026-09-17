package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.composition.Level4AttemptFactory;
import io.github.eugenp84.orbitalsim.app.controller.Level4Controller;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.Locale;
import java.util.Optional;

/**
 * The Level 4 (Real NEO Close Approach) screen for the canonical 2029 Apophis encounter.
 * <p>
 * Physics remains in the authoritative heliocentric frame. Presentation alone follows Earth with a fixed 600,000 km radius so the close approach is visible without changing propagated states.
 * The screen reports sampled encounter measurements; it does not use CNEOS reference values as progression input and does not introduce hints or pedagogical failure conditions.
 *
 * @author Eugeniu PLATITA
 */
public final class Level4Screen implements LevelScreen
{
    private static final String INTEGRATOR_VALUE = "Velocity Verlet";

    private static final double CANVAS_HEIGHT_PIXELS = 700.0;
    private static final double CANVAS_WIDTH_PIXELS = 900.0;
    private static final double KILOMETRES_PER_METRE = 0.001;
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double MIN_TIME_RATE_SECONDS_PER_SECOND = 3_600.0;
    private static final double MAX_TIME_RATE_SECONDS_PER_SECOND = 86_400.0;
    private static final double DEFAULT_TIME_RATE_SECONDS_PER_SECOND = 21_600.0;
    private static final double VIEW_RADIUS_METRES = 600_000_000.0;

    private static final double CONTROL_BAR_SPACING_PIXELS = 10.0;
    private static final double CONTROL_PANEL_PADDING_PIXELS = 10.0;
    private static final double CONTROL_ROW_SPACING_PIXELS = 6.0;

    // Trails are deliberately disabled for this first L4 view. Existing trails store absolute
    // heliocentric history; drawing that history through a camera that follows the current Earth
    // position would not represent an Earth-relative encounter path. A relative-trail representation
    // should be introduced separately if the learning value justifies it.
    private static final int TRAIL_MAXIMUM_POINTS = 1;
    private static final double TRAIL_MINIMUM_SEGMENT_METRES = 1.0;
    private final Level4Controller controller;
    private final SimulationViewCore core;
    private final Parent controlsRegion;
    private final Parent simulationRegion;
    private Label currentDistanceLabel;
    private Label minimumDistanceLabel;
    private Label minimumTimeLabel;
    private Label relativeSpeedLabel;

    /** Builds the canonical Level 4 Apophis close-approach screen. */
    public Level4Screen()
    {
        this.controller = new Level4Controller(new Level4AttemptFactory());
        final Canvas canvas = new Canvas(CANVAS_WIDTH_PIXELS, CANVAS_HEIGHT_PIXELS);
        final ViewTransform initialTransform = earthCentredTransform(controller.snapshot());
        final TrajectoryTrail unusedTrail = new TrajectoryTrail(
                TRAIL_MAXIMUM_POINTS,
                TRAIL_MINIMUM_SEGMENT_METRES);

        this.core = new SimulationViewCore(
                controller,
                canvas,
                initialTransform,
                unusedTrail,
                this::refreshLevelControls,
                Level4Screen::learnerTextFor,
                snapshot -> Optional.empty(),
                INTEGRATOR_VALUE);
        core.setTransformProvider(this::earthCentredTransform);
        core.configureTimeRate(
                MIN_TIME_RATE_SECONDS_PER_SECOND,
                MAX_TIME_RATE_SECONDS_PER_SECOND,
                DEFAULT_TIME_RATE_SECONDS_PER_SECOND);

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
        currentDistanceLabel = new Label();
        minimumDistanceLabel = new Label();
        minimumTimeLabel = new Label();
        relativeSpeedLabel = new Label();
        updateEncounterLabels();

        final HBox buttonRow = new HBox(
                CONTROL_BAR_SPACING_PIXELS,
                core.runPauseButton(),
                core.resetButton());
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        final VBox controlPanel = new VBox(
                CONTROL_ROW_SPACING_PIXELS,
                sectionHeading("LEVEL 4 — REAL NEO CLOSE APPROACH"),
                new Label("99942 Apophis — Earth encounter, April 2029"),
                new Label("View: Earth-centred, fixed 600,000 km radius"),
                labelledValue("Current Earth–Apophis distance", currentDistanceLabel),
                labelledValue("Minimum sampled distance", minimumDistanceLabel),
                labelledValue("Time of sampled minimum", minimumTimeLabel),
                labelledValue("Relative speed at sampled minimum", relativeSpeedLabel),
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

    private ViewTransform earthCentredTransform(final SimulationSnapshot snapshot)
    {
        final Vector3D earthPosition = bodyPosition(snapshot, controller.earthId())
                .orElseThrow(() -> new IllegalStateException("snapshot missing Earth body"));

        return ViewTransform.fitting(
                CANVAS_WIDTH_PIXELS,
                CANVAS_HEIGHT_PIXELS,
                earthPosition.x(),
                earthPosition.y(),
                VIEW_RADIUS_METRES);
    }

    private static String formatDistanceKilometres(final double metres)
    {
        return String.format(Locale.ROOT, "%,.0f km", metres * KILOMETRES_PER_METRE);
    }

    private static String formatElapsedDays(final double seconds)
    {
        return String.format(Locale.ROOT, "%.3f days", seconds / SECONDS_PER_DAY);
    }

    private static String formatSpeedKilometresPerSecond(final double metresPerSecond)
    {
        return String.format(Locale.ROOT,"%.3f km/s", metresPerSecond * KILOMETRES_PER_METRE);
    }

    private static Optional<Vector3D> bodyPosition(final SimulationSnapshot snapshot, final String bodyId) {
        return snapshot.bodies().stream()
                .filter(body -> body.definition().id().equals(bodyId))
                .map(body -> body.state().position())
                .findFirst();
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
            case POLICY_FAILURE -> "the experiment ended unexpectedly";
            case ASSESSMENT_INVALID -> "the experiment could not be assessed reliably";
            case SIMULATION_BREAKDOWN -> "the simulation became numerically unstable";
        };
    }

    private void refreshLevelControls() {
        updateEncounterLabels();
    }

    private static Label sectionHeading(final String text)
    {
        final Label heading = new Label(text);
        heading.setStyle("-fx-font-weight: bold;");
        return heading;
    }

    @Override
    public Parent simulationView() {
        return simulationRegion;
    }

    private void updateEncounterLabels()
    {
        currentDistanceLabel.setText(formatDistanceKilometres(controller.currentDistanceMetres()));
        minimumDistanceLabel.setText(formatDistanceKilometres(controller.minimumDistanceMetres()));
        minimumTimeLabel.setText(formatElapsedDays(controller.minimumDistanceElapsedSeconds()));
        relativeSpeedLabel.setText(formatSpeedKilometresPerSecond(controller.relativeSpeedAtMinimumMetresPerSecond()));
    }
}
