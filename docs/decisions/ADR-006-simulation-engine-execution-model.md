# ADR-006: Simulation Engine Execution Model

**Status:** Accepted  
**Date:** 20.08.2026  
**Modules:** simulator-core (simulation, model, integrator, physics)  
**Related:** Project Proposal §2.1 (System Architecture), §2.2 (Simulation Engine), §3.5 (Design Patterns); Objective 2 (integration methods), Objective 6 (integrator comparison); ADR-002 (definition–state separation), ADR-004 (arbitrary-state evaluation), ADR-005 (integration contract)

---

## Context

The physics and integration layers are complete: three integrators transform
immutable state snapshots (ADR-005), evaluating a configurable acceleration
model at arbitrary trial states (ADR-004), over a domain model that separates
immutable body definitions from time-dependent state (ADR-002).

What does not yet exist is the component those decisions all presuppose: the
owner of committed simulation state. ADR-005 §2 assigns state commitment to
"the simulation engine"; ADR-005 §9 assigns finiteness inspection of returned
snapshots to "the caller"; ADR-002 defines `SimulationBody` as the runtime
association between a definition and its current state, but nothing yet owns
those instances. This decision defines that component: where it lives, what it
owns, how execution is driven, how results are communicated, and how failure
is handled.

Several forces constrain the design:

1. The engine must serve two callers with different needs: an interactive
   runtime driving frames at display cadence, and an evaluation harness
   running thousands of steps as fast as possible (Objective 6).
2. ADR-005 §8 separates presentation speed from numerical step size but
   deliberately left the derivation policy to the engine.
3. ADR-005 §9 leaves numerical breakdown as an observable outcome rather than
   an exception, and requires the caller to inspect snapshots before feeding
   them back — so the engine, as that caller, must define what happens on a
   non-finite result.
4. The proposal commits to the Observer pattern for decoupled communication
   with the rendering layer and the adaptive hint engine, and to runtime
   switching of integration strategies for in-application comparison.
5. simulator-core must remain free of JavaFX and of any UI scheduling
   concept.

## Decision

### 1. The engine lives in simulator-core

`SimulationEngine` is placed in
`io.github.eugenp84.orbitalsim.core.simulation`. It is part of the
computational core: it must run identically under JUnit, a command-line
evaluation harness, the learning scenarios and the JavaFX application. The
dependency direction is:

```
simulator-core (physics, integrator, simulation)
        ↑
simulator-learning        — configures and drives the engine
        ↑
simulator-app             — renders and supplies frame cadence
```

The engine knows nothing about scenarios, hints, rendering or frames. The
learning module may depend on the engine; the engine never depends on the
learning module.

### 2. The engine does not own a real-time loop

The engine exposes deterministic operations only. It has no knowledge of
`AnimationTimer`, wall-clock time, threads or rendering cadence. The
scheduling loop belongs to simulator-app (or a controller component above the
engine). This keeps simulator-core free of UI dependencies and gives both
caller types the same engine:

```
JavaFX / scenario runtime            Evaluation harness
    frame timer                          tight loop
        ↓                                    ↓
    engine.advanceBy(...)                engine.step()
```

### 3. `step()` is the fundamental primitive; `advanceBy()` is orchestration

`step()` attempts exactly one numerical integration step of the engine's
fixed timestep and reports its outcome.

`advanceBy(simulatedSeconds)` translates a requested amount of simulated time
into whole fixed substeps, carrying any remainder forward in an accumulator:

```
accumulatedSeconds += requestedSeconds;
while (accumulatedSeconds >= integrationStepSeconds) {
    attempt one internal integration step;
    if successful: accumulatedSeconds -= integrationStepSeconds;
    if breakdown:  stop;
}
```

The numerical step size never varies. A larger speed multiplier requests more
simulated time per frame and therefore more substeps of identical size — the
separation ADR-005 §8 required. A partial final substep is never executed;
sub-step remainders wait in the accumulator for the next request.

**Accumulator consumption.** Only successfully completed steps are deducted
from the accumulator. **On numerical breakdown, the accumulator is reset to
zero and any unconsumed requested time is discarded.** This is normative, not
an implementation detail. Retaining the unconsumed interval would cause a
later `advanceBy()` call to silently attempt to satisfy time requested before
the failure; after breakdown the caller must decide policy in any case
(ADR-005 §9), and no legitimate consumer exists for implicit continuation of
a pre-failure request. After breakdown, the engine holds the last committed
state, the clock of that state, and an empty accumulator.

**Evaluation calls `step()` directly.** Routing Objective 6 runs through
`advanceBy()` would make the remainder accumulator part of the experiment.
`step()` is the deterministic primitive; `advanceBy()` is runtime
orchestration for interactive callers.

