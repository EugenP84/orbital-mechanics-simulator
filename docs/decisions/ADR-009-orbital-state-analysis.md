# ADR-009: Orbital State Analysis and Element Semantics

**Status:** Accepted  
**Date:** 06.09.2026  
**Modules:** simulator-core (`orbital` package), consumed by simulator-learning and simulator-app  

## Context

ADR-008 §7 places instantaneous orbital mathematics in `simulator-core` and
history-dependent scenario progress in `simulator-learning`. Level 1's
completion policy needs three instantaneous quantities from the core:
eccentricity for orbital shape, an escape criterion, and an angular quantity
from which the learning layer accumulates completed revolutions.

Level 4 requires the reverse direction: constructing an initial Cartesian
state from published orbital elements for a real near-Earth object.

Both directions rest on the same conventions: the reference body, the frame,
the gravitational parameter, the anomaly, the epoch, the units and the
handling of degenerate configurations. Defining those conventions in two
separate decisions would risk two slightly different notions of "orbital
elements" in one system. This ADR defines the semantics once and implements
only the state-to-quantities direction, which Level 1 needs.

The NeoWs `/feed` endpoint integrated under ADR-007 supplies close-approach
and physical data but no orbital elements, which are served by the object
lookup endpoint. Initialising a NEO in Level 4 from its real orbital
parameters depends on a data-acquisition capability that does not yet exist.
That capability is a Level 4 prerequisite and is outside this ADR.

## Decision Drivers

- Level 1 needs orbital shape, an escape criterion and revolution tracking.
- Level 4 needs elements-to-state construction against real published data,
  where epoch and anomaly conventions determine whether the result is
  physically meaningful.
- Objective 6 compares simulated close approaches against published CNEOS
  data, which is valid only if the initial state and its epoch are coherently
  defined.
- The core performs orbital mathematics and does not decide which physical
  model applies.
- Degenerate configurations are common in this application, because Level 1
  aims at a circular orbit and its scenarios are planar.
- The project does not need a general celestial mechanics library.

## Decision

### 1. A new `core/orbital` package

Orbital analysis is placed in `io.github.eugenp84.orbitalsim.core.orbital`,
alongside the existing physics. It is deterministic, provider-independent
mathematics. The same computation serves a learning scenario, a NEO trajectory
and any other consumer, and none of these uses is a pedagogical concern
(ADR-008 §7).

### 2. The formulation is general 3D vector mathematics, not planar-specific

The simulation is displayed in two dimensions, but the computational
representation is three-dimensional (`Vector3D` positions, velocities and
accelerations). A planar orbit is the case where the state stays in a plane:

```
physics representation      3D Cartesian
educational scenarios       predominantly planar
visualisation               2D projection
```

Orbital quantities are derived from vector formulations (specific angular
momentum and the eccentricity vector). These reduce correctly for planar
orbits and stay valid if an inclined NEO trajectory or a 3D renderer is
introduced.

A planar-specific element model is rejected. It would lose generality, and it
would encode a convention as if it were physics. In an orbit lying in the
reference plane, the inclination is zero, but the right ascension of the
ascending node is undefined: the node vector `n = ẑ × h` vanishes identically.
Assigning it zero is a convention, and a planar model would build that
convention in without stating it.

### 3. Calculations operate on relative state, with the reference stated by the caller

All orbital quantities are computed from the position and velocity of one body
relative to a stated reference body:

```
r = r_body − r_reference
v = v_body − v_reference
```

The caller identifies the reference, and the mathematics does not infer it.
The quantities are then correct in a moving frame, such as a planet orbiting a
star that is itself in motion.

Every quantity defined here is invariant under a common spatial translation
and a common Galilean velocity boost applied to both bodies, because only the
difference enters. These properties can be tested directly.

### 4. The gravitational parameter is supplied explicitly by the caller

Orbital calculations receive μ as a validated `double` in m³/s², positive and
finite. They do not compute it from a body's mass.

The appropriate μ depends on the physical model, and only the caller knows
which model applies:

```
fully coupled two-body (Level 1 star + planet)    μ = G(M_primary + M_secondary)
restricted test particle (Level 4 NEO)            μ = G·M_primary
```

The two formulas describe different models. The choice is made at the call
site, so the orbital layer does not decide which gravitational model applies.

A `GravitationalParameter` value type is not introduced. The rest of the core
expresses masses, distances, velocities and times as validated primitive
doubles, so a single-field wrapper here would move the same validation without
providing dimensional safety anywhere else. The type can be added if μ comes
to be used widely enough to be confused with other scalars.

