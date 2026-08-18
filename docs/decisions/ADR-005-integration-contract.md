# ADR-005: Pure Snapshot-Based Numerical Integration Contract

**Status:** Accepted  
**Date:** 18.08.2026  
**Modules:** simulator-core (integrator, physics, simulation)  
**Related:** Project Proposal §2.2 (Simulation Engine), §3.1 (Numerical Methods); Objective 2 (three interchangeable integration methods), Objective 6 (integrator comparison); ADR-002 (definition–state separation), ADR-003 (gravitational model), ADR-004 (arbitrary-state acceleration evaluation)

---

## Context

Three numerical integration methods — Forward Euler, Velocity Verlet and
Runge–Kutta 4 — must be implemented as interchangeable strategies (Objective
2) and compared head-to-head under identical conditions (Objective 6).

ADR-002 established that permanent body properties are separated from
time-dependent state, and that all next states must be computed before any
runtime object is updated, to prevent mixed-timestep state. ADR-004
established that the gravitational model is evaluated at arbitrary supplied
states rather than at committed runtime state, because multi-stage methods
require derivative evaluations at intermediate trial states.

This decision defines the contract that connects the two: what an integrator
consumes, what it produces, and what it is forbidden from doing.

## Decision Drivers

- Honour the two-phase update contract established in ADR-002.
- Keep numerical algorithms free of runtime domain objects, so that they can
  be tested and compared as mathematics.
- Support multi-stage methods whose intermediate states are not committed
  state.
- Preserve genuine substitutability across the three integration strategies.
- Allow numerical breakdown to be observed rather than concealed.
- Prevent presentation concerns from being conflated with numerical accuracy.

## Decision

### 1. Integrators are pure snapshot transformers

An integrator consumes an immutable snapshot of the whole system and returns a
new snapshot. It holds no state between calls.

```
StateVector[] step(
        StateVector[] currentStates,
        double timeStepSeconds,
        AccelerationModel accelerationModel);
```

### 2. Integrators never commit state

An integrator must not accept, reference or mutate `SimulationBody`.
Committing a completed snapshot to runtime bodies is the responsibility of the
simulation engine, and occurs only after a step has returned successfully:

```
SimulationBody[]
      │  engine extracts current snapshot
      ▼
StateVector[]  ──▶  Integrator.step(...)  ──▶  new StateVector[]
                          │
                          │ evaluates AccelerationModel
                          │ at current and, for multi-stage
                          │ methods, trial states
                          ▼
                    engine commits
                          ▼
              SimulationBody.updateState(...)
```

Because the complete next snapshot exists before any commit occurs, the
two-phase requirement of ADR-002 is satisfied structurally rather than by
convention. A step that throws leaves the simulation state untouched, provided
the engine follows this contract and the integrator treats its input array as
read-only (section 5).

### 3. The acceleration model is supplied per call

The physical model is passed to `step` rather than bound to an integrator
instance. This keeps the integrator strategy itself stateless and free of
lifecycle assumptions, which simplifies controlled head-to-head comparison
across integration methods and preserves substitutability of implementations.
The `Integrator` interface consequently imposes no constructor contract.

The simulation engine owns the current physical model, the selected
integrator, the timestep and the current state; the integrator owns only the
algorithm.

### 4. AccelerationModel contract

`AccelerationModel` is introduced now because real callers exist: all three
integrators require acceleration evaluations, and Runge–Kutta 4 requires
several per step at trial states. ADR-004 deferred this abstraction until that
condition was met.

```java
public interface AccelerationModel {
    Vector3D[] accelerationsAt(StateVector[] states);
}
```

The abstraction is mathematical rather than specifically gravitational. The
production implementation is `NBodyAccelerationCalculator`, but simpler models
are legitimate — a constant-acceleration implementation allows an integrator
to be tested independently of gravity.

**Return contract.** Implementations must return a non-null array containing
exactly one acceleration per supplied state, in corresponding body order, with
no null elements.

Every returned acceleration vector must additionally contain finite
components. An integrator depends on this interface rather than on any
particular implementation, so it cannot rely on implementation-specific
safeguards; a non-finite acceleration admitted here would propagate into state
that `StateVector` deliberately permits, spreading across every body within a
few steps and surfacing far from its origin.

