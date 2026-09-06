# ADR-009: Orbital State Analysis and Element Semantics

**Status:** Accepted  
**Date:** 06.09.2026  
**Modules:** simulator-core (new `orbital` package), consumed by simulator-learning and later simulator-app  
**Related:** Project Proposal §4.1 (2D N-body engine, NeoWs integration), §4.2 (Level 1 Stable Orbit, Level 4 NEO Simulation), §7 (NEO Simulation Module); Objective 6 (CNEOS close-approach validation); ADR-002 (definition–state separation), ADR-003 (Newtonian gravity), ADR-006 (engine boundary), ADR-007 (external NEO data boundary), ADR-008 (learning architecture; §7 places instantaneous orbital mathematics in the core)

---

## Context

ADR-008 §7 draws the boundary between instantaneous orbital mathematics, which
belongs in `simulator-core`, and history-dependent scenario progress, which
belongs in `simulator-learning`. It also records that Level 1 cannot be
completed until the core provides the instantaneous quantities its completion
policy needs — eccentricity for orbital shape, an escape criterion, and an
angular quantity from which the learning layer accumulates completed
revolutions.

Level 4 will additionally require the reverse direction: constructing an initial
Cartesian state from published orbital elements for a real near-Earth object.

Both directions rest on the same conventions — which body is the reference,
which frame, which gravitational parameter, which anomaly, at what epoch, in
what units, and what happens in degenerate configurations. Defining those
conventions twice, in two separate decisions, would risk two subtly different
notions of "orbital elements" in one system. This ADR therefore settles the
semantics once, while implementing only the direction that has a consumer today.

A scope correction discovered during this analysis is recorded here so it is not
lost: the NeoWs `/feed` endpoint integrated under ADR-007 supplies close-approach
and physical data but **not** orbital elements, which are served by the object
lookup endpoint. Level 4's requirement to initialise a NEO from its real orbital
parameters therefore depends on a data-acquisition capability that does not yet
exist. This is recorded as a Level 4 prerequisite; it does not block Level 1 and
is not addressed in this decision.

## Decision Drivers

- Level 1 needs orbital shape, an escape criterion, and revolution tracking.
- Level 4 will need elements-to-state construction against real published data,
  where epoch and anomaly conventions determine whether the result is physically
  meaningful.
- Objective 6 compares simulated close approaches against published CNEOS data,
  which is only legitimate if the initial state and its epoch are coherently
  defined.
- The core must remain a computational layer: it performs orbital mathematics
  and does not decide which physical model applies.
- Degenerate configurations are ordinary in this application — Level 1 aims
  directly at a circular orbit, and its scenarios are planar — so degeneracy
  handling is a primary concern, not an edge case.
- The project does not need, and should not acquire, a general celestial
  mechanics library.

## Decision

### 1. A new `core/orbital` package

Orbital analysis lives in `io.github.eugenp84.orbitalsim.core.orbital`,
alongside the existing physics rather than in the learning module. It is
deterministic, provider-independent mathematics: the same computation serves a
learning scenario, a NEO trajectory, and any future consumer, and none of them
is a pedagogical concern (ADR-008 §7).

### 2. The formulation is general 3D vector mathematics, not planar-specific

The proposal describes a 2D simulation, but the implemented computational
representation is already three-dimensional (`Vector3D` positions, velocities
and accelerations), and a planar orbit is simply the case where the state
remains in a plane. The separation is deliberate:

```
physics representation      3D Cartesian
educational scenarios       predominantly planar
visualisation               2D projection
```

Orbital quantities are therefore derived from vector formulations — specific
angular momentum, the eccentricity vector — which reduce correctly for planar
orbits and remain valid if an inclined NEO trajectory or a 3D renderer is
introduced later.

A planar-specific element model is explicitly rejected. Beyond the loss of
generality, it would require encoding a convention as though it were physics:
in an orbit lying in the reference plane, the inclination is genuinely zero, but
the right ascension of the ascending node is **undefined**, not zero — the node
vector `n = ẑ × h` vanishes identically, so the expression is indeterminate.
Assigning it zero is a convention; a planar model would bake that convention in
silently.