All quantities are in SI units, consistent with the rest of the core.

### 5. Implemented quantities

From a relative state and μ, the following are computed:

| Quantity | Definition | Defined when |
|---|---|---|
| specific angular momentum | `h = r × v` | always, including `h = 0` (see below) |
| specific orbital energy | `ε = v²/2 − μ/r` | `r > 0` |
| eccentricity vector | `e = ((v² − μ/r)·r − (r·v)·v) / μ` | `r > 0` |
| eccentricity | `e = ‖e‖` | `r > 0` |
| semi-major axis | `a = −μ / (2ε)` | `ε ≠ 0` |

The angular-momentum vector is defined for every valid non-coincident relative
state, including the degenerate radial case where `r` and `v` are parallel and
`h = 0`. In that case there is no orbital plane, so every quantity that
requires an orbital-plane normal (inclination, the node vector and anything
derived from it) is undefined. An implementation must not normalise a zero
angular-momentum vector while computing such quantities. This case is
reachable in Level 1: a learner who sets tangential velocity to zero produces
purely radial infall.

These diagnostics are not restricted to bound orbits. Specific energy, angular
momentum and eccentricity remain meaningful for escape trajectories, and
Level 1 must be able to recognise escape, so an elliptic-only API would be
wrong. Semi-major axis keeps its conventional meaning for hyperbolic
trajectories (negative `a`). At the parabolic boundary, where `ε → 0` and `a`
is unbounded, it is reported as undefined and not as a large or infinite
number. These vector diagnostics need no hyperbolic or parabolic anomaly
calculation.

The bound/unbound criterion is expressed through specific energy relative to
the selected two-body model and the same μ:

```
ε < 0   bound
ε = 0   parabolic boundary
ε > 0   unbound (escape)
```

The criterion uses energy because eccentricity fails in the radial case. For
rectilinear motion the eccentricity is exactly 1 whatever the energy, so an
`e ≥ 1` test would classify a bound planet falling straight into the star as
escaping. That configuration is reachable in Level 1.

The criterion applies to Level 1's two-body scenario. It is not generalised to
arbitrary N-body configurations, where no single two-body energy characterises
the system.

### 6. Revolution tracking consumes a reference-plane azimuth, not an orbital element

Level 1 requires completed-revolution counting. True anomaly is unsuitable. It
is measured from the eccentricity vector, so for a circular orbit, which is
Level 1's target, the periapsis reference direction does not exist and the
anomaly is undefined. A revolution counter built on true anomaly would fail on
a perfectly circular orbit.

More generally, an arbitrary orbital plane has no intrinsic scalar in-plane
position angle. An angle requires a reference direction within the plane, and
the two natural candidates, the node vector and the eccentricity vector, both
vanish in the degenerate cases.

The core provides a reference-plane azimuth of the relative position:

```
θ = atan2(r_y, r_x)
```

It is defined whenever the projection of `r` onto the reference plane is
non-zero. It is a geometric quantity in a known reference frame and is
documented as such. It is well-conditioned for any eccentricity, including
exact circularity.

The azimuth advances by 2π per revolution only for orbits that are not
perpendicular to the reference plane. For a polar orbit the projection
collapses to a line and the azimuth alternates by π. This limit is acceptable
because Level 1 is planar in the reference plane by construction. A consumer
outside that regime must not rely on the azimuth.

Consistent with ADR-008 §7, the core supplies the instantaneous angle, and the
learning layer unwraps successive values and accumulates them into a
revolution count. The core never counts revolutions.

### 7. Classical element semantics are defined, and degeneracies are explicit

The classical element set is semi-major axis `a`, eccentricity `e`,
inclination `i`, right ascension of the ascending node `Ω`, argument of
periapsis `ω` and an anomaly, with respect to a stated reference frame and
epoch.

Degenerate configurations are common in this application. Undefined quantities
are represented as undefined and are not set to zero:

```
e > 0, i > 0      Ω, ω and the true anomaly are normally defined

e = 0, i > 0      the periapsis direction disappears
                  → ω and true anomaly individually undefined
                  → an argument of latitude can remain meaningful

e > 0, i = 0      the ascending-node direction disappears
                  → Ω and ω individually undefined
                    (ω is measured from the node, so it is lost with it)
                  → a longitude of periapsis can remain meaningful

e = 0, i = 0      node and periapsis directions both disappear
                  → Ω, ω and true anomaly individually undefined
                  → a reference-plane longitude or azimuth can remain meaningful

‖h‖ = 0           no orbital plane (radial motion)
                  → i, Ω and all plane-derived quantities undefined

ε = 0             a undefined (parabolic boundary)
```

