# ADR-006: Simulation Engine Execution Model

**Status:** Accepted  
**Date:** 20.08.2026  
**Modules:** simulator-core (simulation, model, integrator, physics)  

## Context

Three integrators transform immutable state snapshots (ADR-005). They evaluate
a configurable acceleration model at arbitrary trial states (ADR-004), over a
domain model that separates immutable body definitions from time-dependent
state (ADR-002).

These decisions assume a component that owns committed simulation state.
ADR-005 §2 assigns state commitment to the simulation engine, and ADR-005 §9
assigns finiteness inspection of returned snapshots to the caller. ADR-002
defines `SimulationBody` as the runtime association between a definition and
its current state, but does not say what owns those instances. This ADR
defines the engine: where it lives, what it owns, how execution is driven, how
results are communicated and how failure is handled.

Five constraints apply:

1. The engine must serve two callers with different needs: an interactive
   runtime that drives frames at display cadence, and an evaluation harness
   that runs thousands of steps as fast as possible (Objective 6).
2. ADR-005 §8 separates presentation speed from numerical step size and leaves
   the derivation policy to the engine.
3. ADR-005 §9 treats numerical breakdown as an observable outcome and requires
   the caller to inspect snapshots before feeding them back. The engine is
   that caller, so it must define what happens on a non-finite result.
4. The proposal commits to the Observer pattern for decoupled communication
   with the rendering layer and the adaptive hint engine, and to runtime
   switching of integration strategies for in-application comparison.
5. simulator-core must stay free of JavaFX and of any UI scheduling concept.

## Decision

### 1. The engine lives in simulator-core

`SimulationEngine` is placed in
`io.github.eugenp84.orbitalsim.core.simulation`. It is part of the
computational core and must run identically under JUnit, a command-line
evaluation harness, the learning scenarios and the JavaFX application. The
dependency direction is:

```
simulator-core (physics, integrator, simulation)
        ↑
simulator-learning        configures and drives the engine
        ↑
simulator-app             renders and supplies frame cadence
```

The engine has no knowledge of scenarios, hints, rendering or frames. The
learning module may depend on the engine, and the engine never depends on the
learning module.

### 2. The engine does not own a real-time loop

The engine exposes deterministic operations only. It has no knowledge of
`AnimationTimer`, wall-clock time, threads or rendering cadence. The
scheduling loop belongs to simulator-app or to a controller component above
the engine. simulator-core stays free of UI dependencies, and both caller
types use the same engine:

```
JavaFX / scenario runtime            Evaluation harness
    frame timer                          tight loop
        ↓                                    ↓
    engine.advanceBy(...)                engine.step()
```

### 3. `step()` is the primitive and `advanceBy()` is orchestration

`step()` attempts exactly one numerical integration step of the engine's fixed
timestep and reports its outcome.

`advanceBy(simulatedSeconds)` translates a requested amount of simulated time
into whole fixed substeps and carries any remainder forward in an accumulator:

```
accumulatedSeconds += requestedSeconds;
while (accumulatedSeconds >= integrationStepSeconds) {
    attempt one internal integration step;
    if successful: accumulatedSeconds -= integrationStepSeconds;
    if breakdown:  stop;
}
```

The numerical step size never varies. A larger speed multiplier requests more
simulated time per frame and so more substeps of identical size, which is the
separation ADR-005 §8 requires. A partial final substep is never executed.
Sub-step remainders wait in the accumulator for the next request.

Only successfully completed steps are deducted from the accumulator. On
numerical breakdown, the accumulator is reset to zero and any unconsumed
requested time is discarded. This rule is part of the contract. If the
unconsumed interval were kept, a later `advanceBy()` call would attempt to
satisfy time requested before the failure without the caller knowing. After
breakdown the caller must decide policy in any case (ADR-005 §9), and nothing
needs a pre-failure request to continue implicitly. After breakdown, the
engine holds the last committed state, the clock of that state and an empty
accumulator.