### 3. Calculations operate on relative state, with the reference stated by the caller

All orbital quantities are computed from the position and velocity of one body
**relative to a stated reference body**:

```
r = r_body − r_reference
v = v_body − v_reference
```

The caller identifies the reference; the mathematics does not infer it. This
makes the quantities correct in a moving frame — a planet orbiting a star that
is itself in motion — and makes explicit that "the orbit" is always an orbit
*about something*.

A consequence worth testing: every quantity defined here is invariant under a
common spatial translation and a common Galilean velocity boost applied to both
bodies, because only the difference enters.

### 4. The gravitational parameter is supplied explicitly by the caller

Orbital calculations receive μ as a validated `double` in **m³/s²**, positive
and finite. They do not compute it from a body's mass.

The appropriate μ depends on the physical model, and only the caller knows which
model applies:

```
fully coupled two-body (Level 1 star + planet)    μ = G(M_primary + M_secondary)
restricted test particle (Level 4 NEO)            μ = G·M_primary
```

These are not competing formulas; they describe different models. Placing the
choice at the call site keeps the principle clean: **the orbital layer performs
mathematics; it does not decide which gravitational model applies.**

A dedicated `GravitationalParameter` value type is deliberately not introduced.
The rest of the core expresses masses, distances, velocities and times as
validated primitive doubles, so a single-field wrapper here would relocate the
same validation without providing dimensional safety anywhere else. If μ later
circulates widely enough to be confused with other scalars, the type can earn
its place then; there is no such evidence now.

All quantities are SI throughout, consistent with the rest of the core.

### 5. Quantities implemented in this increment

From a relative state and μ, the following are computed:

| Quantity | Definition | Defined when |
|---|---|---|
| specific angular momentum | `h = r × v` | always, including `h = 0` (see below) |
| specific orbital energy | `ε = v²/2 − μ/r` | `r > 0` |
| eccentricity vector | `e = ((v² − μ/r)·r − (r·v)·v) / μ` | `r > 0` |
| eccentricity | `e = ‖e‖` | `r > 0` |
| semi-major axis | `a = −μ / (2ε)` | `ε ≠ 0` |

**The angular-momentum vector is defined for every valid non-coincident
relative state, including the degenerate radial case where `r` and `v` are
parallel and `h = 0`.** In that case there is no orbital plane, so every
quantity requiring an orbital-plane normal — inclination, the node vector and
anything derived from it — is undefined. An implementation must not normalise a
zero angular-momentum vector while computing such quantities. This case is
reachable in Level 1: a learner who sets tangential velocity to zero produces
purely radial infall.

**These diagnostics are not restricted to bound orbits.** Specific energy,
angular momentum and eccentricity remain meaningful for escape trajectories, and
Level 1 must be able to recognise escape, so an elliptic-only API would be wrong.
Semi-major axis carries its conventional meaning for hyperbolic trajectories
(negative `a`); the problematic case is the parabolic boundary, where `ε → 0`
and `a` is unbounded. That case is reported as undefined rather than returned as
a large or infinite number. No hyperbolic or parabolic anomaly machinery is
required to preserve these vector diagnostics.

**The bound/unbound criterion** is expressed through specific energy relative to
the selected two-body model and the same μ:

```
ε < 0   bound
ε = 0   parabolic boundary
ε > 0   unbound (escape)
```

Energy, not eccentricity, is the criterion, and the radial case shows why this
matters rather than being a stylistic preference: for rectilinear motion the
eccentricity is exactly 1 whatever the energy, so an `e ≥ 1` test would classify
a bound planet falling straight into the star as escaping. Since that
configuration is reachable in Level 1, an eccentricity-based escape test would
be wrong in a case the scenario must handle.

This is a defensible escape criterion for Level 1's two-body scenario. It is
**not** generalised to arbitrary N-body configurations, where no single two-body
energy characterises the system.

### 6. Revolution tracking consumes a reference-plane azimuth, not an orbital element

Level 1 requires completed-revolution counting. The obvious candidate, true
anomaly, is unsuitable: it is measured from the eccentricity vector, so for a
circular orbit — precisely Level 1's target — the periapsis reference direction
ceases to exist and the anomaly is undefined. A revolution counter built on true
anomaly would fail on the best possible learner answer.