### 4. The engine owns `SimulationBody` runtime instances

The engine holds the committed state of the simulation as `SimulationBody`
instances — the runtime aggregate ADR-002 defined, associating an immutable
`CelestialBody` definition with its currently committed `StateVector`.

This decision also updates the rationale for `SimulationBody`. ADR-002
justified it partly as a stable identity that observers hold across frames.
Under this ADR observers receive immutable snapshots (section 6) and hold no
body references, so that justification lapses. The stronger and now primary
justification is internal: without the aggregate, the engine would hold
`CelestialBody[]` and `StateVector[]` as parallel arrays whose index
alignment is a permanent unchecked invariant — exactly the relationship this
project has deliberately avoided elsewhere. `SimulationBody` is
engine-private: it appears in no event, no result and no public engine
signature, and it remains a restrained holder (definition, state, updateState)
with no physics, scenario, collision or rendering behaviour.

**Construction input is paired, not parallel.** The engine is constructed
from `List<BodySnapshot>` — each element pairing a definition with its
initial state — and builds its internal `SimulationBody` instances from
those pairs. Accepting separate definition and state collections as
constructor arguments would recreate at the boundary the index-alignment
invariant this section prohibits internally.

The numerical layer continues to see only `StateVector[]`:

```
SimulationBody[]              ← engine-owned runtime aggregates
       ↓ extract
StateVector[]                 ← mathematical boundary (ADR-005)
       ↓
Integrator
       ↓
candidate StateVector[]
       ↓ finiteness validation
SimulationBody.updateState()  ← commit boundary, only if valid
```

### 5. Candidate validation, atomic commit, and time advancement

Each integration step executes: extract the committed snapshot; invoke the
integrator; inspect every component of the candidate for finiteness (the
inspection ADR-005 §9 assigns to the caller); then either commit all states
and advance simulation time, or commit nothing.

Simulation time advances only on successful commit. A rejected candidate
leaves both state and clock exactly as they were: if the engine was at
t = 1000 s and the candidate for t = 1060 s contains a non-finite component,
the engine remains at t = 1000 s holding the last valid state. Time and state
move atomically or not at all — a clock that advanced past a rejected commit
would describe a state that never existed.

Elapsed simulation time is derived from a completed-step counter
(`completedSteps × integrationStepSeconds`) rather than accumulated by
repeated floating-point addition. This avoids accumulated repeated-addition
rounding over long runs; the derived double still incurs a single
multiplication rounding, but the authoritative quantity — `completedSteps` —
is exact, which gives the evaluation harness an exact integer step clock.
Simulation time is elapsed seconds from the initial state; it is not an
absolute astronomical epoch. Any mapping to calendar dates for NEO validation
belongs to simulator-data.

The non-finite candidate is not published as an ordinary snapshot. Failure
information travels in the failure result and failure notification only.

### 6. Observers receive immutable snapshots, never runtime objects

Observer events carry immutable records:

```java
public record BodySnapshot(CelestialBody definition, StateVector state) { }

public record SimulationSnapshot(
        double simulationTimeSeconds,
        long completedSteps,
        List<BodySnapshot> bodies) { }
```

Neither `SimulationEngine` nor `SimulationBody` is ever exposed to an
observer. **Both records enforce their own invariants in their compact
constructors** rather than depending on call-site discipline:
`BodySnapshot` rejects null components; `SimulationSnapshot` rejects a null
body list, a negative or non-finite time, and a negative step count, and
applies `List.copyOf(bodies)` — which also rejects null elements — so the
published list is immutable regardless of what the caller supplied. An array
or caller-supplied mutable list container remains mutable even when its
elements are not: the same observation ADR-005 §5 made about integrator
inputs, applied to the publishing side.

`BodySnapshot` carries the full immutable definition rather than a bare
identifier because known consumers (renderer, hint engine) need name, type,
radius and mass; the definition is immutable, so sharing it is safe.
`BodySnapshot` also serves as the engine's construction input (section 4):
its meaning in both roles is identical — a definition paired with a state at
an instant.

The exact Java shape of the observer contract — a single interface with
snapshot and failure methods, or separate listener types — is deliberately
left to implementation. This ADR fixes the semantics that matter: immutable
payloads, no runtime-object exposure, cadence (section 7), distinct success
and failure notifications, and isolation (section 9).

### 7. Publication cadence is per public operation, not per substep

Integration commit cadence and observer publication cadence are distinct,
for the same reason ADR-005 §8 distinguished integration timestep from
presentation speed. A single interactive request may legitimately execute
hundreds or thousands of internal substeps; publishing an observer event for
each would defeat the separation of numerical cadence from rendering cadence
and impose thousands of callbacks per frame at high speed multipliers.

The cadence rule:

- `step()` — commits one step and publishes one snapshot event.
- `advanceBy()` — performs as many internal substeps as required and
  publishes **one** snapshot event per call, carrying the final committed
  snapshot, provided at least one step succeeded.
- On breakdown, the engine publishes a distinct failure notification. If
  substeps succeeded before the failure, the final committed snapshot is
  published as usual, then the failure notification follows — in that order.
- `advanceBy()` calls that complete zero steps (accumulator below threshold)
  publish nothing.

Internally, `advanceBy()` drives a private integration primitive rather than
the public `step()`, so publication policy exists in exactly two public
methods and is not duplicated or accidentally compounded.

If the adaptive hint engine later demonstrates a genuine need for
per-substep observation, that will be added as an explicit, separately
justified change; thousands of observer callbacks per frame are not the
default.

### 8. Caller outcome and observer notification are separate mechanisms

The direct caller of `step()` or `advanceBy()` controls execution and must
learn synchronously whether advancement occurred. Observers are decoupled
components interested in state changes. One mechanism must not serve both:
reporting breakdown only through events would leave the driving loop unaware
that its own call did not advance, while throwing an exception would
contradict ADR-005 §9's classification of breakdown as an observable outcome
rather than a defect.

Because one `step()` call attempts exactly one step while one `advanceBy()`
call may complete several steps, complete none, or partially succeed, the two
operations have different result types.

`step()` returns:

```java
public sealed interface StepResult {

    record Advanced(SimulationSnapshot snapshot)
            implements StepResult { }

    record NumericalBreakdown(
            double attemptedTimeSeconds,
            int firstNonFiniteBodyIndex)
            implements StepResult { }
}
```

Both records explicitly declare `implements StepResult`; nested records do
not implement their enclosing interface by position, and without the clause
the sealed interface would admit no implementations at all. `Advanced`
rejects a null snapshot in its compact constructor.
`firstNonFiniteBodyIndex` is non-negative; that it falls within the engine's
body count is an engine invariant, since the record cannot know the count.

`advanceBy()` returns a result describing the whole operation:

```java
public record AdvanceResult(
        SimulationSnapshot snapshot,        // last committed state (always present)
        int completedSteps,                 // steps committed by this call, may be 0
        double carriedSeconds,              // accumulator after this call
        StepResult.NumericalBreakdown breakdown  // null if no breakdown occurred
) { }
```

**`AdvanceResult` invariants**, enforced where the record can and stated as
engine guarantees where it cannot:

- `snapshot` is non-null and is always the engine's last committed state;
- `completedSteps ≥ 0`;
- `carriedSeconds ≥ 0`;
- on every non-breakdown outcome, `carriedSeconds < integrationStepSeconds`;
- `breakdown != null ⇒ carriedSeconds == 0` (the accumulator-reset rule of
  section 3).

These invariants distinguish every case section 3 defines — zero completed
steps (`completedSteps == 0`, `breakdown == null`, request carried), full
success (`completedSteps > 0`, `breakdown == null`), and partial success
(`breakdown != null`) — and they translate directly into test assertions.
The committed snapshot is always present, so a caller never needs a second
call to learn where the simulation rests.

`NumericalBreakdown` identifies the first body index at which a non-finite
component was detected, alongside the attempted time. The finiteness
inspection knows both at the moment of failure, and the index materially aids
diagnosis. The invalid candidate itself is never embedded:
`SimulationSnapshot` represents committed state by definition.

A collision, when collision detection is implemented, is a physically valid
scenario event and categorically different from numerical breakdown. The
sealed interface admits a future `Collision` variant without redesign; no
collision model is designed here.

### 9. Observer failure does not affect the committed step

Observer notification occurs only after successful state and time commit. A
committed step is a fact; an observer that fails cannot roll it back, and
reporting the failure of an already committed step through the step's own
result would misreport a call that succeeded.

The rule: **application-level observer failures never invalidate, roll back,
or misreport the committed simulation step.** Dispatch isolates each
observer: a `RuntimeException` thrown by one observer does not prevent the
remaining observers from being notified, does not disturb the engine or its
caller, and is surfaced through an observer-error reporting mechanism rather
than through `StepResult` or `AdvanceResult`. The isolation boundary is
`RuntimeException`. `AssertionError` is deliberately not caught: assertions
indicate violated programmer assumptions and should fail tests loudly rather
than being silently absorbed by dispatch.

**Serious JVM errors are not observer failures.** Conditions such as
`OutOfMemoryError` or `StackOverflowError` indicate that the process may no
longer be in a state where continued simulation is safe; the observer
mechanism does not catch or suppress them, and they may propagate to the
engine's caller. One consequence is stated honestly rather than papered
over: if such an error arises during post-commit dispatch, it can propagate
even though the numerical step was successfully committed. That is
acceptable because process-level failure lies outside the `StepResult`
domain — this ADR distinguishes simulation outcomes from process failures
rather than pretending the engine can safely encapsulate both. The committed
state remains committed in either case.