Evaluation calls `step()` directly. Routing Objective 6 runs through
`advanceBy()` would make the remainder accumulator part of the experiment.

### 4. The engine owns `SimulationBody` runtime instances

The engine holds the committed state of the simulation as `SimulationBody`
instances. `SimulationBody` is the runtime aggregate defined in ADR-002, which
associates an immutable `CelestialBody` definition with its currently
committed `StateVector`.

This ADR changes the rationale for `SimulationBody`. ADR-002 justified it
partly as a stable identity that observers hold across frames. Observers now
receive immutable snapshots (section 6) and hold no body references, so that
reason no longer applies. The remaining reason is internal. Without the
aggregate, the engine would hold `CelestialBody[]` and `StateVector[]` as
parallel arrays whose index alignment is an unchecked invariant.

`SimulationBody` is private to the engine: it appears in no event, no result
and no public engine signature. It stays a minimal holder (definition, state,
`updateState`) with no physics, scenario, collision or rendering behaviour.

The engine is constructed from `List<BodySnapshot>`, where each element pairs
a definition with its initial state, and builds its internal `SimulationBody`
instances from those pairs. Separate definition and state collections as
constructor arguments would bring back the same index-alignment invariant at
the boundary.

The numerical layer sees only `StateVector[]`:

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

Each integration step runs in four stages:

1. Extract the committed snapshot.
2. Invoke the integrator.
3. Inspect every component of the candidate for finiteness (the inspection
   ADR-005 §9 assigns to the caller).
4. Commit all states and advance simulation time, or commit nothing.

Simulation time advances only on a successful commit. A rejected candidate
leaves both state and clock as they were. If the engine was at t = 1000 s and
the candidate for t = 1060 s contains a non-finite component, the engine stays
at t = 1000 s with the last valid state. A clock that advanced past a rejected
commit would describe a state that was never committed.

Elapsed simulation time is derived from a completed-step counter
(`completedSteps × integrationStepSeconds`) and is not accumulated by repeated
floating-point addition, so rounding does not build up over long runs. The
derived double still incurs one multiplication rounding, but `completedSteps`
is exact and gives the evaluation harness an exact integer step clock.
Simulation time is elapsed seconds from the initial state and is not an
absolute astronomical epoch. Any mapping to calendar dates for NEO validation
belongs to simulator-data.

The non-finite candidate is not published as an ordinary snapshot. Failure
information travels only in the failure result and the failure notification.

### 6. Observers receive immutable snapshots, never runtime objects

Observer events carry immutable records:

```java
public record BodySnapshot(CelestialBody definition, StateVector state) { }

public record SimulationSnapshot(
        double simulationTimeSeconds,
        long completedSteps,
        List<BodySnapshot> bodies) { }
```

Neither `SimulationEngine` nor `SimulationBody` is exposed to an observer.
Both records enforce their own invariants in their compact constructors.
`BodySnapshot` rejects null components. `SimulationSnapshot` rejects a null
body list, a negative or non-finite time and a negative step count. It also
applies `List.copyOf(bodies)`, which rejects null elements and makes the
published list immutable whatever the caller supplied. A caller-supplied array
or mutable list stays mutable even when its elements are immutable, as
ADR-005 §5 notes for integrator inputs.

`BodySnapshot` carries the full immutable definition because the known
consumers (renderer and hint engine) need name, type, radius and mass. The
definition is immutable, so sharing it is safe. `BodySnapshot` also serves as
the engine's construction input (section 4), with the same meaning in both
roles: a definition paired with a state at an instant.

The Java shape of the observer contract (a single interface with snapshot and
failure methods, or separate listener types) is left to implementation. This
ADR fixes the semantics: immutable payloads, no runtime-object exposure,
cadence (section 7), distinct success and failure notifications, and isolation
(section 9).

### 7. Publication cadence is per public operation, not per substep

