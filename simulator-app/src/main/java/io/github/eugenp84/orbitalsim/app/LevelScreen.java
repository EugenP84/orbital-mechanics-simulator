package io.github.eugenp84.orbitalsim.app;

import javafx.scene.Parent;

/**
 * A self-contained scenario screen the host can show, activate and deactivate uniformly.
 * <p>
 * Each level (Level 0, Level 1, …) builds its own control panel, canvas and {@link SimulationViewCore},
 * and exposes them here so the host can place the screen in the window and manage its animation-loop lifecycle when switching levels.
 * The host calls {@link #view()} once to obtain the screen's node, {@link #activate()} to render the initial state and start the loop,
 * and {@link #deactivate()} to stop the loop before the screen is hidden or the application shuts down.
 * <p>
 * {@code activate}/{@code deactivate} name the <em>presentation</em> lifecycle deliberately, to avoid colliding with "Start", which in this application means starting the learner's scenario attempt.
 *
 * @author Eugeniu PLATITA
 */
public interface LevelScreen
{
    /** Renders the initial state and starts the animation loop. */
    void activate();

    /** Stops the animation loop; safe to call when already stopped. */
    void deactivate();

    /** The screen's scene content (control panel and canvas), for the host to place in the window. */
    Parent view();
}