# ADR-008: Learning Scenario Architecture and Evaluation Cadence

**Status:** Accepted  
**Date:** 03.09.2026  
**Modules:** simulator-learning (primary), simulator-app (composition), simulator-core (orbital quantities consumed by learning)  

## Context

The learning layer turns a configured physical situation into an educational
activity. It contains the five progressive scenarios (L0–L4) and the adaptive
hint system, and builds on `simulator-core` and `simulator-data`.

The five levels differ in pedagogical purpose:

| Level | Physical evaluation | Failure possible | Adaptive hints |
|---|---|---|---|
| L0 Planet Creation | No | No | Minimal |
| L1 Stable Orbit | Yes | Yes | Yes |
| L2 Planet Comparison | No | No | No |
| L3 Gravitational Perturbation | Yes | Yes | No, by scoping decision |
| L4 NEO Scenario | Observation-based | No hard failure | Potentially |

The proposal names three design patterns for this layer: State for level
progression, Template Method for a common scenario lifecycle, and Observer for
hint integration.

Four questions are addressed:

1. What is a scenario, structurally? The levels share a lifecycle but differ
   in how completion and failure are decided.
2. At what cadence is scenario progress evaluated? ADR-006 §7 publishes one
   observer event per public engine operation, not per integration substep, so
   the learning layer cannot assume every numerical step is observable.
3. Where does scenario configuration come from? L3 needs a real planetary body
   and L4 needs NEO data, and both live in `simulator-data`.
4. How does runtime integrator switching interact with trajectory-based
   grading? The answer changes what a completion policy can assume.

## Decision

### 1. Scenario lifecycle and outcome semantics are separated

A scenario is modelled as a definition (identity, learning objective, initial
simulation configuration, control configuration, completion policy and
optional hint policy). A session executes the definition and owns the common
lifecycle:

```
not started → initialise → active → (completion | failure) → finished
```

Completion and failure semantics are supplied as a policy composed into the
session. Subclass overrides are not used:

```
ScenarioSession
    ├── owns lifecycle and progression state
    ├── drives and observes SimulationEngine
    └── consults a scenario-specific completion policy
```

The session consults the policy with its accumulated context and the current
committed snapshot, and the policy returns an evaluation. A level whose
completion is a user action, a level that tracks orbital progress statefully
and a level that runs to an observation horizon are then three different
policies, and no level implements behaviour irrelevant to it.

Concrete policy types are created only when a level's implementation needs
them. The supporting types named above may also be merged during
implementation.

### 2. Scenario lifecycle is realised through composition

The proposal anticipated Template Method as a possible mechanism for
structuring the common scenario lifecycle. During detailed design, the main
variation between scenarios was found to lie in their outcome semantics:
physical evaluation, failure conditions and hint availability.

The design uses a common `ScenarioSession` to coordinate the lifecycle, with
scenario-specific behaviour supplied through composed policies. Completion,
evaluation and hint behaviour can vary independently while lifecycle
orchestration stays the same across the five levels. This is a composition-
and Strategy-based design, with no inheritance-based lifecycle hierarchy.

Template Method is not prescribed for the scenario implementation. Scenario
subclasses that exist only to provide lifecycle hooks would add inheritance
without a demonstrated requirement. The design can be revisited if meaningful
lifecycle variation appears.

The design adopts Strategy for variable scenario policies and Observer for
simulation-state notification. Scenario progression is modelled as a state
machine (section 3).

### 3. Progression is modelled as a state machine, and its realisation is left open

Scenario progression is modelled over these states:

```
NotStarted → Running ⇄ Paused
                 ├→ Completed
                 └→ Failed
```

Not every transition is valid for every scenario. L0 and L2 have no reachable
`Failed` state, and L4 has no failure transition for a physically valid
outcome. The model defines the states the system can occupy, and each scenario
determines which transitions are valid for it.

This ADR fixes the model: the states, the legal transitions and their
per-scenario reachability. It does not fix whether the model is realised as
the GoF State pattern (state-dependent operations delegated to substitutable
state objects) or as validated transitions over an enumeration.

The proposal anticipated the State pattern. Whether it is warranted depends on
how much state-dependent behaviour exists, and the current design has little.
Advancement is active in `Running` and inert in every other state.
`NotStarted`, `Completed` and `Failed` behave almost identically. The
accumulated evaluation state (angular progress, revolution count and integrity
monitoring) belongs to the completion policy (section 1), so a state object
would carry little behaviour and no data. The mechanism is left to
implementation.

Scenario progression state is distinct from simulation pause. The engine owns
no real-time or application lifecycle concept (ADR-006 §2), so pausing a
scenario is a learning-layer and application concern.

### 4. Three cadences are distinct: numerical, learning-evaluation, presentation

ADR-005 §8 separated numerical timestep from presentation speed, and
ADR-006 §7 separated integration cadence from publication cadence. The
learning layer requires a third rate:

