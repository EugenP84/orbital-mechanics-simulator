# ADR-008: Learning Scenario Architecture and Evaluation Cadence

**Status:** Accepted  
**Date:** 03.09.2026  
**Modules:** simulator-learning (primary), simulator-app (composition), simulator-core (orbital quantities consumed by learning)  
**Related:** Project Proposal §4.2 (Educational Objectives, Levels 0–4), §4.4 (Design Patterns), §6 (Knowledge-Based Adaptive Hint System); ADR-001 (module boundaries), ADR-005 (integration contract), ADR-006 (engine execution model and publication cadence), ADR-007 (external data boundary)

---

## Context

`simulator-core` and `simulator-data` are complete. What does not yet exist is
the layer that turns a configured physical situation into an *educational
activity*: the five progressive scenarios (L0–L4) and the adaptive hint system.

The proposal specifies five levels that are deliberately heterogeneous in
pedagogical purpose:

| Level | Physical evaluation | Failure possible | Adaptive hints |
|---|---|---|---|
| L0 Planet Creation | No | No | Minimal |
| L1 Stable Orbit | Yes | Yes | Yes |
| L2 Planet Comparison | No | No | No |
| L3 Gravitational Perturbation | Yes | Yes | No, by scoping decision |
| L4 NEO Scenario | Observation-based | No hard failure | Potentially |

The proposal also commits to specific design patterns: State for level
progression, Template Method for a common scenario lifecycle, and Observer for
hint integration.

Three questions had to be resolved before implementation:

1. **What is a scenario, structurally?** The levels share a lifecycle but
   differ fundamentally in how completion and failure are decided.
2. **At what cadence is scenario progress evaluated?** ADR-006 §7 publishes one
   observer event per public engine operation, not per integration substep, so
   the learning layer cannot assume every numerical step is observable.
3. **Where does scenario configuration come from?** L3 needs a real planetary
   body and L4 needs NEO data, both of which live in `simulator-data`.

A fourth question emerged during design and is recorded here because it
materially changes what a completion policy can assume: the interaction between
runtime integrator switching and trajectory-based grading.

## Decision

### 1. Scenario lifecycle and outcome semantics are separated

A scenario is modelled as a **definition** (identity, learning objective,
initial simulation configuration, control configuration, completion policy,
optional hint policy) executed by a **session** that owns the common lifecycle:

```
not started → initialise → active → (completion | failure) → finished
```

Completion and failure semantics are supplied as a **policy** composed into the
session, not as subclass overrides:

```
ScenarioSession
    ├── owns lifecycle and progression state
    ├── drives and observes SimulationEngine
    └── consults a scenario-specific completion policy
```

The policy is consulted with the session's accumulated context and the current
committed snapshot, and returns an evaluation. Heterogeneity then becomes
ordinary rather than exceptional: a level whose completion is a user action, a
level that tracks orbital progress statefully, and a level that runs to an
observation horizon are all just different policies, with no level forced to
implement behaviour irrelevant to it.

Concrete policy types are created only when a level's implementation
demonstrates the need, consistent with the project's convention that
abstractions emerge from demonstrated duplication rather than anticipation.
The same applies to the supporting types named above: several may collapse
into one another during implementation.

### 2. Scenario lifecycle is realised through composition

The proposal anticipated Template Method as a possible mechanism for structuring the common scenario lifecycle. During detailed design, the shared lifecycle was refined and the principal variation between scenarios was found to lie in their outcome semantics, including physical evaluation, failure conditions and hint availability.

The resulting design uses a common `ScenarioSession` to coordinate the lifecycle, with scenario-specific behaviour supplied through composed policies. This allows completion/evaluation and hint behaviour to vary independently while keeping lifecycle orchestration consistent across the five levels. In implementation terms, this is primarily a composition- and Strategy-based design rather than an inheritance-based lifecycle hierarchy.

Template Method therefore remains part of the architectural exploration undertaken during the proposal stage, but is not prescribed for the current scenario implementation. Should meaningful lifecycle variation emerge during implementation, the design can be revisited; otherwise, introducing scenario subclasses solely to provide lifecycle hooks would add inheritance without a demonstrated requirement.

The design currently adopts Strategy for variable scenario policies and Observer for simulation-state notification. Scenario progression is modelled as a state machine (§3); whether that state model warrants the GoF State pattern or simpler validated transitions is likewise determined by the behaviour that emerges during implementation.

