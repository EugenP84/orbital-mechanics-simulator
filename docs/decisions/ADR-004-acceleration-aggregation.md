# ADR-004: Multi-Body Acceleration Aggregation and Arbitrary-State Evaluation

**Status:** Accepted  
**Date:** 2026-08-14  
**Modules:** simulator-core (physics, simulation)  
**Related:** Project Proposal §2.2 (Simulation Engine), §3.3 (NEO Integration); Objective 2 (integration methods), Objective 4 (NEO integration), Objective 6 (integrator comparison and CNEOS validation); ADR-002 (definition–state separation), ADR-003 (gravitational model)

---

## Context

`GravityCalculator` (ADR-003) computes the acceleration on one body due to one
source and deliberately holds no policy about which bodies interact. Before
any integrator can be implemented, two questions must be answered about the
layer that sits above it.

**Question 1 — How are multi-body contributions aggregated?** Newtonian
gravity acts pairwise; a body's total acceleration is the sum of contributions
from every other body it interacts with. The aggregation may be structured
either by accumulating each body's acceleration independently, or by
evaluating each pair once and applying corresponding contributions to both
bodies.

**Question 2 — What state does the aggregation layer evaluate?** Euler
evaluates the derivative once, at the current state. Velocity Verlet evaluates
it twice per step, and Runge–Kutta 4 four times, at intermediate *trial*
states that are not the committed state of any body. The interface must
therefore be able to evaluate accelerations at states that do not correspond
to any `SimulationBody`'s current snapshot.

A third consideration constrains both answers. The project requires a
restricted three-body model for the NEO scenario (Proposal §3.3, §12): the
Sun and Earth act gravitationally on the asteroid, but the asteroid's
back-reaction on the primaries is deliberately neglected. Gravitational
interaction in this project is therefore **not universally symmetric**.

## Decision Drivers

- Support the restricted three-body approximation as a first-class
  configuration, not a special case bolted on.
- Allow all three integrators to query the same physical model, which is a
  precondition for a fair comparison (Objective 6).
- Reuse the validated `accelerationBetween` API rather than introducing
  parallel force machinery.
- Keep modelling policy out of the physical domain model (ADR-002) and out of
  the calculator (ADR-003).
- Avoid optimisation that is immaterial at the project's intended scale.

## Options Considered

### Option A — Per-target independent accumulation (chosen)

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

This performs N(N−1) directed evaluations. Asymmetric interaction is expressed
naturally as a property of the source, which is precisely what the restricted
three-body model requires:

| Source | Contributes gravity | Acts on |
|---|---|---|
| Sun | yes | Earth, NEO |
| Earth | yes | Sun, NEO |
| NEO | no | — |

The NEO remains accelerated by both primaries; only its own contribution is
suppressed.

### Option B — Pairwise symmetric aggregation

Evaluate each unordered pair once and apply corresponding contributions to
both bodies, performing N(N−1)/2 pair evaluations — half the evaluations of
Option A.

**Rejected.**

The saving is immaterial at the intended scale. At N = 4, Option A performs
twelve directed evaluations rather than six unordered-pair evaluations. This
difference is too small to justify additional architectural complexity;
performance will be measured separately during evaluation rather than
predicted here.

More importantly, the structure *presumes* symmetry. Where interaction is
asymmetric, the shared-contribution shortcut does not apply, so a per-pair
test for each direction would be needed and the asymmetric case would require
a second code path regardless. The optimisation would therefore be defeated in
exactly the scenario the project treats as its primary technical contribution.

Option B would also naturally introduce a shared vector-force calculation,
requiring either a new force-vector API or equivalent conversion logic.
Neither is currently required by the domain model, and ADR-003 declined to
introduce a vector force method until a requirement existed.

### Interaction policy representation

