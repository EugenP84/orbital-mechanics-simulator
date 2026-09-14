package io.github.eugenp84.orbitalsim.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX entry point and host for the orbital mechanics simulator.
 * <p>
 * The host owns the window and the currently shown {@link LevelScreen}.
 * It is deliberately thin: each level builds its own controls, view core and animation loop,
 * and the host only places the active screen in the window and manages its presentation lifecycle.
 * For now it shows the Level 1 screen; a level switch and the Level 0 screen are added next.
 *
 * @author Eugeniu PLATITA
 */
public final class Main extends Application
{
    private static final String WINDOW_TITLE = "Orbital Mechanics Simulator — Level 1";

    private LevelScreen currentScreen;

    /**
     * Standard Java entry point.
     *
     * @param args command-line arguments, forwarded unchanged to JavaFX
     */
    public static void main(final String[] args)
    {
        launch(args);
    }

    /**
     * Shows the initial screen in the window and activates it.
     *
     * @param stage the primary stage supplied by JavaFX; never null
     */
    @Override
    public void start(final Stage stage)
    {
        currentScreen = new Level1Screen();

        stage.setScene(new Scene(currentScreen.view()));
        stage.setTitle(WINDOW_TITLE);
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
}