This obligation is real rather than inherited. `GravityCalculator` guarantees
that each pairwise contribution is finite, but a sum of finite vectors may
still overflow, so `NBodyAccelerationCalculator` verifies its accumulated
total before returning it.

**Model scope.** For the current project, an `AccelerationModel` must
determine acceleration from body positions and fixed model configuration only.
Velocity is present in the supplied `StateVector` because integrators operate
on complete state snapshots, but it must not influence the computed
acceleration.

This restriction exists because standard Velocity Verlet evaluates
acceleration at the updated position before completing its velocity update,
and therefore assumes acceleration is independent of velocity. Permitting
velocity-dependent models would leave Forward Euler and RK4 valid while
invalidating standard Velocity Verlet over the same model, defeating the
substitutability that Objective 2 requires and that section 3 above claims to
preserve. Supporting velocity-dependent forces — aerodynamic drag, for
instance — would require revisiting this contract and, in particular, the
Velocity Verlet implementation.

ADR-004 observed that `NBodyAccelerationCalculator` reads only `position()`.
That was a description of one implementation; this section makes the property
normative for every implementation of the interface.

**Verification.** Integrators verify the return contract before using the
result. A violation raises `IllegalStateException` rather than
`IllegalArgumentException`: the caller supplied valid arguments, and a
collaborator failed to honour its own contract. Where an implementation
detects that it cannot itself produce a representable result, it raises
`IllegalArgumentException`, consistent with the physics-boundary policy of
ADR-003.

### 5. Arrays are read-only snapshot containers

`StateVector` and `Vector3D` are immutable, but the arrays carrying them are
not. Neither an `Integrator` nor an `AccelerationModel` may modify a supplied
array or replace any of its elements; the array is treated as a read-only
snapshot container.

This matters most for `AccelerationModel`, which receives the array a
multi-stage integrator is still using: a model that wrote to it could corrupt
the inputs of a later stage within the same step.

`Integrator.step` returns a non-null array of the same length, in
corresponding body order, containing no null states, and distinct from the
supplied array. Individual returned `StateVector` objects need not be newly
allocated: because they are immutable, returning an unchanged input element is
safe, and requiring fresh allocation would be an interface invariant serving
no purpose.

### 6. Input contract for `Integrator.step`

- `currentStates` and `accelerationModel` must be non-null, and every state
  element must be non-null — otherwise `NullPointerException`.
- `timeStepSeconds` must be positive and finite — otherwise
  `IllegalArgumentException`.
- A violated `AccelerationModel` return contract raises
  `IllegalStateException`.
- A zero-length state array is valid and produces a zero-length result.

The three exception types correspond to three distinct failure sources: an
absent argument, an invalid argument value, and a misbehaving collaborator.

### 7. Timestep

The parameter is named `timeStepSeconds` rather than `dt`, `delta` or `speed`,
for the reason given in section 8.

Backward integration — a negative timestep — is mathematically legitimate and
has established uses in astrodynamics, including propagation backwards from a
known epoch. It is nevertheless rejected in the current implementation because
no scenario or evaluation activity requires it, and permitting it would weaken
a check that catches sign errors. This is a deliberate scope restriction
rather than a claim that backward propagation is invalid; it is recorded as a
candidate for future work.

### 8. Simulation speed is not the integration timestep

The user-facing time multiplier described in the project scope controls how
much simulated time elapses per rendered frame. It is **not** the numerical
step size.

Treating a 20× speed increase as a 20× larger integration step would degrade
accuracy, and would do so most severely in the NEO scenario, where long time
horizons are compressed and where results are validated against published
CNEOS close-approach data. A future engine may advance one display frame using
several integration substeps.

This ADR does not decide how the engine derives step size from a speed
setting; that policy belongs to the simulation engine. It fixes only that the
integrator API accepts the actual numerical step size and nothing else.

### 9. Finite results are not guaranteed

Integrators validate their arguments and the `AccelerationModel` return
contract, but do **not** guarantee that the state they produce remains finite.