The restricted approximation is a **modelling decision about a particular
scenario**, not a physical property of an object: a real asteroid has mass,
and neglecting its back-reaction is an approximation the scenario adopts.
Placing a flag such as `testParticle` on `CelestialBody` would therefore
introduce scenario-specific policy into the physical domain model, weakening
the separation established in ADR-002.

The policy is expressed instead as `contributesGravity` — phrased as the
question actually being asked, *may this body act as a gravitational source?*
— held by the aggregation configuration rather than by the body definition.
Because the flag is source-oriented, the aggregation algorithm requires no
knowledge of NEOs, test particles or any other scenario concept; it asks only
whether a given source contributes.

A more general representation, such as a pair-specific
`InteractionPolicy.allows(source, target)`, is deliberately not adopted. The
present requirement is that a body either does or does not act as a source; no
requirement for pair-specific asymmetry exists, and a general interaction
matrix would be an abstraction without a driver.

## Decision

**Aggregation.** Adopt Option A: per-target independent accumulation over
contributing sources, delegating each pairwise contribution to
`GravityCalculator.accelerationBetween`.

**Interaction policy.** Each body carries a `contributesGravity` flag within
the aggregation configuration, not within `CelestialBody`. Fully coupled
scenarios set it true for all bodies; the restricted NEO scenario sets it
false for the asteroid.

**Arbitrary-state evaluation.** The aggregation layer accepts state snapshots
as an argument rather than reading `SimulationBody.state()`:

```
accelerationsAt(states) → accelerations
```

Body definitions, masses and interaction policy are configured once and held
by the aggregator; only the varying trial state is passed per call. This
allows Euler, Velocity Verlet and RK4 to query an identical physical model at
whatever states their algorithms require, which is a precondition for
attributing observed differences to the integrators rather than to differences
in the model they evaluate.

Complete `StateVector` snapshots are accepted even though Newtonian gravity
currently depends only on position. Integrators operate on complete state
vectors and generate complete trial states; accepting those snapshots avoids
requiring each integration strategy to construct a separate position-only
representation at every call. The aggregator reads only `position()` and does
not treat velocity as part of the gravitational law.

**Container choice.** The architectural decision is *arbitrary snapshots in,
corresponding accelerations out*; the Java carrier is an implementation
detail. Arrays are used, because integrators combine intermediate results
element-wise by index across bodies and the aggregator is invoked up to four
times per step. The arrays do not escape the physics and integration layers,
and their elements are immutable.

**Interface abstraction deferred.** An `AccelerationModel` interface would be
a natural abstraction once three integrators depend on it. It is not
introduced now: it will be extracted from the concrete implementation when
real callers exist, rather than being defined speculatively.

### How the two decisions reinforce each other

The aggregation choice and the arbitrary-state interface are not independent.
Because the aggregator holds masses and interaction policy as configuration
and receives only states, expressing the `contributesGravity` condition costs
a single test inside a loop the aggregator already performs. Under pairwise
symmetric aggregation reading committed body state, supporting asymmetric
interaction and supporting trial-state evaluation would each have required
structural change. Taken together, the two decisions make the restricted model
and multi-stage integration straightforward rather than merely possible.

## Amendment to ADR-003

ADR-003 recorded that the sharpness of the momentum-conservation diagnostic
depends on whether aggregation is structurally symmetric, and deferred the
choice to this decision. Subsequent analysis showed that the contrast was
overstated.

Under ordinary finite IEEE 754 inputs, the two separation vectors computed for
a pair are oppositely directed and their scalar distances are equal. Any
discrepancy in the resulting contributions therefore arises principally from
the differing floating-point order of multiplying by the two masses and from
subsequent accumulation. Pairwise symmetric aggregation is not exact either:
converting a shared quantity into two accelerations introduces its own
rounding, and summation order prevents exact cancellation in any case. Both
approaches therefore introduce only floating-point-level asymmetry under
normal conditions, while genuine implementation defects produce substantially
larger deviations.