Integration commit cadence and observer publication cadence are distinct, for
the same reason that ADR-005 §8 distinguishes integration timestep from
presentation speed. A single interactive request may execute hundreds or
thousands of internal substeps. Publishing an observer event for each would
impose thousands of callbacks per frame at high speed multipliers.

The cadence rule is:

- `step()` commits one step and publishes one snapshot event.
- `advanceBy()` performs as many internal substeps as required and publishes
  one snapshot event per call, carrying the final committed snapshot, provided
  at least one step succeeded.
- On breakdown, the engine publishes a distinct failure notification. If
  substeps succeeded before the failure, the final committed snapshot is
  published first and the failure notification follows.
- `advanceBy()` calls that complete zero steps (accumulator below threshold)
  publish nothing.

Internally, `advanceBy()` drives a private integration primitive and not the
public `step()`, so publication policy exists in two public methods only and
is not duplicated.

Per-substep observation is not provided. It can be added as a separate change
if the adaptive hint engine needs it.

### 8. Caller outcome and observer notification are separate mechanisms

The direct caller of `step()` or `advanceBy()` controls execution and must
learn synchronously whether advancement occurred. Observers are decoupled
components interested in state changes. One mechanism cannot serve both.
Reporting breakdown only through events would leave the driving loop unaware
that its own call did not advance. Throwing an exception would contradict
ADR-005 §9, which classifies breakdown as an observable outcome.

One `step()` call attempts exactly one step, while one `advanceBy()` call may
complete several steps, complete none or partially succeed, so the two
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

Both records declare `implements StepResult` explicitly. Nested records do not
implement their enclosing interface by position, and without the clause the
sealed interface would admit no implementations. `Advanced` rejects a null
snapshot in its compact constructor. `firstNonFiniteBodyIndex` is
non-negative. That it falls within the engine's body count is an engine
invariant, since the record cannot know the count.

`advanceBy()` returns a result describing the whole operation:

```java
public record AdvanceResult(
        SimulationSnapshot snapshot,        // last committed state (always present)
        int completedSteps,                 // steps committed by this call, may be 0
        double carriedSeconds,              // accumulator after this call
        StepResult.NumericalBreakdown breakdown  // null if no breakdown occurred
) { }
```

`AdvanceResult` has the following invariants. The record enforces those it can
check, and the engine guarantees the rest:

- `snapshot` is non-null and is always the engine's last committed state
- `completedSteps ≥ 0`
- `carriedSeconds ≥ 0`
- on every non-breakdown outcome, `carriedSeconds < integrationStepSeconds`
- `breakdown != null ⇒ carriedSeconds == 0` (the accumulator-reset rule of
  section 3)

These invariants distinguish every case that section 3 defines: zero completed
steps (`completedSteps == 0`, `breakdown == null`, request carried), full
success (`completedSteps > 0`, `breakdown == null`) and partial success
(`breakdown != null`). Each can be asserted directly in tests. The committed
snapshot is always present, so a caller never needs a second call to learn the
engine's current state.

`NumericalBreakdown` identifies the first body index at which a non-finite
component was detected, together with the attempted time. The finiteness
inspection knows both at the moment of failure, and the index helps diagnosis.
The invalid candidate is never embedded, because `SimulationSnapshot`
represents committed state only.

A collision is a physically valid scenario event and is different in kind from
numerical breakdown. The sealed interface can accept a `Collision` variant
later without redesign. No collision model is designed in this ADR.

### 9. Observer failure does not affect the committed step

Observer notification occurs only after a successful state and time commit. An
observer that fails cannot roll a committed step back, and reporting the
observer's failure through the step's own result would misreport a call that
succeeded.

Application-level observer failures never invalidate, roll back or misreport
the committed simulation step. Dispatch isolates each observer. A
`RuntimeException` thrown by one observer does not prevent the remaining
observers from being notified and does not disturb the engine or its caller.
It is reported through an observer-error mechanism, separate from `StepResult`
and `AdvanceResult`. The isolation boundary is `RuntimeException`.
`AssertionError` is not caught, because assertions indicate violated
programmer assumptions and should fail tests visibly.