The Observer boundary is an architectural boundary, not trusted numerical
code; the simulation's correctness and reporting are independent of observer
behaviour within the application-level failure domain.

### 10. The integrator is a deliberately replaceable strategy

The engine is constructed with its bodies (as paired snapshots), its
acceleration model, its fixed integration timestep, and an initial
integrator. Of these, the first three are fixed for the engine's lifetime.
The selected integrator is intentionally mutable:

```java
engine.selectIntegrator(new VelocityVerletIntegrator());
```

subject to one invariant: **the integrator may change only between completed
integration operations, and changing it alters nothing else** — not bodies,
not simulation time, not the fixed timestep, not the acceleration model, not
the accumulator.

This reverses the position taken in the reviewed draft of this ADR, which
proposed reconstructing the engine from its committed snapshot to switch
methods. Review identified that reconstruction transfers more mutable context
than it avoids: simulation time, body states, the accumulator and observer
registrations would all require faithful copying — each a defect opportunity
— to avoid mutating a single field that holds a stateless object. The
`Integrator` contract (ADR-005) was designed precisely so that
implementations are stateless, interchangeable, and operate on identical
state through an identical model; replacing one at a step boundary is
therefore unusually safe, and doing so is the direct application of the
Strategy pattern the proposal commits to for in-application comparison.
Mutability is acceptable where the invariant is clear; this one is.

## Consequences

### Positive

- One engine serves interactive scenarios, JUnit tests and the Objective 6
  harness identically; no timing infrastructure is needed to test it.
- The numerical step size is structurally insulated from presentation speed:
  no code path exists in which a speed multiplier reaches the integrator's
  `timeStepSeconds`.
- Fixed substepping keeps every integration step identical, preserving the
  controlled conditions Objective 6 requires.
- The two-phase contract of ADR-002/005 is executed by a single component
  with an atomic commit point; a failed step cannot leave partial state or a
  misleading clock, and a failed `advanceBy()` cannot leave a hidden
  obligation to consume pre-failure time.
- Rendering cadence is decoupled from integration cadence; interactive
  callers receive one event per request regardless of substep count.
- The caller of every public operation learns synchronously and completely
  what occurred, including partial success, without a second call; the
  stated `AdvanceResult` invariants translate directly into tests.
- Observer-facing types are all immutable, enforce their own invariants, and
  no mutable runtime object crosses an architectural boundary.
- The simulation is robust against application-level observer defects, while
  process-level failures remain visible rather than suppressed.
- Integrator switching for in-application comparison is a single safe
  operation with a stated invariant, exercising the Strategy design the
  proposal requires.
- The exact integer `completedSteps` counter gives evaluation a drift-free
  time base.

### Negative / Accepted Trade-offs

- The accumulator introduces engine state whose semantics (carry on success,
  reset on breakdown, bypass in evaluation) callers must understand; the
  semantics are normative here and documented on `advanceBy()`.
- Discarding unconsumed time on breakdown means an interactive caller that
  wishes to resume must issue a fresh request; accepted because implicit
  continuation of a pre-failure request has no legitimate consumer.
- `AdvanceResult` is a wider type than a bare enum; accepted because every
  field answers a question the driving caller otherwise cannot answer
  without additional calls.
- A mutable integrator field is a deliberate exception to the
  fixed-at-construction configuration style; accepted under a stated
  invariant, and narrower than the alternative of copying all runtime
  context on every switch.
- Per-observer isolation adds dispatch ceremony (per-listener catch of
  `RuntimeException`) to the notification path; accepted as the cost of
  making an architectural boundary safe.
- A serious JVM error during post-commit dispatch may propagate from a call
  whose numerical step was committed; accepted as the honest consequence of
  refusing to classify process-level failure as an observer failure.
- Every public operation allocates an immutable snapshot for its result and
  for observers. Immaterial at N ≤ 4; consistent with the allocation
  trade-off already accepted in ADR-005.
- `BodySnapshot` sharing the full definition couples event consumers to
  `CelestialBody`'s shape; accepted because the definition is immutable and
  its fields are precisely what known consumers require.

## Guiding Principle

The engine owns committed state, a fixed numerical step and nothing about
time as the user experiences it; candidates are validated before an atomic
commit of state and clock together; the accumulator carries only what
success has earned and forgets what failure has invalidated; callers learn
outcomes synchronously and completely while observers receive immutable
facts in isolation; observer defects are contained but process failures are
not concealed; and the only mutable configuration is the one the Strategy
pattern exists to replace.