More generally, **there is no intrinsic scalar "in-plane position angle"** for an
arbitrary orbital plane: an angle requires a reference direction within the
plane, and the two natural candidates — the node vector and the eccentricity
vector — are exactly the ones that vanish in the degenerate cases.

The core therefore provides a **reference-plane azimuth** of the relative
position:

```
θ = atan2(r_y, r_x)
```

defined whenever the projection of `r` onto the reference plane is non-zero.
This is a geometric quantity in a known reference frame, not an orbital element,
and is documented as such. It is well-conditioned for any eccentricity,
including exact circularity.

Its scope limit is stated rather than assumed: the azimuth advances by 2π per
revolution only for orbits that are not perpendicular to the reference plane.
For a polar orbit the projection collapses to a line and the azimuth alternates
by π instead of circulating. This is acceptable because Level 1 is planar in the
reference plane by construction; a consumer outside that regime must not rely on
it.

Consistent with ADR-008 §7, the core supplies the instantaneous angle and the
learning layer unwraps successive values and accumulates them into a revolution
count. The core never counts revolutions.

### 7. Classical element semantics are defined; degeneracies are explicit

The classical element set is defined as semi-major axis `a`, eccentricity `e`,
inclination `i`, right ascension of the ascending node `Ω`, argument of periapsis
`ω`, and an anomaly, with respect to a stated reference frame and epoch.

Degenerate configurations are common in this application, and the decision is
that **undefined quantities are represented as undefined, not manufactured as
zero**:

```
e > 0, i > 0      Ω, ω and the true anomaly are normally defined

e = 0, i > 0      the periapsis direction disappears
                  → ω and true anomaly individually undefined
                  → an argument of latitude can remain meaningful

e > 0, i = 0      the ascending-node direction disappears
                  → Ω and ω individually undefined
                    (ω is measured FROM the node, so it is lost with it)
                  → a longitude of periapsis can remain meaningful

e = 0, i = 0      node and periapsis directions both disappear
                  → Ω, ω and true anomaly individually undefined
                  → a reference-plane longitude or azimuth can remain meaningful

‖h‖ = 0           no orbital plane (radial motion)
                  → i, Ω and all plane-derived quantities undefined

ε = 0             a undefined (parabolic boundary)
```

The nonsingular alternatives named above — argument of latitude, longitude of
periapsis, reference-plane longitude — are noted because they are what remains
physically meaningful in each case. None is implemented in this increment; they
are recorded so that a later implementation does not conclude that a degenerate
configuration carries no usable information.

Filling every field of a six-element structure with zeroes where the physics
provides no value would produce data that looks valid and is not — the same
failure mode this project has rejected elsewhere, in which a representation
asserts more than the underlying reality supports.

The **Java representation** of undefinedness is deliberately left to the
elements-to-state implementation, when the consumer's needs are known. One
constraint is recorded now: the core treats non-finite values as invalid
throughout, so `NaN` should not be adopted casually as an in-domain marker for
"undefined" without reconciling that conflict.

### 8. Epoch and anomaly semantics

An element set is meaningful only at an **epoch**. Elements therefore carry the
instant at which they apply, and a state constructed from them represents the
body **at that instant** — not at whatever simulation start time the application
happens to choose.

This matters directly for Objective 6: a simulated close approach cannot
legitimately be compared with published CNEOS data unless the initial state and
its epoch are coherently defined. Silently treating element-epoch state as
valid at simulation *t* = 0 would invalidate that comparison.

The **anomaly type must be explicit** in any element representation. Published
sources, including NASA, commonly supply *mean anomaly at epoch*, whereas the
standard perifocal state equations are conventionally expressed in terms of
*true anomaly*. These quantities are not interchangeable. For an elliptic orbit,
obtaining the instantaneous geometric phase from a mean anomaly requires solving
Kepler's equation for the eccentric anomaly and converting to true anomaly.

