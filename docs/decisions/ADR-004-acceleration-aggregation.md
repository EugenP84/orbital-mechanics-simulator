# ADR-004: Multi-Body Acceleration Aggregation and Arbitrary-State Evaluation

**Status:** Accepted  
**Date:** 14.08.2026  
**Modules:** simulator-core (physics, simulation)

## Context

`GravityCalculator` (ADR-003) computes the acceleration on one body due to one
source and holds no policy about which bodies interact. Two questions about
the layer above it must be answered before any integrator can be implemented.

1. How are multi-body contributions aggregated? Newtonian gravity acts
   pairwise, and a body's total acceleration is the sum of contributions from
   every other body it interacts with. The aggregation can accumulate each
   body's acceleration independently, or evaluate each pair once and apply
   corresponding contributions to both bodies.
2. What state does the aggregation layer evaluate? Euler evaluates the
   derivative once, at the current state. Velocity Verlet evaluates it twice
   per step and Runge–Kutta 4 four times, at intermediate trial states that
   are not the committed state of any body. The interface must be able to
   evaluate accelerations at states that do not correspond to any
   `SimulationBody`'s current snapshot.

A third consideration constrains both answers. The project requires a
restricted three-body model for the NEO scenario: the Sun and Earth act
gravitationally on the asteroid, but the asteroid's back-reaction on the
primaries is neglected. Gravitational interaction in this project is not
always symmetric.

## Decision Drivers

- Support the restricted three-body approximation without a separate code
  path.
- Allow all three integrators to query the same physical model, which a fair
  comparison requires (Objective 6).
- Reuse the validated `accelerationBetween` API.
- Keep modelling policy out of the physical domain model (ADR-002) and out of
  the calculator (ADR-003).
- Avoid optimisation that is immaterial at the project's intended scale.

## Options Considered

### Option A: Per-target independent accumulation (chosen)

For each body, sum the accelerations contributed by every other body permitted
to act on it:

```
for each target body i:
    total = ZERO
    for each source body j ≠ i:
        if source j contributes gravity:
            total += accelerationBetween(position[i], position[j], mass[j])
    acceleration[i] = total
```

This performs N(N−1) directed evaluations. Asymmetric interaction becomes a
property of the source, which is what the restricted three-body model
requires:

| Source | Contributes gravity | Acts on |
|---|---|---|
| Sun | yes | Earth, NEO |
| Earth | yes | Sun, NEO |
| NEO | no | none |

The NEO is still accelerated by both primaries. Only its own contribution is
suppressed.

### Option B: Pairwise symmetric aggregation

Evaluate each unordered pair once and apply corresponding contributions to
both bodies. This performs N(N−1)/2 pair evaluations, half the number in
Option A.

Rejected. The saving is too small to justify the added complexity: at N = 4,
Option A performs twelve directed evaluations and Option B six pair
evaluations.

The structure also presumes symmetry. Where interaction is asymmetric, the
shared-contribution shortcut does not apply. Each direction would need its own
test, and the asymmetric case would need a second code path. The optimisation
would be lost in the NEO scenario, which the project treats as its primary
technical contribution.

Option B would also introduce a shared vector-force calculation, which needs
either a new force-vector API or equivalent conversion logic. The domain model
requires neither, and ADR-003 did not provide a vector force method.

### Interaction policy representation

Three representations of the restricted approximation were considered.

A `testParticle` flag on `CelestialBody` was rejected. The restricted
approximation is a modelling decision about a particular scenario. A real
asteroid has mass, and neglecting its back-reaction is an approximation the
scenario adopts. A flag on the body definition would put scenario-specific
policy into the physical domain model and weaken the separation established in
ADR-002.

A pair-specific policy such as `InteractionPolicy.allows(source, target)` was
rejected. The requirement is only that a body does or does not act as a
source. No requirement for pair-specific asymmetry exists.

A `contributesGravity` flag held by the aggregation configuration was chosen.
The flag answers one question, whether a body may act as a gravitational
source. Because it is source-oriented, the aggregation algorithm needs no
knowledge of NEOs, test particles or any other scenario concept.

## Decision

Adopt Option A: per-target independent accumulation over contributing sources.
Each pairwise contribution is delegated to
`GravityCalculator.accelerationBetween`.

Each body carries a `contributesGravity` flag in the aggregation
configuration. Fully coupled scenarios set it to true for all bodies, and the
restricted NEO scenario sets it to false for the asteroid. The flag costs one
test inside a loop the aggregator already performs.

The aggregation layer accepts state snapshots as an argument and does not read
`SimulationBody.state()`:

