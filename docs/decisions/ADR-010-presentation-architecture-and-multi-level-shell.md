# ADR-010: JavaFX Presentation Architecture and Multi-Level Shell

**Status:** Accepted  
**Date:** 15.09.2026  
**Modules:** simulator-app (primary); consumes simulator-learning and simulator-core through their existing public surfaces  
**Related:** Project Proposal §4.1 (JavaFX 2D visualisation), §4.2 (Levels 0–4), §4.3 (usability); ADR-001 (module boundaries), ADR-005 §8 (numerical timestep vs. presentation speed), ADR-006 (engine execution model and publication cadence), ADR-008 (learning scenario architecture; §4 sole-advancement, §9 hint observer seam)

---

## Context

`simulator-core`, `simulator-data`, and the learning backends provided the computational and pedagogical foundations. An initial JavaFX presentation was developed for Level 1, after which Level 0 provided the second concrete consumer that exposed duplicated presentation responsibilities and the need for a reusable multi-level application shell. The presentation architecture therefore evolved incrementally from a single-level L1 interface into the shared structure recorded by this ADR.

Three forces shaped the design as it was built incrementally:

1. **The levels are structurally different, not variations of one screen.** L0 configures four physical parameters with no assessment; L1 configures one speed fraction, is assessed, and shows adaptive hints; L2–L4 will differ again. A single monolithic UI would either special-case every level or collapse their genuine differences.
2. **Most presentation behaviour is common.** Every level needs the same run/pause/reset loop, wall-clock-to-simulated-time conversion, trajectory trail, rendering, and status/elapsed readouts. Duplicating that per level would be substantial, repeated technical debt across five levels.
3. **The lifecycle and cadence boundaries from earlier ADRs had to be preserved in the UI.** Advancement must route through the session (ADR-008 §4); presentation speed must never alter the numerical timestep (ADR-005 §8); the learning observation cadence must not be replaced by the JavaFX frame rate.

The architecture below was reached incrementally, each extraction justified by a second concrete consumer or an observed defect rather than by anticipation. Only the adopted result is recorded here.

## Decision Drivers