```
numerical cadence       fixed integration timestep (dt)
learning cadence        simulated interval between scenario evaluations
presentation cadence    rendering frame rate
```

A scenario session splits an interactive advance request into bounded
observation intervals and calls the engine once per interval, so that scenario
evaluation receives regularly spaced observations regardless of frame rate:

```
frame requests advance by X simulated time
        ↓
ScenarioSession splits X into observation intervals
        ↓
SimulationEngine.advanceBy(interval)   one snapshot per call (ADR-006 §7)
        ↓
fixed numerical substeps internally
```

The engine does not change. N chunked calls yield N snapshots under the
existing per-operation publication rule, and the accumulator carries
remainders.

The observation interval is bounded on both sides.

The lower bound is an integer multiple of the timestep: `interval = k × dt`
for a positive integer `k`. An interval below `dt` does not eliminate
observations, because successive requests accumulate until a step completes,
but the requested cadence then differs from the actual one. A non-integer
multiple makes the number of substeps per observation alternate (successive
requests at 1.5 × dt commit 1, 2, 1, … steps), which makes evaluation
irregular. With `interval = k × dt`, every observation represents exactly `k`
committed steps. This follows from the engine invariant that carried seconds
are always below one timestep (ADR-006 §8), since `⌊(c + k·dt)/dt⌋ = k` for
any `c < dt`. A non-zero carry is preserved and not cleared, so the
observation timeline is offset by a constant but stays uniformly spaced.

The upper bound is set by orbital phase resolution. Revolution counting and
crossing detection rely on angular progress between observations. With too
coarse an interval, a crossing can be stepped over and revolutions can be
miscounted. The interval must be small relative to the scenario's
characteristic orbital period.

Exact values are scenario-specific and are calibrated empirically.

While a scenario session is active, it is the only component permitted to
request simulation advancement. The accumulator arithmetic above does not
require this, but timeline integrity does. If the application advanced the
same engine independently between observation intervals, simulated time would
pass that the session never observed, and revolution counts and shape
evaluation would be corrupted. Advancement flows in one direction:

```
JavaFX controller  → requests simulated-time advancement
        ↓
ScenarioSession    → owns observation chunking and evaluation
        ↓
SimulationEngine   → fixed numerical substeps
```

### 5. Assessed scenarios fix their integrator, and switching belongs to comparison mode

Runtime integrator switching (Objective 2) cannot be combined directly with
trajectory-based grading. A measurement on an exactly circular orbit
(Sun–Earth, `dt` = 1 h, two revolutions) shows:

| Integrator | max ΔE/E | max Δa/a |
|---|---|---|
| Forward Euler | 1.74 % | 1.77 % |
| Velocity Verlet | ~0.0000 % | ~0.0000 % |

A learner who submits the analytically correct answer would fail an
energy-based criterion under Euler and pass it under Verlet. The level would
then grade the integrator.

An assessed scenario fixes the integrator used for its graded execution, and
Velocity Verlet is that integrator. Interchangeable integrators remain
available in the comparison and what-if exploration capability, where
differences between methods are observed and do not count as learner success
or failure.

Two alternatives were rejected:

- Calibrating the tolerance to accommodate the least accurate permitted
  integrator. Numerical error would then decide what counts as a good orbit.
  Learner performance, numerical method and scenario timestep would also be
  entangled, so recalibrating the timestep would change the meaning of
  success.
- Grading with a hidden reference integrator while displaying another. The
  learner would be assessed on a different simulation from the one they
  observe.

L1 teaches orbital mechanics and comparison mode teaches numerical methods.
Letting numerical error decide success in L1 would mix the two learning goals.

### 6. L1 success is defined by orbital shape, with energy drift as a separate integrity diagnostic

Energy and semi-major axis are not independent evidence. For a two-body orbit
`ε = −μ/2a` is bijective, so an energy tolerance and a semi-major-axis
tolerance express the same constraint in different units. Neither constrains
eccentricity: an orbit can conserve energy, hold `a` constant and still be
clearly elliptical.

L1 success is defined as:

1. at least N complete revolutions (N = 2)
2. no collision or minimum-altitude violation
3. no escape
4. eccentricity bounded by a scenario-configured `e_max`
5. numerical energy drift below a generous integrity threshold

Criteria 1–4 assess the learner's orbit. Criterion 5 assesses whether the
simulation can be trusted and is not a measure of orbital quality. Under the
fixed Verlet integrator it has no effect for well-behaved orbits and matters
only at high eccentricity, where a fixed timestep becomes inadequate near
periapsis:

| e | max ΔE/E (Verlet, dt = 1 h, 2 revolutions) |
|---|---|
| 0.0 – 0.3 | 0.0000 % |
| 0.5 | 0.0001 % |
| 0.7 | 0.0013 % |
| 0.9 | 0.1198 % |

