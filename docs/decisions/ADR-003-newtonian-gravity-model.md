# ADR-003: Newtonian Gravity Model and Acceleration-Based Physics Interface

**Status:** Accepted  
**Date:** 07.08.2026  
**Modules:** simulator-core (physics, math)

## Context

The simulation engine requires a gravitational model and a defined interface
between that model and the numerical integrators. Two questions must be
settled before any integrator is implemented, because both determine what the
integrators consume and what the evaluation measures.

1. Is the gravitational force law modified for numerical robustness? Newton's
   law of universal gravitation diverges as separation approaches zero.
   Large-N simulation codes commonly introduce a softening length ε, which
   replaces the point-mass potential with a smoothed equivalent and prevents
   unbounded accelerations during close encounters.
2. Does the physics layer expose force or acceleration? The integrators
   propagate equations of motion expressed in acceleration, whereas the
   gravitational law is conventionally stated as a force.

The project methodology commits to Newtonian mechanics, to comparative
evaluation of Euler, Velocity Verlet and RK4 in terms of energy conservation
and trajectory accuracy, and to validation of simulated NEO close-approach
predictions against published NASA CNEOS data. These commitments constrain
both answers.

The model builds on ADR-002, which separated immutable body definitions from
replaceable state snapshots to support reproducible integrator experiments.

## Decision Drivers

- Compare the integrators against an unmodified physical law (Objective 6).
- Keep simulated quantities directly comparable with the analytically derived
  Newtonian values used for validation.
- Match the interface between physics and integrators to the mathematics of
  the equations of motion.
- Keep the physics layer stateless and free of side effects.
- Prefer explicit failure over silent production of physically meaningless
  values.

## Options Considered

### Option A: Softened gravitational model

Introduce a softening length ε so that accelerations remain bounded as
separation approaches zero. Plummer-type softening is a widely used
formulation.

Rejected. Softening is standard practice in large-N astrophysical simulation,
where close encounters are frequent, and a softened force law still conserves
its own total energy. It does not suit this project's evaluation. The
integrators would be compared under a modified gravitational model instead of
the Newtonian inverse-square model specified in the methodology. Energy and
trajectory results would also be harder to compare with the analytical
Newtonian quantities (circular-orbit velocity, escape velocity and
gravitational potential energy) used for validation and for the adaptive hint
system. The simulation runs at N ≤ 4 with configured scenarios, so the
frequent close encounters that motivate softening do not arise.

### Option B: Force-based physics interface

Expose gravitational force as the primary quantity and divide by mass within
each integrator.

Rejected as the primary interface. Numerical propagation of the equations of
motion requires acceleration. A force-based primary interface would place a
conversion step inside every integrator and would require a mass for every
accelerated body. Force is still useful as a reported physical quantity and is
kept as a secondary API.

### Option C: Exact Newtonian gravity with acceleration as the primary quantity (chosen)

Implement the unmodified inverse-square law. Gravitational acceleration is the
primary quantity consumed by the integrators, and force magnitude is available
as a secondary quantity for verification and user-facing display.

## Decision

Adopt Option C.

The exact inverse-square law is implemented, with no gravitational softening.

Gravitational acceleration is the primary quantity exposed by the physics
layer, because it is the quantity the equations of motion require. The mass of
the accelerated body does not appear in the acceleration expression.
Restricted test-particle propagation is supported as a result: a NEO's
trajectory is determined by the masses of the primary bodies, and its
back-reaction on those bodies is neglected. The domain model may still hold an
estimated positive mass for such a body where one is available, consistent
with the `CelestialBody` invariant in ADR-002.

Force magnitude is exposed for physical verification and for reporting
quantities to the user interface. A vector force method is not provided,
because nothing currently requires it.

`GravityCalculator` computes a gravitational interaction on request and holds
no policy about which interactions exist. Whether a given pair interacts is
decided by the force-aggregation layer or the scenario configuration. This
includes the restricted three-body policy, in which a test particle exerts no
back-reaction.

`GravityCalculator` validates its own public contract independently of
invariants guaranteed elsewhere. Source masses must be positive and finite.
The derived separation must be positive and finite, so coincident positions
and non-finite geometries are rejected before NaN or infinite values can
propagate through the simulation. The computed acceleration is also checked
for finiteness before it is returned, since finite inputs at very small
separations can still overflow.

