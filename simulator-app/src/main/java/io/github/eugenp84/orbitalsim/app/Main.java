package io.github.eugenp84.orbitalsim.app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.function.Supplier;

/**
 * JavaFX entry point and host for the orbital mechanics simulator.
 * <p>
 * The host owns the window, a thin level-switch bar, and the currently shown {@link LevelScreen}.
 * It is deliberately thin: each level builds its own controls, view core and animation loop.
 * the host only places the active screen in the window, drives its presentation lifecycle, and swaps screens on a level change.
 * Screens are rebuilt fresh on each switch — a screen is a disposable view, and no cross-level state is carried through it.
 * Any future learner-created-body continuity will be a model-layer concern, not screen state.
 * The two-button switch is a placeholder for a later side navigation panel.
 * <p>
 * A level switch is atomic in both directions: the replacement screen is constructed before the current one is stopped, and the navigation selection is restored if construction fails,
 * so either the new level is fully installed or the previous level remains both operational and selected.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application
{
    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator";
    private static final double SWITCH_BAR_SPACING_PIXELS = 8.0;
    private static final double SWITCH_BAR_PADDING_PIXELS = 6.0;

    private final BorderPane host = new BorderPane();

    private Stage stage;
    private LevelScreen currentScreen;
    private ToggleButton currentLevelButton;

    private HBox buildSwitchBar()
    {
        final ToggleGroup group = new ToggleGroup();

        final ToggleButton level0Button = new ToggleButton("L0");
        level0Button.setToggleGroup(group);
        level0Button.setOnAction(event -> switchTo(Level0Screen::new, "Level 0", level0Button));

        final ToggleButton level1Button = new ToggleButton("L1");
        level1Button.setToggleGroup(group);
        level1Button.setSelected(true);
        level1Button.setOnAction(event -> switchTo(Level1Screen::new, "Level 1", level1Button));

        currentLevelButton = level1Button;

        final HBox switchBar = new HBox(SWITCH_BAR_SPACING_PIXELS, level0Button, level1Button);
        switchBar.setAlignment(Pos.CENTER_LEFT);
        switchBar.setPadding(new Insets(SWITCH_BAR_PADDING_PIXELS));
        return switchBar;
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

    private void showLevel(final LevelScreen screen, final String levelName)
    {
        currentScreen = screen;
        host.setCenter(screen.view());
        stage.setTitle(WINDOW_TITLE + " — " + levelName);
        screen.activate();
    }

    /**
     * Shows the host window with the level switch and the initial (Level 1) screen.
     * The initial screen is activated only after the scene is set and the stage shown, matching the verified screen lifecycle.
     *
     * @param primaryStage the primary stage supplied by JavaFX; never null
     */
    @Override
    public void start(final Stage primaryStage)
    {
        this.stage = primaryStage;
        host.setTop(buildSwitchBar());

        currentScreen = new Level1Screen();
        host.setCenter(currentScreen.view());
        stage.setTitle(WINDOW_TITLE + " — Level 1");
        stage.setScene(new Scene(host));
        stage.show();
        currentScreen.activate();
    }

    /**
     * Deactivates the current screen when the application shuts down.
     */
    @Override
    public void stop()
    {
        if (currentScreen != null)
        {
            currentScreen.deactivate();
        }
    }

    private void switchTo(
            final Supplier<LevelScreen> screenFactory,
            final String levelName,
            final ToggleButton source)
    {
        // Keep one level selected at all times.
        // If the active toggle is clicked again, restore its selection and do not rebuild the current screen.
        if (!source.isSelected())
        {
            source.setSelected(true);
            return;
        }

        // The toggle selection has already moved to the requested level.
        // Construct the replacement before touching the current screen;
        // if construction fails, the current screen is left untouched and the previous selection is restored, so navigation and screen state agree.
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
    }
}