### 3. Progression is modelled as a state machine; the realisation mechanism is deferred

Scenario progression is modelled over these states:

```
NotStarted → Running ⇄ Paused
                 ├→ Completed
                 └→ Failed
```

Not every transition is legitimate for every scenario: L0 and L2 have no
reachable `Failed` state, and L4 has no failure transition for a physically
valid outcome. This is a property of scenario configuration, not a defect in
the state model: the model defines the states the system can occupy, and the
scenario determines which transitions are valid for it.

**What this ADR fixes** is the model — the states, the legal transitions, and
their per-scenario reachability. **What it deliberately does not fix** is
whether the model is realised as the GoF State pattern (state-dependent
operations delegated to substitutable state objects) or as validated
transitions over an enumeration.

The proposal anticipated the State pattern. Whether it is warranted depends on
how much genuinely state-dependent behaviour exists, and the current design
gives reason for caution: advancement is active in `Running` and inert in every
other state, `NotStarted`, `Completed` and `Failed` behave near-identically, and
— decisively — the accumulated evaluation state (angular progress, revolution
count, integrity monitoring) belongs to the completion policy under §1, not to
the progression state. A state object would therefore carry little behaviour and
no data.

The mechanism is consequently left to implementation, and whichever is adopted
will be recorded as such. A pattern named in the proposal is not evidence that
the pattern fits; §2 applies the same standard to Template Method.

Scenario progression state is distinct from simulation pause. The engine
deliberately owns no real-time or application lifecycle concept (ADR-006 §2);
pausing a scenario is a learning-layer and application concern, not an engine
state.

### 4. Three cadences are distinct: numerical, learning-evaluation, presentation

ADR-005 §8 separated numerical timestep from presentation speed, and ADR-006 §7
separated integration cadence from publication cadence. This ADR adds the third
rate the learning layer requires:

```
numerical cadence       fixed integration timestep (dt)
learning cadence        simulated interval between scenario evaluations
presentation cadence    rendering frame rate
```

A scenario session translates an interactive advance request into **bounded
observation intervals**, calling the engine once per interval, so that scenario
evaluation receives regularly spaced observations regardless of frame rate:

```
frame requests advance by X simulated time
        ↓
ScenarioSession splits X into observation intervals
        ↓
SimulationEngine.advanceBy(interval)   — one snapshot per call (ADR-006 §7)
        ↓
fixed numerical substeps internally
```

This requires **no change to the engine**: N chunked calls yield N snapshots
under the existing per-operation publication rule, and the accumulator carries
remainders correctly.

**The observation interval is bounded on both sides.**

*Lower bound — an integer multiple of the timestep.* The interval must satisfy
`interval = k × dt` for a positive integer `k`. An interval below `dt` does not
eliminate observations (successive requests accumulate until a step completes),
but it makes the requested cadence diverge from the actual one; a non-integer
multiple makes the number of substeps per observation alternate, introducing
accumulator-induced irregularity into pedagogical evaluation (successive
requests at 1.5 × dt commit 1, 2, 1, … steps). With `interval = k × dt`, every
observation represents exactly `k` committed steps: this follows from the
engine's own invariant that carried seconds are always below one timestep
(ADR-006 §8), since `⌊(c + k·dt)/dt⌋ = k` for any `c < dt`. Note that a
non-zero carry is *preserved* rather than cleared, so the observation timeline
is offset by a constant but remains uniformly spaced.

**Sole advancement during an active session.** While a scenario session is
active, it is the only component permitted to request simulation advancement.
This is not required by the accumulator arithmetic above, which holds
regardless of carry; it is required for *timeline integrity*. If the
application advanced the same engine independently between observation
intervals, simulated time would pass that the session never observed,
corrupting revolution counts and shape evaluation. Advancement therefore flows
in one direction:

```
JavaFX controller  → requests simulated-time advancement
        ↓
ScenarioSession    → owns observation chunking and evaluation
        ↓
SimulationEngine   → fixed numerical substeps
```

*Upper bound — fine enough to resolve orbital phase.* Revolution counting and
crossing detection rely on angular progress between observations. Too coarse an
interval aliases: a crossing can be stepped over entirely, and revolutions can
be miscounted. The interval must therefore be small relative to the scenario's
characteristic orbital period.

Exact values are scenario-specific and calibrated empirically rather than
guessed, consistent with the per-scenario speed-range configuration already
committed to in the proposal.