Momentum conservation consequently remains a useful validation diagnostic
under either structure, but is **not** a criterion for selecting the
aggregation strategy. ADR-004 selects Option A on the basis of support for
asymmetric restricted interactions and compatibility with arbitrary integrator
trial states.

ADR-003 is not rewritten; this amendment records that the reasoning was
refined by later analysis.

## Scope of Energy-Based Validation

A consequence of asymmetric interaction must be recorded here, because it
determines which scenarios can support which parts of Objective 6.

In the restricted configuration, the conventional total Newtonian mechanical
energy of all three represented bodies is **not a conserved invariant of the
implemented model**. The NEO retains a finite positive mass while its
reciprocal gravitational influence on the primaries is deliberately omitted,
so the resulting equations are not those of a fully coupled closed three-body
Newtonian system generated by the ordinary symmetric pair potential. The
conventional expression

```
E = Σᵢ ½ mᵢ vᵢ²  −  Σᵢ<ⱼ G mᵢ mⱼ / rᵢⱼ
```

is therefore not an invariant of those equations, and changes in a naïvely
calculated three-body total cannot be interpreted as integration error.

The Sun–Earth subsystem nevertheless remains fully coupled within that
scenario, so its energy remains a meaningful diagnostic of the primaries'
integration. It is only a putative total over Sun, Earth and a finite-mass
non-contributing NEO that must not be treated as a conserved invariant.

Objective 6 is consequently satisfied by two distinct measurements on two
distinct scenario families:

| Measurement | Scenario family | Validity |
|---|---|---|
| Energy conservation; momentum conservation | Fully coupled two-body and three-body systems, all bodies contributing gravity | Valid — closed Newtonian system generated by a symmetric pair potential |
| Trajectory accuracy against published CNEOS close-approach data | Restricted Sun–Earth–NEO system | Valid for assessing the trajectory accuracy of the project's specified restricted model against the chosen CNEOS reference case, subject to the model's documented simplifications |

`EnergyCalculator` is therefore specified for fully coupled Newtonian systems.
Where it is applied to a configuration containing a non-contributing body, the
resulting total is not a conserved quantity and must not be reported as
evidence of integrator behaviour.

For completeness, the invariant of the circular restricted three-body problem
is the Jacobi constant, evaluated in the co-rotating frame. Computing it is
outside the current scope; it is noted as the correct conserved quantity for
that configuration and as a candidate for future work.

## Consequences

### Positive

- The restricted three-body model is expressed directly by the aggregation
  structure rather than as an exception to it.
- All three integrators evaluate an identical physical model at their own
  trial states, so measured differences are attributable to the integration
  methods (Objective 6).
- The validated `accelerationBetween` API is reused unchanged; no parallel
  force-vector machinery is introduced.
- Modelling policy remains outside `CelestialBody`, preserving ADR-002.
- The aggregation algorithm contains no scenario-specific concepts.
- The division of Objective 6 into energy-based and trajectory-based
  validation is settled before implementation, rather than being discovered
  when energy figures for the NEO scenario behave unexpectedly.

### Negative / Accepted Trade-offs

- N(N−1) directed evaluations rather than N(N−1)/2 pair evaluations. Judged
  immaterial at N ≤ 4; would warrant revisiting if the body count grew
  substantially. Actual cost will be measured during evaluation.
- Interaction policy is configuration the caller must supply correctly. A
  scenario that wrongly marks a massive body as non-contributing would produce
  physically wrong results that the physics layer cannot detect, since it
  holds no policy of its own.
- Total mechanical energy is meaningful only for fully coupled configurations,
  so the whole-system energy diagnostic is unavailable for the NEO scenario
  and a separate validation route (CNEOS comparison) is required there.

## Guiding Principle

Let the aggregation structure follow the modelling requirements rather than an
optimisation that is immaterial at the project's scale; keep scenario policy
out of both the physical model and the calculator; and establish which
measurements are valid for which configurations before implementing the
measurements.
