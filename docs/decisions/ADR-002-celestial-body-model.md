# ADR-002: Composition-Based Celestial Body Model with Definition–State Separation

**Status:** Accepted  
**Date:** 25.07.2026  
**Modules:** simulator-core (model, math), simulator-data (nasa)  

## Context

The simulation engine must represent stars, planets and asteroids, including
real near-Earth objects (NEOs) imported from NASA's NeoWs API.

The project proposal named the Factory pattern as the mechanism for creating
celestial bodies. During detailed design, the domain model had to meet these
requirements:

1. The physics engine treats all bodies uniformly. Newtonian gravity depends
   only on physical properties (mass, position, velocity and separation), so
   astronomical classification does not affect it.
2. Real NEO catalogue data and close-approach information can be loaded from
   the NASA NeoWs API.
3. Euler, Velocity Verlet and RK4 can be compared from identical initial
   conditions (Objective 6).
4. Simulation reset, save/load persistence and scenario replay are supported.
5. The simulation core is separated from external data sources.
6. Further body classifications (e.g. moons, comets and spacecraft) can be
   added later with little structural change.

## Decision Drivers

- Model the physics domain, not the external data source.
- Avoid speculative inheritance.
- Support reproducible integrator comparison.
- Keep NeoWs-specific types out of simulator-core.
- Preserve stable runtime references for rendering and observers.

## Options Considered

### Option A: Full inheritance hierarchy

```
abstract CelestialBody
 ├── Star
 ├── Planet
 └── Asteroid
```

Rejected. All subclasses would hold identical state and take part in the
simulation identically. They would add no specialised behaviour and would
exist only for classification, at the cost of extra structure. Making the
Factory pattern visible in the design does not justify the hierarchy.

### Option B: Single class with NEO subclass

```
CelestialBody
        ▲
        │
NearEarthObject
```

`NearEarthObject` would extend `CelestialBody` with NASA-specific metadata
(NeoWs identifier, estimated diameter and close-approach information).

Rejected. The inheritance would be driven by external catalogue metadata
while simulation behaviour stayed identical. Close-approach information also
describes an event: one asteroid may have many recorded close approaches, so
embedding them in the simulation entity would model a one-to-many
relationship as object state. The subclass would couple the physics model to
NeoWs-specific concepts and break the separation between simulator-core and
simulator-data.

### Option C: Fully immutable simulation objects

```
CelestialBody (record, carrying its own state)

Integrator:  Current Bodies ──▶ New Bodies (complete replacement per step)
```

Each simulation step would create a new collection of celestial bodies using
immutable records and `withState()` copy methods.

Rejected, although viable. The simulation uses the Observer pattern, and
components such as the renderer and the hint engine keep references to
simulation objects throughout execution. Replacing every body instance on
every integration step would force observers to re-resolve object identity
continually. Stable runtime objects with replaceable immutable state
snapshots are simpler for the current architecture.

### Option D: Composition with definition–state separation (chosen)

| Type | Module | Responsibility |
|---|---|---|
| `CelestialBody` (record) | core/model | Immutable identity and physical properties (id, name, type, mass, radius) |
| `CelestialBodyType` (enum) | core/model | Classification (`STAR`, `PLANET`, `ASTEROID`) |
| `StateVector` (record) | core/math | Immutable simulation state (position, velocity) |
| `SimulationBody` | core/model | Mutable runtime entity associating a `CelestialBody` with its current `StateVector` |
| `CelestialBodyFactory` | core/model | Validation, default values and construction of valid core-domain objects |
| `NeoMetadata` (record) | data/nasa | NASA catalogue metadata describing the object itself |
| `CloseApproach` (record) | data/nasa | One recorded close-approach event |
| `NeoCatalogueEntry` (record) | data/nasa | NASA DTO containing metadata and close-approach history |
| `NeoBodyMapper` | data/nasa | Maps NASA DTOs into source-independent core creation requests |

The data layer converts `NeoCatalogueEntry` instances into neutral
core-domain creation requests before invoking the factory, so simulator-core
has no dependency on NeoWs-specific concepts.

The kinematic state record is named `StateVector` and is placed in
`core/math` next to `Vector3D`. "State vector" is the standard astrodynamics
term for a position and velocity pair.

## Decision

Adopt Option D.

`SimulationBody` is the stable runtime object. It associates an immutable
`CelestialBody` definition (permanent physical properties) with a replaceable
immutable `StateVector` (time-dependent state). This follows the Gang of Four
principle of favouring object composition over class inheritance. Inheritance
is reserved for future cases in which different behaviour requires
polymorphic substitution.

Astronomical classification is represented by the `CelestialBodyType`
enumeration, because simulation behaviour does not currently vary by type.

NASA-specific metadata and close-approach records stay in the simulator-data
module. `NeoBodyMapper` translates them into source-independent core-domain
objects and is the boundary between external APIs and the simulation model.

`CelestialBodyFactory` is a Simple Factory. The GoF Factory Method is not
used, because product behaviour does not vary polymorphically and
subclass-based instantiation would add nothing. The factory:

- validates physical properties;
- applies default values;
- enforces canonical units;
- generates identifiers where appropriate;
- constructs valid core-domain objects.

External data mapping is performed in the data layer, outside the factory.

Numerical integrators operate on a consistent snapshot of the current
simulation state. Each integration step computes all next states before any
runtime object is updated. Only then does the simulation engine replace each
body's `StateVector`, which prevents mixed-timestep updates.

## Consequences

### Positive

- Integrator comparison (Objective 6) can reuse identical body definitions
  with fresh initial states, which makes experiments repeatable.
- Reset, replay and save/load are simpler because immutable definitions are
  separated from changing simulation state.
- Renderer and observer components keep stable `SimulationBody` references
  throughout a simulation run.
- State updates replace complete immutable snapshots, so no vector component
  is mutated individually and inconsistent intermediate state is less likely.
- Supporting another catalogue provider (e.g. ESA) requires changes only
  within simulator-data.
- Further passive body classifications (e.g. moons or comets) can be
  introduced without new subclasses.

### Negative / Accepted Trade-offs

- The model contains more classes than a single mutable entity.
- Simulation updates require a two-phase process (calculate all next states,
  then commit all updates). This contract will be made explicit in the
  `Integrator` interface documentation.
- Actively controlled entities (e.g. spacecraft with propulsion or manoeuvre
  logic) may later need a different behavioural abstraction if their
  simulation behaviour diverges from that of passive gravitational bodies.