### 5. Assessed scenarios fix their integrator; switching belongs to comparison mode

Runtime integrator switching (Objective 2) and trajectory-based grading cannot
be combined naively. Measurement on an **exactly correct** circular orbit
(Sun–Earth, `dt` = 1 h, two revolutions) shows:

| Integrator | max ΔE/E | max Δa/a |
|---|---|---|
| Forward Euler | 1.74 % | 1.77 % |
| Velocity Verlet | ~0.0000 % | ~0.0000 % |

A learner submitting the analytically perfect answer would therefore **fail**
an energy-based criterion under Euler and **pass** it under Verlet. The level
would be grading the integrator, not the learner.

**Decision:** an assessed scenario fixes the integrator used for its graded
execution; **Velocity Verlet** is the fixed pedagogical integrator. Interchangeable
integrators remain fully available in the dedicated comparison and what-if
exploration capability, where differences between methods are *observed* rather
than interpreted as learner success or failure.

Two alternatives were rejected:

- *Calibrating the tolerance to survive the least accurate permitted
  integrator.* This would let numerical error, rather than pedagogical
  judgement, determine what counts as a good orbit, and would entangle learner
  performance, numerical method and scenario timestep so that recalibrating the
  timestep silently changes the meaning of success.
- *Grading with a hidden reference integrator while displaying another.*
  Technically possible, but the learner would be assessed on a different
  simulation from the one they observe.

This also sharpens the educational structure: **L1 teaches orbital mechanics;
comparison mode teaches numerical methods.** Allowing numerical error to
determine success in the former would blur the two learning goals.

### 6. L1 success is defined by orbital shape; energy drift is a separate integrity diagnostic

Energy and semi-major axis are not independent evidence: for a two-body orbit
`ε = −μ/2a` is bijective, so an energy tolerance and a semi-major-axis
tolerance express the same constraint in different units. Neither constrains
**eccentricity** — an orbit can conserve energy perfectly, hold `a` constant,
and still be markedly elliptical.

L1 success is therefore defined as:

1. at least N complete revolutions (N = 2 by the proposal);
2. no collision or minimum-altitude violation;
3. no escape;
4. eccentricity bounded by a scenario-configured `e_max`;
5. numerical energy drift below a generous integrity threshold.

Criteria 1–4 assess **the learner's orbit**. Criterion 5 assesses **the
simulation's trustworthiness** and is not a measure of orbital quality. Under
the fixed Verlet integrator this guard is silent for well-behaved orbits and
meaningful only at the pathological edge, where a fixed timestep becomes
inadequate near periapsis:

| e | max ΔE/E (Verlet, dt = 1 h, 2 revolutions) |
|---|---|
| 0.0 – 0.3 | 0.0000 % |
| 0.5 | 0.0001 % |
| 0.7 | 0.0013 % |
| 0.9 | 0.1198 % |

The analytic circular velocity `v_c = √(μ/r)` remains the hint system's
reference target without being the success criterion: a slightly eccentric
orbit may still be an acceptable answer, so hints reason toward the analytic
ideal while assessment applies a pedagogically chosen tolerance.

**Numerical thresholds are not fixed by this ADR.** `e_max`, the minimum
altitude, and the energy-integrity threshold are to be calibrated empirically
and documented before final scenario evaluation, rather than appearing in code
as unexplained constants.

### 7. Instantaneous orbital quantities are a core concern; temporal scenario progress is not

The boundary runs between quantities derived from a **single physical state**
and quantities accumulated across **successive observations**:

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
gravitational parameter; it is astrodynamics and belongs in the core. Counting
completed revolutions is history-dependent: it exists only as accumulated
angular progress across a sequence of observations, and it answers a question
about whether a *learning activity* has progressed. Placing it in the core
would make the computational engine aware of a scenario-completion concept,
contrary to the separation ADR-006 establishes and to the rest of this
document.

The learning layer therefore consumes instantaneous orbital quantities but
derives none of the underlying orbital mathematics itself; conversely, the core
never accumulates scenario progress.

This creates a dependency on core orbital-element computation arriving before
L1 can be completed. That computation is the subject of ADR-009 (orbital
element representation and Cartesian conversion); either a minimal
element-computation capability lands in core first and ADR-009 later formalises
the fuller conversion, or ADR-009 is brought forward. The boundary itself is
not negotiable: the same rule that keeps NEO conversion out of the learning
module keeps orbital-shape mathematics out of it.

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