```
accelerationsAt(states) → accelerations
```

Body definitions, masses and interaction policy are configured once and held
by the aggregator. Only the trial state is passed per call. Euler, Velocity
Verlet and RK4 can then query an identical physical model at whatever states
their algorithms require, so observed differences can be attributed to the
integrators.

Complete `StateVector` snapshots are accepted even though Newtonian gravity
depends only on position. Integrators operate on complete state vectors and
generate complete trial states, so no integration strategy has to build a
separate position-only representation at every call. The aggregator reads only
`position()`.

Arrays carry the snapshots and the results, because integrators combine
intermediate results element-wise by index across bodies and the aggregator is
invoked up to four times per step. The arrays do not escape the physics and
integration layers, and their elements are immutable.

An `AccelerationModel` interface is not introduced in this ADR. It can be
extracted from the concrete implementation once the three integrators depend
on it.

## Amendment to ADR-003

ADR-003 noted that the sensitivity of the momentum-conservation diagnostic
depends on how aggregation is implemented, and left the aggregation approach
open. The difference between the two approaches is smaller than that note
suggests.

With finite inputs, the two separation vectors computed for a pair are
oppositely directed and their scalar distances are equal. Any discrepancy
between the two contributions arises mainly from the floating-point order of
multiplying by the two masses and from later accumulation. Pairwise symmetric
aggregation has rounding of its own, because converting a shared quantity into
two accelerations rounds each result, and summation order prevents exact
cancellation. Both approaches introduce asymmetry only at floating-point
level, while implementation defects produce much larger deviations.

Momentum conservation is a useful validation diagnostic under either
structure. It is not a criterion for selecting the aggregation strategy.
Option A is selected for its support of asymmetric restricted interactions and
its compatibility with integrator trial states.

## Scope of Energy-Based Validation

Asymmetric interaction determines which scenarios can support which parts of
Objective 6.

In the restricted configuration, the conventional total Newtonian mechanical
energy of the three represented bodies is not conserved by the implemented
model. The NEO keeps a finite positive mass while its gravitational influence
on the primaries is omitted, so the equations are not those of a fully coupled
three-body system generated by the symmetric pair potential. The expression

```
E = Σᵢ ½ mᵢ vᵢ²  −  Σᵢ<ⱼ G mᵢ mⱼ / rᵢⱼ
```

is not an invariant of those equations, and changes in a three-body total
calculated this way cannot be interpreted as integration error.

The Sun–Earth subsystem remains fully coupled within that scenario, so its
energy is still a meaningful diagnostic of the primaries' integration.

Objective 6 is satisfied by two measurements on two scenario families:

| Measurement | Scenario family | Validity |
|---|---|---|
| Energy conservation and momentum conservation | Fully coupled two-body and three-body systems, all bodies contributing gravity | Valid: closed Newtonian system generated by a symmetric pair potential |
| Trajectory accuracy against published CNEOS close-approach data | Restricted Sun–Earth–NEO system | Valid for the restricted model against the chosen CNEOS reference case, within the model's documented simplifications |

`EnergyCalculator` is specified for fully coupled Newtonian systems. If it is
applied to a configuration containing a non-contributing body, the result must
not be reported as evidence of integrator behaviour.

The invariant of the circular restricted three-body problem is the Jacobi
constant, evaluated in the co-rotating frame. Computing it is outside the
current scope and is a candidate for future work.

## Consequences

### Positive

- The restricted three-body model is expressed directly by the aggregation
  structure.
- All three integrators evaluate an identical physical model at their own
  trial states (Objective 6).
- The validated `accelerationBetween` API is reused unchanged, and no parallel
  force-vector code is introduced.
- Modelling policy stays outside `CelestialBody`, as ADR-002 requires.
- The aggregation algorithm contains no scenario-specific concepts.
- The division of Objective 6 into energy-based and trajectory-based
  validation is settled before implementation.

### Negative / Accepted Trade-offs

- Option A performs N(N−1) directed evaluations where pairwise aggregation
  would perform N(N−1)/2. The difference is negligible at N ≤ 4 and should be
  revisited if the body count grows substantially. The actual cost is measured
  during evaluation.
- Interaction policy is configuration that the caller must supply correctly. A
  scenario that wrongly marks a massive body as non-contributing would produce
  physically wrong results, and the physics layer cannot detect this because
  it holds no policy of its own.
- Total mechanical energy is meaningful only for fully coupled configurations.
  The whole-system energy diagnostic is unavailable for the NEO scenario,
  which needs a separate validation route (CNEOS comparison).