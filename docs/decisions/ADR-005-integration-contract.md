# ADR-005: Pure Snapshot-Based Numerical Integration Contract

**Status:** Accepted  
**Date:** 18.08.2026  
**Modules:** simulator-core (integrator, physics, simulation)  

## Context

Forward Euler, Velocity Verlet and Runge–Kutta 4 must be implemented as
interchangeable strategies (Objective 2) and compared under identical
conditions (Objective 6).

ADR-002 separated permanent body properties from time-dependent state and
required all next states to be computed before any runtime object is updated,
which prevents mixed-timestep state. ADR-004 established that the
gravitational model is evaluated at supplied states, because multi-stage
methods need derivative evaluations at intermediate trial states that are not
committed runtime state.

This ADR defines the contract between the two: what an integrator consumes,
what it produces and what it must not do.

## Decision Drivers

- Preserve the two-phase update contract of ADR-002.
- Keep numerical algorithms free of runtime domain objects, so that they can
  be tested in isolation.
- Support multi-stage methods whose intermediate states are not committed
  state.
- Make the three integration strategies substitutable.
- Allow numerical breakdown to be observed.
- Separate presentation speed from numerical step size.

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

An integrator must not accept, reference or mutate `SimulationBody`. The
simulation engine commits a completed snapshot to the runtime bodies, and only
after a step has returned successfully:

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

The complete next snapshot exists before any commit occurs, so the two-phase
requirement of ADR-002 follows from the structure of the call. A step that
throws leaves the simulation state untouched, provided the engine follows this
contract and the integrator treats its input array as read-only (section 5).

### 3. The acceleration model is supplied per call

The physical model is passed to `step` and is not bound to an integrator
instance. The integrator strategy stays stateless and free of lifecycle
assumptions, which simplifies controlled comparison across integration methods
and keeps implementations substitutable. The `Integrator` interface imposes no
constructor contract.

The simulation engine owns the current physical model, the selected
integrator, the timestep and the current state. The integrator owns the
algorithm.

### 4. AccelerationModel contract

`AccelerationModel` is introduced in this ADR because all three integrators
require acceleration evaluations, and Runge–Kutta 4 requires several per step
at trial states. ADR-004 left the interface out until such callers existed.

```java
public interface AccelerationModel {
    Vector3D[] accelerationsAt(StateVector[] states);
}
```

The abstraction is mathematical and not tied to gravity. The production
implementation is `NBodyAccelerationCalculator`. Simpler models are allowed,
and a constant-acceleration implementation lets an integrator be tested
independently of gravity.

Implementations must return a non-null array containing exactly one
acceleration per supplied state, in corresponding body order, with no null
elements. Every returned acceleration vector must have finite components.

An integrator depends on this interface and cannot rely on the safeguards of a
particular implementation. `StateVector` accepts non-finite values, so a
non-finite acceleration would propagate into state, spread across every body
within a few steps and surface far from its origin. `GravityCalculator`
guarantees that each pairwise contribution is finite, but a sum of finite
vectors may still overflow, so `NBodyAccelerationCalculator` verifies its
accumulated total before returning it.

An `AccelerationModel` must determine acceleration from body positions and
fixed model configuration only. Velocity is present in the supplied
`StateVector` because integrators operate on complete state snapshots, but it
must not influence the computed acceleration.

Standard Velocity Verlet evaluates acceleration at the updated position before
completing its velocity update, so it assumes acceleration is independent of
velocity. Velocity-dependent models would leave Forward Euler and RK4 valid
but invalidate standard Velocity Verlet over the same model, and the
substitutability required by Objective 2 would be lost. Supporting
velocity-dependent forces such as aerodynamic drag would require revisiting
this contract and the Velocity Verlet implementation.

ADR-004 noted that `NBodyAccelerationCalculator` reads only `position()`. This
section makes that property a requirement for every implementation of the
interface.

Integrators verify the return contract before using the result. A violation
raises `IllegalStateException`, because the caller supplied valid arguments
and a collaborator broke its contract. An implementation that cannot produce a
representable result raises `IllegalArgumentException`, consistent with the
physics-boundary policy of ADR-003.

### 5. Arrays are read-only snapshot containers

`StateVector` and `Vector3D` are immutable, but the arrays carrying them are
not. Neither an `Integrator` nor an `AccelerationModel` may modify a supplied
array or replace any of its elements.

This matters most for `AccelerationModel`, which receives the array a
multi-stage integrator is still using. A model that wrote to it could corrupt
the inputs of a later stage within the same step.

`Integrator.step` returns a non-null array of the same length, in
corresponding body order, containing no null states, and distinct from the
supplied array. Individual returned `StateVector` objects need not be newly
allocated. They are immutable, so returning an unchanged input element is
safe.

### 6. Input contract for `Integrator.step`

- `currentStates` and `accelerationModel` must be non-null, and every state
  element must be non-null. Otherwise `NullPointerException` is thrown.