**That is an anomaly conversion at a single epoch, not propagation to another
epoch**, and it therefore falls inside this decision's scope rather than outside
it. The required elliptic anomaly-conversion capability is implemented together
with the elements-to-state functionality when Level 4 needs it (section 9). An
element model that labels its sixth parameter merely "anomaly" invites exactly
the error this paragraph guards against.

Two operations are consequently kept distinct, as established when this decision
was scoped:

```
coordinate/state conversion   elements at epoch t₀ → Cartesian state at epoch t₀
                              (may include anomaly conversion at t₀)

propagation                   state or elements at t₀ → state or elements at t₁,
                              where t₁ ≠ t₀
```

This ADR governs the first. The second is out of scope (section 10).

### 9. Elements → Cartesian state is defined here but implemented later

The conversion from classical elements plus μ to a Cartesian `StateVector` is
governed by every convention in this ADR. It is not implemented in this
increment because it has no consumer yet: Level 1 needs only the
state-to-quantities direction, and Level 4 additionally depends on the NeoWs
lookup acquisition capability recorded in the Context above.

Settling the semantics now while deferring the implementation follows the
practice used throughout this project: fix conventions before they can diverge,
but do not build functionality before something needs it.

### 10. Out of scope

This decision does not introduce orbital propagation, the universal-variable
formulation, general hyperbolic or parabolic anomaly conversion machinery,
coordinate-frame transformation libraries, perturbation models, or ephemeris
systems. `simulator-core` is not to become a general celestial mechanics
library; it implements the minimum mathematically correct capability its
consumers require.

One distinction is deliberate: the **elliptic** mean-to-true anomaly conversion
required by the selected NASA data is *deferred* to the Level 4 increment, not
excluded. It is same-epoch conversion within this ADR's scope (section 8),
implemented alongside elements-to-state when there is a consumer. What is
excluded is general anomaly machinery for hyperbolic and parabolic trajectories,
for which this project has no consumer.

## Consequences

### Positive

- Level 1 is unblocked with a small, analytically testable capability.
- The vector orbital diagnostics are valid for inclined and unbound
  trajectories, so Level 4 and a possible 3D renderer require no reformulation
  of the orbital-analysis foundation; the Level 1 reference-plane azimuth
  retains the documented inclination limitation.
- Explicit μ keeps the model choice with the caller and the mathematics free of
  assumptions about which scenario it serves.
- Relative-state semantics make every quantity correct in a moving inertial
  frame and invariant under a common spatial translation and a common Galilean
  velocity boost applied to both bodies — directly testable properties.
- Revolution tracking works at exact circularity, which is Level 1's target
  state and where the obvious alternative fails.
- Epoch and anomaly conventions are fixed before Level 4 depends on them,
  protecting the Objective 6 comparison from a class of silent error.
- Degeneracies are visible rather than disguised as zero values.
- The newly discovered Level 4 data dependency is recorded without diverting
  effort from the current critical path.

### Negative / Accepted Trade-offs

- The reference-plane azimuth is a frame-dependent geometric quantity rather
  than an intrinsic orbital element, and carries a documented scope limit for
  near-perpendicular orbits. Accepted: no intrinsic alternative exists that
  survives circularity, which is the case Level 1 must handle.
- Representing undefined quantities honestly is more awkward than returning
  zeroes, and the representation decision is deferred rather than settled.
  Accepted: a convenient lie about the physics is worse than a deferred design
  choice.
- Defining elements-to-state without implementing it means part of this ADR
  describes future work. Accepted: the alternative is defining the same
  conventions twice and risking divergence.
- A plain `double` for μ provides no dimensional type safety. Accepted:
  consistent with the rest of the core, and a single-field wrapper would add
  ceremony without protection.
- Level 4 remains blocked on a data capability that does not exist. Accepted and
  recorded; it is not on Level 1's critical path.

## Guiding Principle

Orbital quantities are computed from relative state and a caller-supplied
gravitational parameter, in general vector form, so that the mathematics serves
any model without choosing one. What the physics leaves undefined is reported as
undefined. Elements mean nothing without their epoch and an explicit anomaly
convention, so both are fixed before anything depends on them — and the core
computes instantaneous quantities while the learning layer accumulates history.
