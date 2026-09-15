package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.app.controller.ScenarioControls;
import io.github.eugenp84.orbitalsim.app.rendering.SimulationRenderer;
import io.github.eugenp84.orbitalsim.app.rendering.TrajectoryTrail;
import io.github.eugenp84.orbitalsim.app.rendering.ViewTransform;
import io.github.eugenp84.orbitalsim.core.math.Vector3D;
import io.github.eugenp84.orbitalsim.core.simulation.SimulationSnapshot;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioLifecycleState;
import io.github.eugenp84.orbitalsim.learning.scenario.ScenarioTerminationReason;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.paint.Color;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * The level-agnostic presentation core shared by every scenario screen: the animation loop, the
 * trajectory trail, the rendering, and the generic controls (run/pause, reset, time rate, status,
 * integrator, elapsed) that behave identically for every level.
 * <p>
 * It drives its scenario only through the {@link ScenarioControls} surface, so it has no knowledge of
 * orbital speed, hints, or planet parameters. The generic status word and colour are level-agnostic,
 * but the learner-facing interpretation of a termination reason, the selection of which body the trail
 * follows, and the integrator label text are supplied by the screen.
 * The core owns and wires its generic control widgets and exposes them individually — as bare values, without captions —
 * so each screen decides how to caption and arrange them; the core never knows how a screen lays out its panel.
 * <p>
 * The view transform is replaceable ({@link #setTransform(ViewTransform)}) so a screen whose physical extent changes with configuration can re-frame the view without rebuilding the core.
 * Advancement and rendering run on the JavaFX Application Thread; the loop runs continuously and advances only while running.
 *
 * @author Eugeniu PLATITA
 */
public final class SimulationViewCore
{
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    // A stalled frame, debugger or GC pause must not become one huge simulated-time request. Excess
    // wall-clock time is discarded, not replayed. A presentation safeguard, not a physics rule.
    private static final double MAX_FRAME_DELTA_SECONDS = 1.0 / 30.0;

    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final double MIN_TIME_RATE_DAYS_PER_SECOND = 1.0;
    private static final double MAX_TIME_RATE_DAYS_PER_SECOND = 60.0;

    private static final double TIME_RATE_SLIDER_WIDTH_PIXELS = 260.0;

    private final ScenarioControls controls;
    private final GraphicsContext graphics;
    private final TrajectoryTrail trail;
    private final SimulationRenderer renderer;
    private final Runnable levelControlsRefresh;
    private final Function<ScenarioTerminationReason, String> terminationText;
    private final Function<SimulationSnapshot, Optional<Vector3D>> trailPosition;

    private final Button runPauseButton = new Button();
    private final Button resetButton = new Button("Reset");
    private final Label stateLabel = new Label();
    private final Label elapsedLabel = new Label();
    private final Label integratorLabel;
    private final Label timeRateValueLabel = new Label();
    private final Slider timeRateSlider;

    private ViewTransform transform;
    private AnimationTimer animationTimer;

    /**
     * Creates a presentation core driving the given controls, rendering onto the given canvas through the given transform,
     * recording motion into the given trail, refreshing level-specific controls
     * through the given callback, interpreting termination reasons through the given text mapping,
     * selecting the trailed body through the given position function, and labelling the integrator with the given bare value text (the screen supplies its caption).
     *
     * @param controls             the level-agnostic surface driving the scenario; never null
     * @param canvas               the canvas to render onto; never null
     * @param transform            the initial metre-to-pixel mapping for the view; never null
     * @param trail                the trajectory trail to record and draw; never null
     * @param levelControlsRefresh run as part of every generic control refresh; never null
     * @param terminationText      maps a termination reason to the screen's learner-facing text; never null
     * @param trailPosition        selects the trailed body's position from a snapshot, or empty; never null
     * @param integratorValue      the integrator value text (without caption); never null or blank
     */
    public SimulationViewCore(
            final ScenarioControls controls,
            final Canvas canvas,
            final ViewTransform transform,
            final TrajectoryTrail trail,
            final Runnable levelControlsRefresh,
            final Function<ScenarioTerminationReason, String> terminationText,
            final Function<SimulationSnapshot, Optional<Vector3D>> trailPosition,
            final String integratorValue)
    {
        this.controls = Objects.requireNonNull(controls, "controls must not be null");
        Objects.requireNonNull(canvas, "canvas must not be null");
        this.graphics = canvas.getGraphicsContext2D();
        this.transform = Objects.requireNonNull(transform, "transform must not be null");
        this.trail = Objects.requireNonNull(trail, "trail must not be null");
        this.levelControlsRefresh = Objects.requireNonNull(levelControlsRefresh, "levelControlsRefresh must not be null");
        this.terminationText = Objects.requireNonNull(terminationText, "terminationText must not be null");
        this.trailPosition = Objects.requireNonNull(trailPosition, "trailPosition must not be null");
        Objects.requireNonNull(integratorValue, "integratorValue must not be null");
        if (integratorValue.isBlank())
        {
            throw new IllegalArgumentException("integratorValue must not be blank");
        }
        this.integratorLabel = new Label(integratorValue);
        this.renderer = new SimulationRenderer();

        runPauseButton.setOnAction(event ->
        {
            controls.toggleRun();
            refreshControls();
        });
        resetButton.setOnAction(event ->
        {
            controls.reset();
            afterAttemptReplaced();
        });

        final double initialDaysPerSecond = controls.simulatedSecondsPerRealSecond() / SECONDS_PER_DAY;
        timeRateSlider = new Slider(MIN_TIME_RATE_DAYS_PER_SECOND, MAX_TIME_RATE_DAYS_PER_SECOND, initialDaysPerSecond);
        timeRateSlider.setPrefWidth(TIME_RATE_SLIDER_WIDTH_PIXELS);
        timeRateSlider.valueProperty().addListener((observable, oldValue, newValue) ->
        {
            final double daysPerSecond = newValue.doubleValue();
            controls.setSimulatedSecondsPerRealSecond(daysPerSecond * SECONDS_PER_DAY);
            timeRateValueLabel.setText(formatTimeRate(daysPerSecond));
        });
        timeRateValueLabel.setText(formatTimeRate(initialDaysPerSecond));
    }

    /**
     * Makes an attempt replacement immediately visible: clears the trail, renders the new committed state, and refreshes the controls. A screen that also changed the view extent calls
     * {@link #setTransform(ViewTransform)} before this.
     */
    public void afterAttemptReplaced()
    {
        trail.clear();
        renderCurrent();
        refreshControls();
    }

    private AnimationTimer createTimer()
    {
        return new AnimationTimer()
        {
            private long previousFrameNanos;

            @Override
            public void handle(final long now)
            {
                if (previousFrameNanos == 0L)
                {
                    previousFrameNanos = now;
                    return;
                }

                final long elapsedNanos = now - previousFrameNanos;
                previousFrameNanos = now;
                if (elapsedNanos <= 0L)
                {
                    return;
                }

                final double elapsedWallClockSeconds = elapsedNanos / NANOS_PER_SECOND;
                final double boundedWallClockSeconds = Math.min(elapsedWallClockSeconds, MAX_FRAME_DELTA_SECONDS);

                final boolean wasRunning = controls.state() == ScenarioLifecycleState.RUNNING;

                if (wasRunning)
                {
                    trailPosition.apply(controls.snapshot()).ifPresent(trail::record);
                }

                controls.advanceByWallClock(boundedWallClockSeconds);

                final SimulationSnapshot snapshot = controls.snapshot();
                if (wasRunning)
                {
                    trailPosition.apply(snapshot).ifPresent(trail::record);
                }

                renderer.render(graphics, snapshot, transform, trail.points());
                refreshControls();
            }
        };
    }

    /** The elapsed-time readout label, wired and updated by the core, for a screen to place. */
    public Label elapsedLabel()
    {
        return elapsedLabel;
    }

    private static String elapsedTextFor(final OptionalDouble elapsedSeconds)
    {
        if (elapsedSeconds.isEmpty())
        {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f days", elapsedSeconds.getAsDouble() / SECONDS_PER_DAY);
    }

    private static String formatTimeRate(final double daysPerSecond)
    {
        return String.format(Locale.ROOT, "%.0f days / second", daysPerSecond);
    }

    /**
     * Renders the current committed state and refreshes the controls once, before the loop starts,
     * so the initial view is shown immediately rather than after the first animation frame.
     */
    public void initialiseView()
    {
        renderCurrent();
        refreshControls();
    }

    /** The integrator value label (no caption), for a screen to place under its own caption. */
    public Label integratorLabel()
    {
        return integratorLabel;
    }

    /** Updates the generic controls from the current state, then runs the level-specific refresh. */
    public void refreshControls()
    {
        final ScenarioLifecycleState current = controls.state();
        runPauseButton.setText(runPauseLabelFor(current));
        runPauseButton.setDisable(!controls.canToggleRun());
        stateLabel.setText(statusTextFor(current, controls.terminationReason()));
        stateLabel.setTextFill(statusColourFor(current));
        elapsedLabel.setText(elapsedTextFor(controls.elapsedSimulatedSeconds()));
        levelControlsRefresh.run();
    }

    /** Renders the current committed state (with the current trail). */
    public void renderCurrent()
    {
        renderer.render(graphics, controls.snapshot(), transform, trail.points());
    }

    /** The core-owned reset button, for a screen to place in its layout. */
    public Button resetButton()
    {
        return resetButton;
    }

    /** The core-owned run/pause button, for a screen to place in its layout. */
    public Button runPauseButton()
    {
        return runPauseButton;
    }

    private static String runPauseLabelFor(final ScenarioLifecycleState state)
    {
        return switch (state)
        {
            case NOT_STARTED, COMPLETED, FAILED -> "Start";
            case RUNNING -> "Pause";
            case PAUSED -> "Resume";
        };
    }

    /**
     * Replaces the view transform, so a screen can re-frame the view when its physical extent changes with configuration.
     * Choosing the extent is a level-specific responsibility.
     *
     * @param newTransform the new metre-to-pixel mapping; never null
     */
    public void setTransform(final ViewTransform newTransform)
    {
        this.transform = Objects.requireNonNull(newTransform, "newTransform must not be null");
    }

    /**
     * Starts the animation loop.
     * <p>
     * Each {@code startLoop()} after a {@link #stopLoop()} creates a fresh timer whose first frame establishes a new wall-clock baseline and performs no advancement,
     * so a restart cannot generate a large first delta from time elapsed while stopped.
     *
     * @throws IllegalStateException if the loop is already started
     */
    public void startLoop()
    {
        if (animationTimer != null)
        {
            throw new IllegalStateException("animation loop is already started");
        }
        animationTimer = createTimer();
        animationTimer.start();
    }

    private static Color statusColourFor(final ScenarioLifecycleState state)
    {
        return switch (state)
        {
            case COMPLETED -> Color.web("#4caf50");
            case FAILED -> Color.web("#e05252");
            case NOT_STARTED, RUNNING, PAUSED -> Color.web("#c7ccd6");
        };
    }

    /** The status readout label, wired and updated (text and colour) by the core, for a screen to place. */
    public Label statusLabel()
    {
        return stateLabel;
    }

    private String statusTextFor(
            final ScenarioLifecycleState state,
            final Optional<ScenarioTerminationReason> reason)
    {
        if (reason.isEmpty())
        {
            return statusWordFor(state);
        }
        return statusWordFor(state) + " — " + terminationText.apply(reason.get());
    }

    private static String statusWordFor(final ScenarioLifecycleState state)
    {
        return switch (state)
        {
            case NOT_STARTED -> "Ready";
            case RUNNING -> "Running";
            case PAUSED -> "Paused";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
        };
    }

    /** Stops the animation loop, if running, and permits a later restart. */
    public void stopLoop()
    {
        if (animationTimer != null)
        {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    /** The time-rate slider, wired by the core, for a screen to place. */
    public Slider timeRateControl()
    {
        return timeRateSlider;
    }

    /** The time-rate value label, updated by the core's slider listener, for a screen to place. */
    public Label timeRateValueLabel()
    {
        return timeRateValueLabel;
    }
}