- `timeStepSeconds` must be positive and finite. Otherwise
  `IllegalArgumentException` is thrown.
- A violated `AccelerationModel` return contract raises
  `IllegalStateException`.
- A zero-length state array is valid and produces a zero-length result
  (section 10).

The three exception types correspond to three failure sources: an absent
argument, an invalid argument value and a collaborator that broke its
contract.

### 7. Timestep

The parameter is named `timeStepSeconds`, not `dt`, `delta` or `speed`, for
the reason given in section 8.

Backward integration (a negative timestep) is mathematically valid and has
established uses in astrodynamics, including propagation backwards from a
known epoch. It is rejected in the current implementation because no scenario
or evaluation activity requires it, and permitting it would weaken a check
that catches sign errors. It is a candidate for future work.

### 8. Simulation speed is not the integration timestep

The user-facing time multiplier controls how much simulated time elapses per
rendered frame. It is not the numerical step size.

Treating a 20× speed increase as a 20× larger integration step would degrade
accuracy, most severely in the NEO scenario, where long time horizons are
compressed and results are validated against published CNEOS close-approach
data. The engine may advance one display frame using several integration
substeps.

How the engine derives step size from a speed setting is engine policy and is
not decided here. The integrator API accepts the actual numerical step size
and nothing else.

### 9. Finite results are not guaranteed

Integrators validate their arguments and the `AccelerationModel` return
contract, but they do not guarantee that the state they produce is finite.

A non-finite acceleration returned by a model violates that model's declared
contract. A non-finite state produced from valid inputs is numerical breakdown
of the integration run, which this project sets out to measure. Forward Euler
is the baseline because it degrades, and the timestep at which usable
propagation breaks down is evidence for the comparison required by
Objective 6. Treating that outcome as a contract violation would prevent the
evaluation harness from observing it. The exception would then mark each
method's stability limit, where no defect exists.

A non-finite result means the numerical representation no longer provides a
usable state. It does not indicate physical escape, because a hyperbolic
escape trajectory remains finite indefinitely.

Detection and policy belong to the caller, which has the context to respond. A
learning scenario should terminate and report numerical instability. An
evaluation harness should record the method, timestep and step index at which
divergence first appeared.

The caller must inspect a returned snapshot before committing it and before
invoking the next step. A non-finite state fed back into the acceleration
model fails inside the gravity layer on the following step, and the
information that breakdown began one step earlier is lost.

### 10. Empty systems

A zero-length system returns a zero-length result, consistent with the
aggregation and energy layers, and callers need no special case. The
acceleration model is still invoked and its return contract is still verified,
so a model that returns accelerations for a zero-length system is detected.

### 11. Forward Euler is the baseline method

The baseline is Forward (explicit) Euler, which advances both state components
from current values:

```
r(n+1) = r(n) + v(n) · Δt
v(n+1) = v(n) + a(n) · Δt
```

The method is named explicitly to distinguish it from semi-implicit
(symplectic) Euler, which uses the updated velocity for the position update
and has different energy behaviour. Attributing Forward Euler's energy drift
to "Euler" without qualification would misrepresent the Euler family in the
Objective 6 comparison.

## Consequences

### Positive

- Integrators can be tested with synthetic acceleration models and no gravity,
  which separates integrator defects from gravity-model defects.
- The Objective 6 comparison is a loop over integrator instances applied to
  one model and one initial snapshot. No reset logic is needed, because
  integrators hold no state.
- Runge–Kutta 4's intermediate stages are passed as ordinary arguments, and
  trial states cannot be mistaken for committed state.
- A failed step cannot leave the simulation in a partially updated state.
- The three integration strategies are substitutable over any conforming
  acceleration model, because the model contract is limited to what all three
  require.
- Non-finite values cannot enter integration through the acceleration model,
  so a non-finite state identifies the integration step as the point of
  breakdown.
- Numerical instability stays observable as an experimental outcome.
- Presentation speed cannot reach the numerical step size through the
  integrator API.

### Negative / Accepted Trade-offs

- Each step allocates at least a new result array and normally constructs new
  state snapshots for each body. At N ≤ 4 this cost is negligible.
- The engine must extract snapshots before a step and commit them afterwards.
  Integrators do not update bodies in place.
- Callers must inspect returned snapshots for finiteness (section 9). Both the
  engine and the evaluation harness must implement this check.
- Velocity-dependent acceleration models are excluded by contract, so
  aerodynamic drag or thrust would require revisiting section 4.
- Backward integration is unavailable until the restriction is revisited.
- The read-only array convention is documented but not enforced. Java arrays
  cannot be made immutable, and defensive copying at every boundary would be
  disproportionate at this scale.
- Verification of the `AccelerationModel` return contract is repeated in each
  integrator. A shared package-private helper may be extracted if the
  duplication proves real across the three implementations. An abstract base
  class is not intended, because the three methods share an interface and no
  is-a relationship, and a base class would reintroduce the constructor
  assumptions that section 3 avoids.