- Support heterogeneous levels through one shared shell without per-level duplication of the loop, trail, rendering and generic controls.
- Keep the JavaFX layer out of the learning and core layers, and keep each layer's boundary intact (sole advancement, three-cadence separation, sealed session).
- Extract shared machinery only when a second consumer demonstrates it is genuinely shared; avoid speculative UI abstraction.
- Usability: the control panel must remain legible as levels add controls, on small (14") screens.

## Decision

### 1. A shared, level-agnostic presentation core

`SimulationViewCore` owns the machinery common to every level: the `AnimationTimer` loop, the `TrajectoryTrail`, the `SimulationRenderer`, and the generic control widgets (run/pause, reset, time-rate slider, status, integrator, elapsed). It drives its scenario only through the level-agnostic `ScenarioControls` interface, so it holds no knowledge of orbital speed, hints or planet parameters. Advancement and rendering run on the JavaFX Application Thread; the loop runs continuously and advances only while running.

Three concerns that vary by level are *injected* rather than embedded, so the core stays genuinely level-agnostic: the learner-facing interpretation of a termination reason, the selection of which body the trail follows, and the integrator label text. This kept a real defect from the design — without injection, the core would have rendered "stable orbit achieved" for a Level 0 completion.

The core exposes its generic widgets individually (as bare, un-captioned values) so each screen decides how to caption and arrange them; the core never learns how a screen lays out its panel. The view transform is replaceable, so a level whose physical extent changes with configuration (L0's orbital distance) can re-frame the view without rebuilding the core.

### 2. Level-agnostic control surface, per-level controllers

`ScenarioControls` is the narrow surface the core and generic widgets use: lifecycle transitions, wall-clock advancement, presentation speed, snapshot, lifecycle state, termination reason, reset. `SimulationController` (L1) and `Level0Controller` (L0) both implement it, and each adds only its level-specific configuration and pedagogy (L1: speed-fraction, hints, assessability refusal; L0: four planet parameters, circular-speed reference, no hints). The lifecycle/presentation behaviour shared by both controllers is factored into a `ScenarioPlayer`, extracted once the second controller demonstrated the identical need.

This split proves the abstraction generalises: the same shared surface drives L0's trivial, unconditional, hint-free scenario and L1's history-dependent assessed scenario.

### 3. Host / screen composition shell

`Main` is a thin `Application` host. It owns the window shell and persistent level navigation and manages screen lifecycle; it knows nothing of how a screen builds its simulation or controls. Each level is a `LevelScreen` that owns its controller, view core and controls, and exposes two regions the host composes: `simulationView()` (the canvas region, returned as a `Parent` so overlays can be added later without changing the host) and `controlsView()` (the level-specific controls). The host places the simulation region in the centre and, on the right, a vertical panel holding the persistent navigation above the active screen's controls.

Screens are rebuilt fresh on each switch; a screen is a disposable view carrying no cross-level state. The replacement screen is constructed before the current screen is deactivated. If construction fails, the current screen remains operational. If the switch does not complete, the previous navigation selection is restored; failures during subsequent lifecycle or installation operations propagate without attempting screen rollback.

### 4. Vertical side-panel layout

Controls live in a vertical right-hand panel with a preferred width of approximately 300 px, rather than in horizontal top bars. This was adopted in response to observed truncation: horizontal caption+value rows were ellipsised on a 14" screen, hiding information. Each control and readout is stacked vertically with its caption on its own line and long values (e.g. termination status) wrapped, which removes horizontal competition rather than merely reducing it. The panel's preferred width is a property of the host shell, not of any individual screen, so levels do not independently redefine the shell's intended width.

### Lifecycle and cadence invariants preserved

- **Sole advancement (ADR-008 §4):** the loop advances only through `ScenarioControls`/the session; the app never drives the engine directly.
- **Three cadences kept distinct:** the numerical timestep is fixed in the engine; the learning observation cadence is the session's chunking; the presentation frame rate drives only rendering. The time-rate control scales requested simulated time (ADR-005 §8) and never the timestep. A stalled frame's wall-clock delta is clamped so it cannot inject a large one-shot simulated jump.
- **Hint observer seam (ADR-008 §9):** hints reach the UI as a read-through on the controller; the hint engine observes committed states at the learning cadence, not the frame rate.

## Relationship to the proposal's named patterns

The proposal (ADR-008 context) named State, Template Method and Observer as intended patterns. This presentation architecture preserves the **Observer** mechanism for learning feedback: the L1 hint engine receives committed simulation states through the `SimulationListener` seam and the UI reads the resulting hint through the controller. The JavaFX `AnimationTimer` is instead the presentation scheduler: it requests wall-clock-derived advancement and rendering but is not a `SimulationListener`.

The UI additionally uses a **Strategy-by-injection** approach for level-varying presentation behaviour, including termination-text interpretation and trail-body selection, with the integrator display value also supplied to the presentation core rather than embedded as level-specific knowledge.

It deliberately does **not** introduce a State-pattern object graph or a Template-Method scenario superclass for the UI: the `LevelScreen` interface plus per-level composition proved sufficient for two heterogeneous levels, and additional pattern machinery would be speculative until L2–L4 demonstrate a need. This continues the project's established discipline of adopting abstractions only when a second consumer justifies them (cf. ADR-008's rejection of premature pattern commitment).

## Consequences

**Positive**

- One shared core and shell drive two structurally different levels (L0, L1) with no duplicated loop/trail/render/status code; the differences live entirely in each screen and controller. This is direct evidence the presentation abstraction generalises, and is the intended path for L2–L4.
- Every layer boundary from earlier ADRs is preserved: JavaFX is confined to `simulator-app`; the session remains the sole advancement boundary and stays sealed; the three cadences remain distinct.
- The extractions (`ScenarioPlayer`, `ScenarioControls`, `SimulationViewCore`, the host/screen split) were each driven by a demonstrated second consumer or an observed usability defect, not by anticipation.
- The vertical side panel scales to more controls, which is a precondition for L3/L4's larger control sets.

**Negative / accepted**

- `SimulationViewCore` has a wide constructor (controls, canvas, transform, trail, refresh callback, two injected functions, integrator value). This is the cost of keeping it level-agnostic; it is documented rather than hidden behind a builder at this stage.
- A failed level switch guarantees only that construction failure is safe; a failure during a screen's own activation is treated as a programming defect and propagates rather than being rolled back.
- Two small UI helpers (`sectionHeading`, `labelledValue`) are duplicated across the two screens. Extracting a shared UI utility is deferred until a third consumer (L2) confirms the shape.

## Deferred / future work

- A dedicated `SidePanel` component (persistent navigation region + replaceable content region) was designed but not built; the host currently composes the panel directly. It becomes worthwhile when navigation grows beyond two levels.
- Cross-level continuity — carrying a learner-created body definition from L0 into later levels (the concept chains L0→L2 explicitly, via serialised state) — is intentionally a model-layer concern, not screen state. Screens remain disposable; continuity will be introduced when its first downstream consumer (L2) requires it, and L4 remains anchored to real astronomical data rather than a learner-created body.