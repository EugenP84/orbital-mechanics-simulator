package io.github.eugenp84.orbitalsim.app;

import javafx.scene.Parent;

/**
 * A self-contained scenario screen the host can show, activate and deactivate uniformly.
 * <p>
 * Each level (Level 0, Level 1, …) builds its own controller, view core and controls, and exposes two
 * regions the host composes into the window shell: a simulation region (the canvas and any future
 * overlays around it) and a level-specific controls region (the parameter controls and readouts). The
 * host places the simulation region in the centre and the controls region in the right-hand panel
 * beneath the persistent level navigation, without knowing anything about how either is built.
 * <p>
 * The regions are returned as {@link Parent} rather than a bare canvas, so a screen may later wrap its
 * canvas with overlays or additional views without changing the host. {@code activate}/{@code
 * deactivate} name the presentation lifecycle deliberately, to avoid colliding with "Start", which in
 * this application means starting the learner's scenario attempt.
 *
 * @author Eugeniu PLATITA
 */
public interface LevelScreen
{
    /** Renders the initial state and starts the animation loop. */
    void activate();

    /** The level-specific controls region, for the host to place in the right-hand panel. */
    Parent controlsView();

    /** Stops the animation loop; safe to call when already stopped. */
    void deactivate();

    /** The simulation region (canvas and any overlays), for the host to place in the centre. */
    Parent simulationView();
}