`simulator-learning` does **not** depend on `simulator-data`. A learning
scenario has no legitimate reason to know where a celestial body came from: L3
needs a perturbing body with suitable physical parameters, not NASA JSON, a
planet-library repository, Gson, HTTP or `NeoDataProvider`. L4 needs a
NEO-compatible simulation configuration plus presentation metadata, not the
ability to fetch it.

Composition therefore runs through the application layer:

```
simulator-data     acquires external/reference information
        ↓
simulator-app      selects, converts and maps it
        ↓
simulator-learning receives scenario-ready, source-neutral configuration
        ↓
simulator-core     executes the physics
```

Data acquisition answers *what data is available*; learning answers *how a
configured physical situation becomes a learning activity*. If NASA were
replaced by another source, the learning logic would compile unchanged.

For L4 specifically, the chain is:

```
NeoWs → simulator-data → provider-neutral data → app composition
      → core orbital conversion → initial BodySnapshot configuration
      → simulator-learning → L4 session
```

The astrodynamics transformation remains a core computational concern; the
learning module must not solve it.

### 9. Adaptive hints are an optional capability, not a mandatory scenario hook

Hint support varies independently of completion semantics: L1 has hints, L3
deliberately does not (the correct parameter space is not analytically
characterisable once multiple bodies interact simultaneously), L0 and L2 have
little use for them, and L4 may.

Hints are therefore an **optional composed capability**, not a method every
scenario must implement. Were hint support a base-class method, L3 would
require an artificial implementation stating that it has none.

The hint engine observes simulation state through the existing observer
boundary (`SimulationListener`, ADR-006) rather than participating in scenario
physics execution, preserving the decoupling the proposal describes: it can be
extended or disabled without affecting the simulation or rendering layers.
Hint evaluation uses the learning observation cadence, which is more than
sufficient — hints do not require per-substep evaluation.

## Consequences

### Positive

- Levels that differ fundamentally in pedagogical outcome share one lifecycle
  without any level implementing behaviour irrelevant to it.
- The design records a pattern anticipated and rejected on evidence, which is
  stronger design justification than implementing a pattern because it was
  named.
- Scenario evaluation is insulated from frame rate: correctness depends on a
  configured simulated interval, not on rendering performance.
- The three-cadence separation is achieved with no change to the engine,
  reusing the publication contract already established in ADR-006.
- Graded scenarios are fair: a correct answer cannot fail because of the
  learner's integrator choice, and the educational distinction between orbital
  mechanics and numerical methods is preserved.
- L1 success measures the learner's orbit, with numerical trustworthiness
  monitored separately rather than conflated into the same number.
- Numerical thresholds are deferred to empirical calibration rather than
  appearing as unexplained constants.
- The module dependency graph is preserved; learning is source-neutral and
  would survive replacement of the external data provider.

### Negative / Accepted Trade-offs

- Composition introduces more collaborating types than a single scenario base
  class. Accepted: the alternative is subclasses with no-op hooks, and type
  count is controlled by creating policies only on demonstrated need.
- Fixing the integrator in assessed scenarios means runtime switching is not
  exercised inside graded levels. Accepted: the proposal requires interchangeable
  methods and in-application comparison, not arbitrary switching while a success
  criterion is active.
- Chunked advancement means the learning layer issues several engine calls per
  frame rather than one. Immaterial at this scale, and it buys frame-rate
  independence.
- While a scenario is active, the application cannot advance the engine
  directly; all advancement is routed through the session. Accepted: shared
  advancement would let unobserved simulated time corrupt scenario evaluation.
- L1 cannot be completed until core orbital-shape computation exists, coupling
  this module's progress to ADR-009. Accepted: implementing eccentricity inside
  the learning module would violate the boundary this ADR exists to protect.
- Deferring numerical thresholds means L1 cannot be finally validated until
  calibration is done. Accepted: an unjustified threshold would be worse than a
  deferred one.

## Guiding Principle

Scenario lifecycle is uniform and belongs to the session; scenario outcome
semantics vary and belong to composed policies. Evaluation observes the
simulation at a deliberately chosen cadence, distinct from both numerical and
rendering rates. A graded scenario measures the learner's physics, never the
numerical method used to display it. And the learning layer receives configured
physical situations — it neither acquires external data nor computes
astrodynamics.