This validation sits at the physics boundary and not in `Vector3D`. `Vector3D`
accepts any `double` without constructor validation because vectors are
constructed several times per body per simulation step. Per-construction
finiteness checks would be a recurring cost for a guarantee that is needed
only where values enter or leave the physical model.

Gravitational dynamics and system energy quantities are separate
responsibilities, implemented as `GravityCalculator` and `EnergyCalculator` in
the `physics` package. The location of the derived orbital quantities needed
by the learning scenarios and hint system (circular-orbit velocity, escape
velocity and surface gravity) is not decided here.

## Consequences

### Positive

- Integrator comparison measures numerical error against an unmodified
  physical law, so energy-conservation and trajectory-accuracy results can be
  attributed to the integrators.
- Simulated quantities can be compared directly with analytically derived
  Newtonian values. Both the CNEOS validation (Objective 6) and the inference
  rules of the adaptive hint system depend on this.
- The acceleration-primary interface matches the mathematical form the
  integrators require, so the `Integrator` abstraction has no mass-handling
  concerns.
- Restricted test-particle configurations are supported without special-casing
  the domain model.
- The physics layer is stateless and its methods are pure functions of their
  arguments, so it can be tested against known physical values.
- The gravitational model never returns a non-finite acceleration and cannot
  become a source of NaN or infinite values in the simulation state.

### Accepted Trade-offs

- Close-encounter fidelity is bounded by the timestep. Collision detection
  operates within a discrete numerical simulation, and a sufficiently large
  timestep may allow two bodies to pass through one another between successive
  evaluations without an overlap being detected. Rejecting coincident and
  non-finite geometries guards the singular cases only. Accelerations may
  become numerically extreme at small but non-zero separations before that
  point is reached. Timestep calibration and scenario parameter constraints
  are part of simulation validation, and the relationship between timestep and
  close-encounter accuracy is reported as an evaluation finding.
- Validation logic for mass is duplicated between `CelestialBody` and
  `GravityCalculator`. A public API over primitive values cannot rely on
  invariants enforced elsewhere.
- `Vector3D` provides no finiteness guarantee, so any future consumer of
  vector values that requires one must validate at its own boundary, as the
  physics layer does.
- Without softening, scenarios must be configured so that close encounters are
  either physically meaningful (Level 3 collision avoidance) or avoided by
  construction.

## Notes for Evaluation

Three details affect the correctness and interpretation of the
energy-conservation measurement required by Objective 6.

1. Gravitational potential energy must be summed over unique pairs (i < j).
   Summing over all ordered pairs double-counts the interaction. The resulting
   total stays self-consistent over time, so the error cannot be detected from
   a conservation plot alone.

2. Kinetic energy is frame-dependent. Validation scenarios for the integrator
   comparison should be initialised in the centre-of-mass inertial frame.
   Energy should then be calculated from the propagated states in that fixed
   frame, without re-centring the system, so that numerical centre-of-mass or
   momentum drift stays visible in the measurement. Total energy is conserved
   in any inertial frame, and a constant centre-of-mass translational term is
   an additive constant that does not affect conservation.

3. Total linear momentum is a conditional diagnostic. In fully coupled N-body
   validation scenarios where all pairwise interactions are represented
   symmetrically, total linear momentum should stay conserved up to
   floating-point error. Significant drift then helps distinguish an
   implementation defect from the expected numerical energy error of an
   integration method. Two conditions limit this diagnostic and must be stated
   wherever it is used:

  - It does not apply unchanged to restricted test-particle configurations
    such as the Sun–Earth–NEO model. The test particle's back-reaction on the
    primary bodies is neglected there, so total momentum across the
    represented bodies is not a conserved quantity of the computational
    model.
  - Its sensitivity depends on how the force-aggregation layer is
    implemented. If each body's acceleration is computed independently, equal
    and opposite contributions cancel only through floating-point arithmetic,
    and small momentum drift is expected numerical noise. If each pair is
    evaluated once and equal and opposite contributions are applied to both
    bodies, Newton's third law holds by construction and momentum is
    conserved to within a few units in the last place regardless of
    integrator, so any drift indicates a defect. The aggregation approach is
    outside the scope of this ADR.