# ADR-003: Newtonian Gravity Model and Acceleration-Based Physics Interface

**Status:** Accepted  
**Date:** 07.08.2026  
**Modules:** simulator-core (physics, math)  

---

## Context

The simulation engine requires a gravitational model and a defined interface
between that model and the numerical integrators. Two questions must be
settled before any integrator is implemented, because both determine what the
integrators consume and what the evaluation subsequently measures.

**Question 1 — Is the gravitational force law modified for numerical
robustness?** Newton's law of universal gravitation diverges as separation
approaches zero. Large-N simulation codes commonly introduce a softening
length ε, replacing the point-mass potential with a smoothed equivalent, to
prevent unbounded accelerations during close encounters.

**Question 2 — Does the physics layer expose force or acceleration?** The
equations of motion propagated by the integrators are expressed in
acceleration; the gravitational law is conventionally stated as a force.

The project methodology commits to Newtonian mechanics, to comparative
evaluation of Euler, Velocity Verlet and RK4 in terms of energy conservation
and trajectory accuracy, and to validation of simulated NEO close-approach
predictions against published NASA CNEOS data. These commitments constrain
both answers.

This decision continues the trail established by ADR-002 which separated
immutable body definitions from replaceable state snapshots specifically to
support reproducible integrator experiments; ADR-003 establishes the physical
law that those state transitions approximate.

## Decision Drivers

- Preserve a clean experimental baseline for integrator comparison
  (Objective 6).
- Keep simulated quantities directly comparable with analytically derived
  Newtonian values used for validation.
- Match the interface between physics and integrators to the mathematics of
  the equations of motion.
- Maintain the stateless, side-effect-free character of the physics layer.
- Prefer explicit failure over silent production of physically meaningless
  values.

## Options Considered

### Option A — Softened gravitational model

Introduce a softening length ε so that accelerations remain bounded as
separation approaches zero, with Plummer-type softening being a widely used
formulation.

**Rejected.**

Softening is not, in itself, physically inconsistent: a softened force law
derived from a corresponding softened potential conserves its own total energy
exactly, and softening is standard practice in large-N astrophysical
simulation where close encounters are frequent and individually
uninteresting.

The objection is specific to this project's evaluation design. Applying
softening would mean that the integrators are no longer being compared under
the Newtonian inverse-square model specified in the project methodology, but
under a deliberately modified gravitational model. Energy-conservation and
trajectory-accuracy results would then be less directly comparable with the
analytical Newtonian quantities — circular-orbit velocity, escape velocity,
gravitational potential energy — used for validation and for the adaptive hint
system. The simulation also operates at N ≤ 4 with deliberately configured
scenarios, so the frequent-close-encounter conditions that motivate softening
do not arise.

### Option B — Force-based physics interface

Expose gravitational force as the primary quantity and divide by mass within
each integrator.

**Rejected as the primary interface.**

Numerical propagation of the equations of motion requires acceleration. A
force-based primary interface would place a conversion step inside every
integrator and would require a mass for every accelerated body. Force remains
useful as a reported physical quantity and is retained as a secondary API.

### Option C — Exact Newtonian gravity with acceleration as the primary quantity (Chosen)

Implement the unmodified inverse-square law, exposing gravitational
acceleration as the primary quantity consumed by the integrators, with force
magnitude available as a secondary quantity for verification and user-facing
display.

## Decision

Adopt Option C.

**Gravitational model.** No gravitational softening is applied. The exact
inverse-square law is implemented, so that simulated behaviour remains
directly comparable with the analytical Newtonian quantities used for
validation and for scenario feedback.

**Primary interface.** Gravitational acceleration is the primary quantity
exposed by the physics layer, because acceleration is the quantity directly
required by the equations of motion and therefore represents the natural
interface between the gravitational model and the numerical integrators.

The mass of the accelerated body does not appear in the acceleration
expression. This naturally supports restricted test-particle propagation,
where a NEO's trajectory is determined by the masses of the primary bodies and
its back-reaction on those bodies is deliberately neglected. The domain model
may nevertheless retain an estimated positive mass as a physical property
where one is available, consistent with the `CelestialBody` invariant
established in ADR-002.

**Secondary interface.** Force magnitude is exposed for physical verification
and for reporting quantities to the user interface. A vector force method is
not provided at this stage; it will be added only if a requirement for it
arises.

**Scope of the calculator.** `GravityCalculator` computes gravitational
interaction when asked and holds no policy about which interactions exist.
The decision of whether a given pair interacts — in particular the restricted
three-body policy in which a test particle exerts no back-reaction — belongs
to the force-aggregation layer or scenario configuration, not to the
calculator.

**Validation of primitive and derived inputs.** `GravityCalculator` validates
its own public contract independently of invariants guaranteed elsewhere.
Source masses must be positive and finite. Derived separation must be positive
and finite: coincident positions and non-finite geometries are rejected rather
than being allowed to propagate NaN or infinite values through the simulation.
The computed acceleration is additionally verified to be finite before being
returned, since finite inputs at sufficiently small separations can still
overflow.

