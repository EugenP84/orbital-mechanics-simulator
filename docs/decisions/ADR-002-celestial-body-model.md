# ADR-002: Composition-Based Celestial Body Model with Definition–State Separation

**Status:** Accepted  
**Date:** 25.07.2026  
**Modules:** simulator-core (model, math), simulator-data (nasa)  
**Related:** Project Proposal §2.2 (Simulation Engine), §3.5 (Design Patterns); Objective 6 (Numerical Integrator Comparison)

---

## Context

The simulation engine must represent several kinds of celestial bodies —
including stars, planets and asteroids (including real Near-Earth Objects
imported from NASA's NeoWs API) — while supporting both accurate numerical
simulation and future extensibility.

The project proposal identified the Factory pattern as the mechanism for
creating celestial bodies. During detailed design, the domain model needed to
satisfy the following requirements:

1. Uniform treatment of all bodies by the physics engine. Newtonian gravity
   depends only on physical properties (mass, position, velocity and
   separation), not on astronomical classification.
2. Loading real NEO catalogue data and close-approach information from the
   NASA NeoWs API.
3. Head-to-head comparison of multiple numerical integration algorithms
   (Euler, Velocity Verlet and RK4) from identical initial conditions
   (Objective 6).
4. Support for simulation reset, save/load persistence and scenario replay.
5. Clear separation between the simulation core and external data sources.
6. Future extensibility with additional body classifications (e.g. moons,
   comets and spacecraft) without introducing unnecessary structural
   complexity.

## Decision Drivers

- Model the physics domain rather than the external data source.
- Avoid speculative inheritance.
- Support reproducible integrator comparison.
- Maintain clear module boundaries.
- Preserve stable runtime references for rendering and observers.
- Keep the design simple while allowing future extension.

## Options Considered

### Option A — Full inheritance hierarchy

```
abstract CelestialBody
 ├── Star
 ├── Planet
 └── Asteroid
```

**Rejected.**

All subclasses would contain identical state and participate in the simulation
identically. They would introduce no specialised behaviour and would exist
purely for classification.

This is inheritance for taxonomy rather than polymorphism. It increases
structural complexity without providing meaningful behavioural variation.

The visibility of the Factory pattern alone is not sufficient justification.
Design patterns should support the domain model rather than dictate it.

### Option B — Single class with NEO subclass

```
CelestialBody
        ▲
        │
NearEarthObject
```

`NearEarthObject` would extend `CelestialBody` by adding NASA-specific
metadata (NeoWs identifier, estimated diameter, close-approach information,
etc.).

**Rejected.**

The inheritance would be driven by external catalogue metadata rather than
different simulation behaviour.

Furthermore, close-approach information represents an **event** rather than a
permanent property of the object. A single asteroid may have many recorded
close approaches. Embedding such information directly within the simulation
entity would incorrectly model a one-to-many relationship as object state.

This approach would also couple the physics model to NeoWs-specific concepts,
violating the architectural separation between simulator-core and
simulator-data.

### Option C — Fully immutable simulation objects

```
CelestialBody (record, carrying its own state)

Integrator:  Current Bodies ──▶ New Bodies (complete replacement per step)
```

Each simulation step would create an entirely new collection of celestial
bodies using immutable records and `withState()` copy methods.

**Rejected (viable but not preferred).**

This design is conceptually elegant and works well for functional
architectures.

However, the simulation uses the Observer pattern. Components such as the
renderer and educational hint engine retain references to simulation objects
throughout execution.

Replacing every body instance on every integration step would complicate
observer management by requiring continual re-resolution of object identity.

Maintaining stable runtime objects while replacing immutable state snapshots
provides a simpler solution for the current architecture.

### Option D — Composition with definition–state separation (Chosen)

| Type                         | Module | Responsibility |
|------------------------------|---|---|
| `CelestialBody` (record)     | core/model | Immutable identity and physical properties (id, name, type, mass, radius) |
| `CelestialBodyType` (enum)   | core/model | Classification (`STAR`, `PLANET`, `ASTEROID`) |
| `StateVector` (record)       | core/math | Immutable simulation state (position, velocity) |
| `SimulationBody`             | core/model | Mutable runtime entity associating a `CelestialBody` with its current `StateVector` |
| `CelestialBodyFactory`       | core/model | Validation, default values and construction of valid core-domain objects |
| `NeoMetadata` (record)       | data/nasa | NASA catalogue metadata describing the object itself |
| `CloseApproach` (record)     | data/nasa | One recorded close-approach event |
| `NeoCatalogueEntry` (record) | data/nasa | NASA DTO containing metadata and close-approach history |
| `NeoBodyMapper`              | data/nasa | Maps NASA DTOs into source-independent core creation requests |

The data layer converts `NeoCatalogueEntry` instances into neutral core-domain
creation requests before invoking the factory. Consequently, simulator-core
remains completely independent of NeoWs-specific concepts.

**Naming note.** The immutable kinematic state record is named `StateVector`
and resides in `core/math` rather than being introduced as a new `BodyState`
type in `core/model`. *State vector* is the standard term for a position and
velocity pair in the astrodynamics literature (Bate, Mueller and White, 1971;
Curtis, 2024 — Proposal references [21], [22]), and the type is a purely
kinematic quantity that sits naturally alongside `Vector3D`. Reusing the
established, tested type avoids churn and keeps the codebase aligned with
domain vocabulary.

## Decision

Adopt Option D.

**Composition over inheritance.** This design follows the GoF principle
“Favor object composition over class inheritance.” `SimulationBody` combines
an immutable `CelestialBody` definition with an immutable `StateVector`, while
NASA-specific metadata remains in separate data-layer types. Inheritance is
reserved for future cases in which genuinely different behaviour requires
polymorphic substitution.

**Classification.** Astronomical classification is represented using the
`CelestialBodyType` enumeration rather than inheritance because simulation behaviour
does not currently vary polymorphically.

**Composition.** External catalogue information is modelled separately from
the simulation domain. NASA-specific metadata and close-approach records
remain entirely within the simulator-data module. A dedicated mapper
(`NeoBodyMapper`) translates external representations into source-independent
core-domain objects, forming an architectural boundary between external APIs
and the simulation model.

**Definition–state separation.** Permanent physical properties are represented
by immutable `CelestialBody` records. Time-dependent simulation state is
represented by immutable `StateVector` records. `SimulationBody` provides the
stable runtime object used throughout the simulation by associating an
immutable body definition with a replaceable immutable state snapshot.

**Object creation.** `CelestialBodyFactory` is implemented as a Simple Factory
rather than the GoF Factory Method. Because product behaviour does not vary
polymorphically, subclass-based instantiation would introduce unnecessary
complexity. The factory is responsible for:

- validating physical properties;
- applying default values;
- enforcing canonical units;
- generating identifiers where appropriate; and
- constructing valid core-domain objects.

External data mapping remains outside the factory and is performed within the
data layer.

**Simulation updates.** Numerical integrators operate on a consistent snapshot
of the current simulation state. Each integration step computes the complete
collection of next states before any runtime object is updated. Only after all
calculations complete does the simulation engine replace each body's immutable
`StateVector`, preventing mixed-timestep updates.

## Consequences

### Positive

- Integrator comparison (Objective 6) can reuse identical immutable body
  definitions while supplying fresh initial states, simplifying repeatable
  experiments.
- Reset, replay and save/load operations become straightforward because
  immutable definitions are separated from changing simulation state.
- Renderer and observer components retain stable `SimulationBody` references
  throughout a simulation run.
- State updates replace complete immutable snapshots rather than mutating
  vector components individually, reducing the risk of inconsistent
  intermediate state.
- simulator-core remains independent of external data-source concepts.
- Supporting additional catalogue providers (e.g. ESA) requires changes only
  within simulator-data.
- Additional passive body classifications (e.g. moons or comets) can be
  introduced without creating unnecessary subclasses.

### Negative / Accepted Trade-offs

- The model contains more classes than a single mutable entity, increasing
  initial structural complexity.
- Simulation updates require a two-phase process (calculate all next states,
  then commit all updates). This contract will be made explicit in the
  `Integrator` interface documentation.
- Actively controlled entities (e.g. spacecraft with propulsion or manoeuvre
  logic) may eventually require a different behavioural abstraction if their
  simulation behaviour diverges significantly from passive gravitational
  bodies.

## Guiding Principle

Use inheritance when behaviour varies polymorphically; use an enumeration for
classification; use composition for optional metadata; and separate immutable
physical identity from time-dependent simulation state.
