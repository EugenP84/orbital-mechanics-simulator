package io.github.eugenp84.orbitalsim.app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Supplier;

/**
 * JavaFX entry point and host for the orbital mechanics simulator.
 * <p>
 * The host owns the window shell and persistent level navigation.
 * Each {@link LevelScreen} supplies a simulation region and a controls region;
 * the host places the simulation region in the centre and, on the right, a vertical panel holding the persistent navigation above the screen's controls.
 * The host composes JavaFX regions and manages screen lifecycle; it knows nothing of how a screen builds its simulation or controls.
 * <p>
 * Screens are rebuilt fresh on each switch. The replacement screen is constructed before the current screen is deactivated;
 * if construction fails, the current screen remains operational and the previous navigation selection is restored.
 * Failures during subsequent lifecycle or installation operations propagate.
 * Cross-level continuity (e.g. a learner-created body carried forward) is a future model-layer concern, not screen state.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application
{
    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator (developed by Eugeniu Platita)";
    private static final double NAV_BAR_SPACING_PIXELS = 8.0;
    private static final double SIDE_PANEL_SPACING_PIXELS = 6.0;
    private static final double SIDE_PANEL_PADDING_PIXELS = 6.0;
    private static final double SIDE_PANEL_WIDTH_PIXELS = 300.0;
    private final BorderPane host = new BorderPane();
    private final VBox sidePanel = new VBox(SIDE_PANEL_SPACING_PIXELS);

    private Stage stage;
    private LevelScreen currentScreen;
    private ToggleButton currentLevelButton;
    private HBox navigationBar;

    private HBox buildNavigationBar()
    {
        final ToggleGroup group = new ToggleGroup();

        final ToggleButton level0Button = new ToggleButton("L0");
        level0Button.setToggleGroup(group);
        level0Button.setTooltip(new Tooltip("Learning Scenario L0 - Create a Planet"));
        level0Button.setOnAction(
                event -> switchTo(Level0Screen::new,"Learning Scenario L0 - Create a Planet", level0Button));

        final ToggleButton level1Button = new ToggleButton("L1");
        level1Button.setToggleGroup(group);
        level1Button.setSelected(true);
        level1Button.setTooltip(new Tooltip("Learning Scenario L1 - Stable Orbit"));
        level1Button.setOnAction(
                event -> switchTo(
                        Level1Screen::new,
                        "Learning Scenario L1 - Stable Orbit",
                        level1Button));

        final ToggleButton level2Button = new ToggleButton("L2");
        level2Button.setToggleGroup(group);
        level2Button.setTooltip(new Tooltip("Learning Scenario L2 - Planet Comparison"));
        //level2Button.setDisable(true);

        final ToggleButton level3Button = new ToggleButton("L3");
        level3Button.setToggleGroup(group);
        level3Button.setTooltip(new Tooltip("Learning Scenario L3 - Gravitational Perturbation"));
        level3Button.setOnAction(
                event -> switchTo(
                        Level3Screen::new,
                        "Learning Scenario L3 - Gravitational Perturbation",
                        level3Button));

        final ToggleButton level4Button = new ToggleButton("L4");
        level4Button.setToggleGroup(group);
        level4Button.setTooltip(new Tooltip("Learning Scenario L4 - Near-Earth Object Observation"));
        level4Button.setOnAction(
                event -> switchTo(
                        Level4Screen::new,
                        "Learning Scenario L4 - Near-Earth Object Observation",
                        level4Button));

        final ToggleButton comparisonButton = new ToggleButton("Compare");
        comparisonButton.setToggleGroup(group);
        comparisonButton.setTooltip(new Tooltip("Numerical Integrator Comparison"));
        comparisonButton.setOnAction(
                event -> switchTo(
                        IntegratorComparisonScreen::new,
                        "Numerical Integrator Comparison",
                        comparisonButton));

        currentLevelButton = level1Button;

        final HBox bar = new HBox(
                NAV_BAR_SPACING_PIXELS,
                level0Button,
                level1Button,
                level2Button,
                level3Button,
                level4Button,
                comparisonButton);

        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /**
     * Standard Java entry point.
     *
     * @param args command-line arguments, forwarded unchanged to JavaFX
     */
    public static void main(final String[] args) {
        launch(args);
    }

    private void showLevel(final LevelScreen screen, final String levelName)
    {
        currentScreen = screen;
        host.setCenter(screen.simulationView());
        sidePanel.getChildren().setAll(navigationBar, new Separator(), screen.controlsView());
        stage.setTitle(WINDOW_TITLE + " | " + levelName);
        screen.activate();
    }

    /**
     * Shows the host window with the side panel and the initial (Level 1) screen.
     * The initial screen is activated only after the scene is set and the stage shown, matching the verified screen lifecycle.
     *
     * @param primaryStage the primary stage supplied by JavaFX; never null
     */
    @Override
    public void start(final Stage primaryStage)
    {
        this.stage = primaryStage;
        this.navigationBar = buildNavigationBar();
        sidePanel.setPadding(new Insets(SIDE_PANEL_PADDING_PIXELS));
        sidePanel.setPrefWidth(SIDE_PANEL_WIDTH_PIXELS);
        host.setRight(sidePanel);

        currentScreen = new Level1Screen();
        host.setCenter(currentScreen.simulationView());
        sidePanel.getChildren().setAll(navigationBar, new Separator(), currentScreen.controlsView());
        stage.setTitle(WINDOW_TITLE + " | Learning Scenario L1 - Stable Orbit");
        stage.setScene(new Scene(host));
        stage.show();
        currentScreen.activate();
    }

    /**
     * Deactivates the current screen when the application shuts down.
     */
    @Override
    public void stop() {
        if (currentScreen != null)
        {
            currentScreen.deactivate();
        }
    }

    private void switchTo(final Supplier<LevelScreen> screenFactory, final String levelName, final ToggleButton source)
    {
        if (!source.isSelected())
        {
            source.setSelected(true);
            return;
        }

        boolean installed = false;
        try
        {
            final LevelScreen nextScreen = screenFactory.get();
            currentScreen.deactivate();
            showLevel(nextScreen, levelName);
            currentLevelButton = source;
            installed = true;
        }
        finally
        {
            if (!installed)
            {
                currentLevelButton.setSelected(true);
            }
        }
    }//----------- end of switchTo() ==================================
}