Serious JVM errors are not observer failures. Conditions such as
`OutOfMemoryError` or `StackOverflowError` indicate that the process may no
longer be in a state where continued simulation is safe. The observer
mechanism does not catch or suppress them, and they may propagate to the
engine's caller. If such an error arises during post-commit dispatch, it can
propagate even though the numerical step was committed. This is accepted
because process-level failure lies outside the `StepResult` domain. The
committed state remains committed in either case.

Observers are outside the trusted numerical code. Within the application-level
failure domain, the simulation's correctness and reporting do not depend on
observer behaviour.

### 10. The integrator is a replaceable strategy

The engine is constructed with its bodies (as paired snapshots), its
acceleration model, its fixed integration timestep and an initial integrator.
The first three are fixed for the engine's lifetime. The selected integrator
can be replaced:

```java
engine.selectIntegrator(new VelocityVerletIntegrator());
```

One invariant applies: the integrator may change only between completed
integration operations, and changing it alters nothing else. Bodies,
simulation time, the fixed timestep, the acceleration model and the
accumulator are unaffected.

The alternative, reconstructing the engine from its committed snapshot to
switch methods, was rejected. Reconstruction would require copying simulation
time, body states, the accumulator and observer registrations, and each copy
is an opportunity for a defect, all to avoid mutating one field that holds a
stateless object. `Integrator` implementations are stateless and
interchangeable and operate on identical state through an identical model
(ADR-005), so replacing one at a step boundary is safe. It is also the direct
application of the Strategy pattern that the proposal commits to for
in-application comparison.

## Consequences

### Positive

- One engine serves interactive scenarios, JUnit tests and the Objective 6
  harness identically, and no timing infrastructure is needed to test it.
- No code path exists in which a speed multiplier reaches the integrator's
  `timeStepSeconds`.
- Fixed substepping keeps every integration step identical, as the controlled
  conditions of Objective 6 require.
- The two-phase contract of ADR-002 and ADR-005 is executed by a single
  component with an atomic commit point. A failed step cannot leave partial
  state or a misleading clock, and a failed `advanceBy()` leaves no
  pre-failure time to consume.
- Rendering cadence is decoupled from integration cadence. Interactive callers
  receive one event per request regardless of substep count.
- The caller of every public operation learns synchronously what occurred,
  including partial success, without a second call.
- Observer-facing types are immutable and enforce their own invariants, and no
  mutable runtime object crosses an architectural boundary.
- Application-level observer defects cannot affect the simulation, and
  process-level failures stay visible.
- Integrator switching for in-application comparison is a single operation
  with a stated invariant.
- The exact integer `completedSteps` counter gives evaluation a drift-free
  time base.

### Negative / Accepted Trade-offs

- The accumulator adds engine state whose semantics (carry on success, reset
  on breakdown, bypass in evaluation) callers must understand. The semantics
  are documented on `advanceBy()`.
- Unconsumed time is discarded on breakdown, so an interactive caller that
  wishes to resume must issue a fresh request.
- `AdvanceResult` is a wider type than a bare enum. Each field answers a
  question the driving caller could otherwise answer only with additional
  calls.
- A mutable integrator field is an exception to the fixed-at-construction
  configuration style. It is limited by the invariant in section 10 and is
  narrower than copying all runtime context on every switch.
- Per-observer isolation adds a per-listener catch of `RuntimeException` to
  the notification path.
- A serious JVM error during post-commit dispatch may propagate from a call
  whose numerical step was committed, because process-level failure is not
  classified as an observer failure.
- Every public operation allocates an immutable snapshot for its result and
  for observers. The cost is negligible at N ≤ 4 and consistent with the
  allocation trade-off accepted in ADR-005.
- `BodySnapshot` shares the full definition, which couples event consumers to
  the shape of `CelestialBody`. The definition is immutable and its fields are
  the ones the known consumers require.