This validation is located at the physics boundary and not in
`Vector3D`. `Vector3D` accepts any `double` without constructor validation
because vectors are constructed several times per body per simulation step,
making per-construction finiteness checks a recurring cost for a guarantee
that is only required where values enter or leave the physical model.
`GravityCalculator` therefore cannot assume its inputs are physically valid,
and checks them explicitly.

**Component separation.** Gravitational dynamics and system energy quantities
are distinct responsibilities and are implemented as separate components
(`GravityCalculator` and `EnergyCalculator`) within the `physics` package.
Derived orbital quantities required by the learning scenarios and hint system
(circular-orbit velocity, escape velocity, surface gravity) will be located
when that requirement is reached, rather than being placed speculatively.

## Consequences

### Positive

- Integrator comparison measures numerical error against an unmodified
  physical law, so energy-conservation and trajectory-accuracy results are
  attributable to the integrators rather than to a modified force model.
- Simulated quantities remain directly comparable with analytically derived
  Newtonian values, supporting both CNEOS validation (Objective 6) and the
  analytically grounded inference rules of the adaptive hint system.
- The acceleration-primary interface matches the mathematical form the
  integrators require, keeping the `Integrator` abstraction free of
  mass-handling concerns.
- Restricted test-particle configurations are supported without special-casing
  the domain model.
- The physics layer remains stateless and its methods pure functions of their
  arguments, making it straightforward to test against known physical values.
- The gravitational model does not return non-finite accelerations, preventing it from becoming a source of NaN or infinite values propagated through the simulation state.

### Accepted Trade-offs

- **Close-encounter fidelity is bounded by the timestep.** Collision detection
  operates within a discrete numerical simulation: a sufficiently large
  timestep may allow two bodies to pass through one another between successive
  evaluations without an overlap being detected. Rejection of coincident and
  non-finite geometries guards the singular cases only; accelerations may
  become numerically extreme at small but non-zero separations before that
  point is reached. Timestep calibration and scenario parameter constraints
  therefore form part of simulation validation, and the relationship between
  timestep and close-encounter accuracy is a reportable evaluation finding
  rather than a solved problem.
- Validation logic for mass is duplicated between `CelestialBody` and
  `GravityCalculator`. This is accepted deliberately: a public API over
  primitive values cannot rely on invariants enforced elsewhere.
- `Vector3D` provides no finiteness guarantee, so any future consumer of
  vector values that requires one must validate at its own boundary, as the
  physics layer does.
- Without softening, scenarios must be configured such that close encounters
  are either physically meaningful (Level 3 collision avoidance) or avoided by
  construction.

## Notes for Evaluation

Three details affect the correctness and interpretation of the
energy-conservation measurement required by Objective 6, and are recorded here
so that they are not overlooked when `EnergyCalculator` and the
force-aggregation layer are implemented.

**1. Gravitational potential energy must be summed over unique pairs**
(i < j). Summing over all ordered pairs double-counts the interaction and
yields a total that remains internally self-consistent over time, and is
therefore not detectable by inspecting a conservation plot alone.

**2. Kinetic energy is frame-dependent.** For the integrator comparison,
validation scenarios will be initialised in the centre-of-mass inertial frame
where appropriate. Energy will then be calculated from the propagated states
in that fixed frame rather than by continually re-centring the system, so that
numerical centre-of-mass or momentum drift remains observable rather than
being removed from the measurement. Total energy is conserved in any inertial
frame; a constant centre-of-mass translational term is an additive constant
and does not affect conservation.

**3. Total linear momentum is a conditional diagnostic.** In fully coupled
N-body validation scenarios where all pairwise interactions are represented
symmetrically, total linear momentum should remain conserved up to
floating-point error, so significant drift helps distinguish an implementation
defect from the expected numerical energy error of an integration method. Two
conditions limit this diagnostic and must be stated wherever it is used:

- It does not apply unchanged to restricted test-particle configurations such
  as the Sun–Earth–NEO model, where the test particle's back-reaction on the
  primary bodies is deliberately neglected and total momentum across the
  represented bodies is therefore not a conserved quantity of the
  computational model.
- Its sharpness depends on how the force-aggregation layer is implemented. If
  each body's acceleration is computed independently, cancellation of equal
  and opposite contributions is arithmetically emergent rather than
  structurally enforced, and small momentum drift is expected numerical noise.
  If instead each pair is evaluated once and equal and opposite contributions
  are applied to both bodies, Newton's third law holds structurally and
  momentum is conserved to within a few units in the last place regardless of
  integrator, making drift a sharp indicator of defect. The choice between
  these will be made and recorded when the force-aggregation layer is
  designed; ADR-003 does not pre-commit to either.

## Guiding Principle

Model the physical law exactly as specified in the methodology; expose the
quantity the mathematics requires; validate at every public boundary; and
record limitations as evaluation topics rather than concealing them behind
numerical convenience.