The nonsingular alternatives named above (argument of latitude, longitude of
periapsis and reference-plane longitude) are what remains physically
meaningful in each case. None is implemented by this ADR.

Filling every field of a six-element structure with zeroes where the physics
provides no value would produce data that looks valid and is not.

The Java representation of an undefined quantity is left to the
elements-to-state implementation. One constraint applies: the core treats
non-finite values as invalid throughout, so `NaN` cannot serve as a marker for
"undefined" without resolving that conflict.

### 8. Epoch and anomaly semantics

An element set is meaningful only at an epoch. Elements carry the instant at
which they apply, and a state constructed from them represents the body at
that instant, whatever simulation start time the application chooses.

Objective 6 depends on this. A simulated close approach cannot be compared
with published CNEOS data unless the initial state and its epoch are
coherently defined. Treating an element-epoch state as valid at simulation
t = 0 would invalidate the comparison.

The anomaly type must be explicit in any element representation. Published
sources, including NASA, commonly supply mean anomaly at epoch, whereas the
standard perifocal state equations are expressed in terms of true anomaly. The
two are not interchangeable. For an elliptic orbit, obtaining the
instantaneous geometric phase from a mean anomaly requires solving Kepler's
equation for the eccentric anomaly and converting to true anomaly.

This is an anomaly conversion at a single epoch, so it falls inside the scope
of this ADR. It is not propagation to another epoch. The elliptic anomaly
conversion is implemented together with the elements-to-state functionality
when Level 4 needs it (section 9).

Two operations are kept distinct:

```
coordinate/state conversion   elements at epoch t₀ → Cartesian state at epoch t₀
                              (may include anomaly conversion at t₀)

propagation                   state or elements at t₀ → state or elements at t₁,
                              where t₁ ≠ t₀
```

This ADR governs the first. The second is out of scope (section 10).

### 9. Elements → Cartesian state is defined here but implemented later

The conversion from classical elements plus μ to a Cartesian `StateVector` is
governed by every convention in this ADR. It is not implemented yet because it
has no consumer. Level 1 needs only the state-to-quantities direction, and
Level 4 also depends on the NeoWs lookup capability described in the Context.

### 10. Out of scope

This ADR does not introduce orbital propagation, the universal-variable
formulation, general hyperbolic or parabolic anomaly conversion,
coordinate-frame transformation libraries, perturbation models or ephemeris
systems. `simulator-core` implements the minimum mathematically correct
capability its consumers require.

The elliptic mean-to-true anomaly conversion required by the selected NASA
data is deferred to Level 4 and is not excluded (section 8). General anomaly
conversion for hyperbolic and parabolic trajectories is excluded, because this
project has no consumer for it.

## Consequences

### Positive

- Level 1 gets the quantities it needs from a small, analytically testable
  capability.
- The vector orbital diagnostics are valid for inclined and unbound
  trajectories, so Level 4 and a possible 3D renderer require no
  reformulation. The Level 1 reference-plane azimuth keeps its documented
  inclination limit.
- Explicit μ keeps the model choice with the caller.
- Relative-state semantics make every quantity correct in a moving inertial
  frame and invariant under a common translation and a common Galilean
  velocity boost.
- Revolution tracking works at exact circularity, which is Level 1's target
  state.
- Epoch and anomaly conventions are fixed before Level 4 depends on them,
  which protects the Objective 6 comparison.
- Degenerate cases are reported as undefined.
- The Level 4 data dependency is recorded.

### Negative / Accepted Trade-offs

- The reference-plane azimuth is a frame-dependent geometric quantity with a
  documented scope limit for near-perpendicular orbits. No intrinsic
  alternative is defined for a circular orbit, which Level 1 must handle.
- Representing undefined quantities is more awkward than returning zeroes, and
  the Java representation is not yet decided.
- Part of this ADR describes future work, because elements-to-state is defined
  without being implemented. The alternative is to define the same conventions
  twice and risk divergence.
- A plain `double` for μ provides no dimensional type safety. This is
  consistent with the rest of the core.
- Level 4 depends on a data capability that does not yet exist.