The two cases differ. A non-finite acceleration returned by a model violates
that collaborator's declared mathematical contract. A non-finite state
produced from otherwise valid inputs instead represents numerical breakdown of
the integration run — a phenomenon this project exists partly to measure.
Forward Euler is the baseline precisely because it degrades, and the timestep
at which usable propagation breaks down is evidence for the comparison
required by Objective 6. Reclassifying that outcome as a contract violation
would prevent the evaluation harness from observing it, and would make the
failure boundary an artefact of each method's stability rather than of any
defect.

Non-finite state must not be confused with physical escape: a hyperbolic
escape trajectory remains finite indefinitely. A non-finite result means the
numerical representation has ceased to provide a usable state.

Detection and policy therefore belong to the caller, which has the context to
respond appropriately — a learning scenario should terminate and report
numerical instability, while an evaluation harness should record the method,
timestep and step index at which divergence first appeared.

**Consequence for the engine.** The caller must inspect a returned snapshot
before committing it and before invoking the next step. A non-finite state fed
back into the acceleration model will fail inside the gravity layer on the
following step, losing the information that breakdown began one step earlier.

### 10. Empty systems

A zero-length system returns a zero-length result, consistent with the
aggregation and energy layers. No special-casing by callers is required. The
acceleration model is still invoked and its return contract still verified, so
a model returning accelerations for a zero-length system is detected.

### 11. Forward Euler is the first implementation

The baseline is **Forward (explicit) Euler**, advancing both state components
from current values:

```
r(n+1) = r(n) + v(n) · Δt
v(n+1) = v(n) + a(n) · Δt
```

The method is named explicitly to distinguish it from semi-implicit
(symplectic) Euler, which uses the updated velocity for the position update
and exhibits markedly different energy behaviour. That distinction matters
directly to the comparison in Objective 6: attributing Forward Euler's energy
drift to "Euler" without qualification would misrepresent the Euler family.

## Consequences

### Positive

- Integrators can be tested as pure mathematics, using synthetic acceleration
  models with no gravity involved, isolating "does Euler implement Euler" from
  "does the gravitational model compute gravity".
- Objective 6 comparison is a loop over integrator instances applied to one
  model and one initial snapshot; reproducibility follows from purity rather
  than from careful reset logic.
- Runge–Kutta 4's intermediate stages are ordinary arguments rather than a
  special case, because trial states are never confused with committed state.
- A failed step cannot leave the simulation in a partially updated state.
- The three integration strategies are genuinely substitutable over any
  conforming acceleration model, because the model contract is constrained to
  what all three require.
- Non-finite values cannot enter integration through the acceleration model,
  so a non-finite state identifies the integration step itself as the point of
  breakdown.
- Numerical instability remains observable as an experimental outcome rather
  than being converted into an exception.
- Presentation speed is prevented from being directly conflated with numerical
  step size at the integrator boundary.

### Negative / Accepted Trade-offs

- Each step allocates at least a new result array and will normally construct
  new state snapshots for each body. At N ≤ 4 this allocation cost is
  immaterial.
- The engine must extract snapshots before a step and commit them afterwards,
  rather than integrators updating bodies in place. This is the intended
  separation, but it is code the engine must contain.
- Callers must inspect returned snapshots for finiteness rather than relying
  on the integrator to fail fast. This is deliberate (§9), but it is a
  responsibility that both the engine and the evaluation harness must
  implement.
- Velocity-dependent acceleration models are excluded by contract, so
  aerodynamic drag or thrust would require revisiting section 4.
- Backward integration is unavailable until the restriction is revisited.
- The read-only array convention is a documented contract rather than an
  enforced one: Java arrays cannot be made immutable, and defensive copying at
  every boundary would be disproportionate at this scale.
- Defensive verification of the `AccelerationModel` return contract is
  repeated in each integrator. Whether this is extracted to a shared
  package-private helper will be decided once genuine duplication exists
  across the three implementations, rather than pre-emptively. An abstract
  base class is not the intended form: the three methods share an interface,
  not an is-a relationship, and a base class would reimpose the constructor
  assumptions section 3 deliberately avoids.

## Guiding Principle

Integration is mathematics over immutable snapshots; committing state is the
engine's responsibility; constrain the collaborator contract to what every
strategy requires; treat numerical breakdown as an observation rather than a
defect; and treat the numerical step size as a physical quantity, not a
presentation setting.
