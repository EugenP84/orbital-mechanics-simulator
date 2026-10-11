# ADR-010: JavaFX Presentation Architecture and Multi-Level Shell

**Status:** Accepted  
**Date:** 15.09.2026  
**Modules:** simulator-app (primary); uses simulator-learning and simulator-core through their public interfaces  
**Related:** Project Proposal §2.1 (System Architecture), §2.3 (Progressive Learning Scenarios); ADR-001 (module boundaries), ADR-005 §8 (numerical timestep and presentation speed), ADR-006 (engine execution model and publication cadence), ADR-008 (learning scenario architecture; §4 sole advancement, §9 hint observer)

## Context

This ADR defines the JavaFX presentation structure shared by the learning
levels. It is based on the two levels that exist at the time of the decision,
L0 and L1.

Three constraints shape the design:

1. The levels are structurally different. L0 configures four physical
   parameters and has no assessment. L1 configures one speed fraction, is
   assessed and shows adaptive hints. L2–L4 differ again. A single monolithic
   UI would have to special-case every level.
2. Most presentation behaviour is common. Every level needs the same
   run/pause/reset loop, conversion from wall-clock time to simulated time,
   trajectory trail, rendering, and status and elapsed-time readouts.
   Duplicating these in each of five levels would create repeated code to
   maintain.
3. The lifecycle and cadence boundaries of earlier ADRs apply in the UI.
   Advancement must go through the session (ADR-008 §4), presentation speed
   must never alter the numerical timestep (ADR-005 §8), and the JavaFX frame
   rate must not replace the learning observation cadence.

## Decision Drivers

- Support different levels through one shared shell, without duplicating the
  loop, trail, rendering and generic controls in each level.
- Keep JavaFX out of the learning and core layers, and keep the boundaries of
  those layers intact (sole advancement, three separate cadences, sealed
  session).
- Extract shared code only when a second consumer shows that it is shared.
- Keep the control panel legible on small (14-inch) screens as levels add
  controls.

## Decision

### 1. A shared, level-agnostic presentation core

`SimulationViewCore` owns what is common to every level: the `AnimationTimer`
loop, the `TrajectoryTrail`, the `SimulationRenderer` and the generic control
widgets (run/pause, reset, time-rate slider, status, integrator and elapsed
time). It drives its scenario only through the level-agnostic
`ScenarioControls` interface and has no knowledge of orbital speed, hints or
planet parameters. Advancement and rendering run on the JavaFX Application
Thread. The loop runs continuously and advances the scenario only while it is
running.

Three concerns that vary by level are injected: the learner-facing
interpretation of a termination reason, the choice of the body the trail
follows, and the integrator label text. Without injection, the core would
display "stable orbit achieved" when Level 0 completes.

The core exposes its generic widgets individually, without captions, so that
each screen decides how to caption and arrange them. The view transform is
replaceable, so a level whose physical extent changes with configuration (L0's
orbital distance) can re-frame the view without rebuilding the core.

### 2. Level-agnostic control surface, per-level controllers

`ScenarioControls` is the interface used by the core and the generic widgets.
It covers lifecycle transitions, wall-clock advancement, presentation speed,
the snapshot, the lifecycle state, the termination reason and reset.
`SimulationController` (L1) and `Level0Controller` (L0) both implement it, and
each adds only its level-specific configuration and pedagogy:

- L1: speed fraction, hints and refusal of configurations that cannot be
  assessed.
- L0: four planet parameters, a circular-speed reference and no hints.

The lifecycle and presentation behaviour shared by both controllers is in
`ScenarioPlayer`.

The same interface drives L0's unconditional, hint-free scenario and L1's
history-dependent assessed scenario.

### 3. Host / screen composition shell

`Main` is the `Application` host. It owns the window, the persistent level
navigation and the screen lifecycle, and it does not depend on how a screen
builds its simulation or controls. Each level is a `LevelScreen` that owns its
controller, view core and controls. A screen exposes two regions for the host
to compose:

- `simulationView()` is the canvas region. It is returned as a `Parent`, so
  that overlays can be added without changing the host.
- `controlsView()` holds the level-specific controls.

The host places the simulation region in the centre and a vertical panel on
the right, with the navigation above the active screen's controls.

Screens are rebuilt on each switch, and a screen carries no cross-level state.
The replacement screen is constructed before the current screen is
deactivated. If construction fails, the current screen stays operational. If
the switch does not complete, the previous navigation selection is restored.
Failures in later lifecycle or installation operations propagate, and no
screen rollback is attempted.

### 4. Vertical side-panel layout

Controls are placed in a vertical right-hand panel with a preferred width of
about 300 px. Horizontal top bars were tried first, and their
caption-and-value rows were truncated with an ellipsis on a 14-inch screen.
Each control and readout is stacked vertically with its caption on its own
line, and long values such as the termination status are wrapped. The
preferred width belongs to the host shell, so individual screens do not
redefine it.

### 5. Lifecycle and cadence invariants

- Sole advancement (ADR-008 §4): the loop advances only through
  `ScenarioControls` and the session. The application never drives the engine
  directly.
- Three separate cadences: the numerical timestep is fixed in the engine, the
  learning observation cadence is set by the session's chunking, and the
  presentation frame rate drives only rendering. The time-rate control scales
  the requested simulated time (ADR-005 §8) and never the timestep. The
  wall-clock delta of a stalled frame is clamped, so it cannot cause a large
  jump in simulated time.
- Hint observation (ADR-008 §9): the UI reads hints through the controller.
  The hint engine observes committed states at the learning cadence.

### 6. Design patterns

The proposal names State, Template Method and Observer. The presentation
architecture keeps Observer for learning feedback: the L1 hint engine receives
committed simulation states through `SimulationListener`, and the UI reads the
resulting hint through the controller. The JavaFX `AnimationTimer` is the
presentation scheduler. It requests advancement and rendering from wall-clock
time and is not a `SimulationListener`.

Level-varying presentation behaviour is supplied by injection, in the manner
of the Strategy pattern. This covers termination-text interpretation,
trail-body selection and the integrator display value.

No State-pattern object graph and no Template Method scenario superclass are
introduced for the UI. The `LevelScreen` interface with per-level composition
is sufficient for the two existing levels.

## Consequences

### Positive

- One shared core and shell drive two structurally different levels (L0 and
  L1) with no duplicated loop, trail, rendering or status code. The
  differences are confined to each screen and controller.
- The layer boundaries of earlier ADRs are preserved. JavaFX is confined to
  `simulator-app`, the session is the only advancement path, and the three
  cadences stay separate.
- The vertical side panel has room for the larger control sets of L3 and L4.

### Negative / Accepted Trade-offs

- `SimulationViewCore` has a wide constructor (controls, canvas, transform,
  trail, refresh callback, two injected functions and the integrator value).
  No builder is provided.
- A failed level switch is safe only if the failure occurs during
  construction. A failure during a screen's own activation is treated as a
  programming defect and propagates.
- Two small UI helpers (`sectionHeading` and `labelledValue`) are duplicated
  across the two screens. A shared utility is not extracted yet.

## Deferred Work

- A `SidePanel` component (a persistent navigation region and a replaceable
  content region) is designed but not built. The host composes the panel
  directly.
- Cross-level continuity, which carries a learner-created body definition from
  L0 into L2, is a model-layer concern and is not held in screen state. It is
  not implemented by this ADR. Screens stay disposable. L4 uses real
  astronomical data and no learner-created body.