The analytic circular velocity `v_c = √(μ/r)` is the hint system's reference
target but is not the success criterion. A slightly eccentric orbit may still
be an acceptable answer, so hints guide towards the analytic value while
assessment applies a pedagogically chosen tolerance.

This ADR does not fix numerical thresholds. `e_max`, the minimum altitude and
the energy-integrity threshold are calibrated empirically and documented.

### 7. Instantaneous orbital quantities belong to the core, and temporal scenario progress does not

The boundary runs between quantities derived from a single physical state and
quantities accumulated across successive observations:

```
simulator-core        instantaneous orbital mathematics
                      eccentricity vector and eccentricity
                      orbital elements and derived quantities
                      relative position and velocity quantities

simulator-learning    temporal pedagogical interpretation
                      accumulated angular progress
                      completed-revolution count
                      "two revolutions completed"
```

Eccentricity is a property of `(r, v, μ)` at an instant and requires the
gravitational parameter. It is astrodynamics and belongs in the core. Counting
completed revolutions is history-dependent. It exists only as accumulated
angular progress across a sequence of observations, and it answers a question
about whether a learning activity has progressed. Placing it in the core would
make the computational engine aware of a scenario-completion concept, contrary
to the separation established in ADR-006.

The learning layer consumes instantaneous orbital quantities and derives none
of the underlying orbital mathematics. The core never accumulates scenario
progress.

L1 depends on core orbital-element computation, which is the subject of
ADR-009 (orbital element representation and Cartesian conversion). The same
rule that keeps NEO conversion out of the learning module keeps orbital-shape
mathematics out of it.

### 8. `simulator-learning` remains independent of `simulator-data`

The module dependency graph established in ADR-001 is unchanged:

```
            simulator-app
             /          \
            v            v
 simulator-learning   simulator-data
            \            /
             v          v
            simulator-core
```

`simulator-learning` does not depend on `simulator-data`. A learning scenario
does not need to know where a celestial body came from. L3 needs a perturbing
body with suitable physical parameters, and has no use for NASA JSON, a
planet-library repository, Gson, HTTP or `NeoDataProvider`. L4 needs a
NEO-compatible simulation configuration and presentation metadata, and does
not need to fetch them.

Composition runs through the application layer:

```
simulator-data     acquires external/reference information
        ↓
simulator-app      selects, converts and maps it
        ↓
simulator-learning receives scenario-ready, source-neutral configuration
        ↓
simulator-core     executes the physics
```

If NASA were replaced by another source, the learning logic would compile
unchanged.

For L4, the chain is:

```
NeoWs → simulator-data → provider-neutral data → app composition
      → core orbital conversion → initial BodySnapshot configuration
      → simulator-learning → L4 session
```

The astrodynamics transformation is a core concern, and the learning module
must not perform it.

### 9. Adaptive hints as optional capability

Hint support varies independently of completion semantics. L1 has hints. L3
does not, because the correct parameter space cannot be characterised
analytically once multiple bodies interact. L0 and L2 have little use for
hints, and L4 may have them.

Hints are an optional composed capability and are not a method that every
scenario must implement. If hint support were a base-class method, L3 would
need an artificial implementation stating that it has none.

The hint engine observes simulation state through the existing observer
boundary (`SimulationListener`, ADR-006) and does not take part in scenario
physics execution. It can be extended or disabled without affecting the
simulation or rendering layers. Hint evaluation uses the learning observation
cadence, because hints do not need per-substep evaluation.

## Consequences

### Positive

- Levels with different pedagogical outcomes share one lifecycle, and no level
  implements behaviour irrelevant to it.
- Scenario behaviour varies through composed policies, without scenario
  subclasses that exist only to provide lifecycle hooks.
- Scenario evaluation is independent of frame rate. Correctness depends on a
  configured simulated interval.
- The three cadences are separated with no change to the engine, by reusing
  the publication contract of ADR-006.
- Graded scenarios are fair: a correct answer cannot fail because of the
  integrator.
- L1 success measures the learner's orbit, and numerical trustworthiness is
  monitored separately.
- Numerical thresholds are set by empirical calibration.
- The module dependency graph is preserved. The learning module is
  source-neutral and would be unaffected by a replacement of the external data
  provider.

### Negative / Accepted Trade-offs

- Composition introduces more collaborating types than a single scenario base
  class. The alternative is subclasses with no-op hooks, and the number of
  types is limited by creating policies only when needed.
- Fixing the integrator in assessed scenarios means runtime switching is not
  exercised inside graded levels. The proposal requires interchangeable
  methods and in-application comparison, which the comparison mode provides.
- Chunked advancement means the learning layer issues several engine calls per
  frame. The cost is negligible at this scale.
- While a scenario is active, the application cannot advance the engine
  directly, and all advancement is routed through the session.
- L1 cannot be completed until core orbital-shape computation exists
  (ADR-009).
- L1 cannot be finally validated until the numerical thresholds are
  calibrated.