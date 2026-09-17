# Orbital Mechanics Simulator

A gamified orbital mechanics and near-Earth object (NEO) simulator for
introductory astrophysics education.

Developed by Eugeniu Platita.

## Overview

The simulator combines a physics-based N-body gravitational engine, a set of
progressive interactive learning scenarios, an adaptive hint system, and
integration with real tracked asteroid data from NASA's NeoWs service. Three
numerical integration methods — Forward Euler, Velocity Verlet and
Runge–Kutta 4 — are interchangeable at runtime and can be compared directly for
accuracy, energy behaviour and computational cost.

## Technology stack

- Java 21 LTS (Eclipse Temurin)
- JavaFX 21
- Gradle 8.13 (wrapper included)
- JUnit 5 (Jupiter)
- Gson 2.11.0
- Java `java.net.http.HttpClient` (NeoWs access)

## Modules

| Module | Responsibility | Status                                                        |
|---|---|---------------------------------------------------------------|
| `simulator-core` | Domain model, gravitational physics, numerical integrators, simulation engine | Complete                                                      |
| `simulator-data` | NASA NeoWs integration, offline cache, JSON persistence | Complete                                                      |
| `simulator-learning` | Progressive learning scenarios and adaptive hint system | Complete)                                                     |
| `simulator-app` | JavaFX desktop application | UI, rendering, controllers and multi-level presentation shell |

The dependency direction is one-way: `simulator-app` → `{simulator-learning,
simulator-data}` → `simulator-core`. The core depends on nothing outside the
JDK and is free of JavaFX and of any external-service concern.

### `simulator-core`

A self-contained, deterministic computational core, organised into five
packages:

- `math` — immutable `Vector3D` and `StateVector` value types, physical
  constants.
- `model` — `CelestialBody` definitions and the runtime `SimulationBody`
  aggregate (immutable definition + mutable committed state).
- `physics` — Newtonian gravity, energy diagnostics, and the N-body
  acceleration model behind an `AccelerationModel` abstraction.
- `integrator` — the `Integrator` strategy and its three implementations,
  each a pure transformer of immutable state snapshots.
- `simulation` — the `SimulationEngine`, which owns committed state, commits
  atomically, separates numerical timestep from presentation speed, and
  publishes immutable snapshots to observers.

The numerical methods are validated to their theoretical convergence orders,
and the physics is checked against known reference values (Earth surface
gravity, the Sun–Earth gravitational force, circular-orbit closure).

### `simulator-data`

Integration with NASA's NeoWs (Near Earth Object Web Service), built around a trust boundary that keeps the unreliable, externally-owned data representation away from the deterministic core:

- A provider abstraction (NeoDataProvider) behind which live and offline sources are interchangeable.
- NASA-specific DTOs kept private to the adapter; retrieved JSON is normalised into provider-neutral, SI-unit types before it crosses the module boundary.
- A bundled offline dataset (a genuine captured NeoWs response) served through the same parser as live data, with explicit provenance (live vs cached) and source-capture timestamps.
- Cause-based failure classification: transport failures, rate limiting, server errors and malformed external data fall back to the offline dataset, while configuration errors and internal defects surface rather than being masked.
- An injectable HTTP transport seam, so every response class is tested deterministically offline; a real HttpClient-backed transport performs the actual network I/O.

### `simulator-learning`

The learning layer owns scenario lifecycle, observation cadence, completion
policy and pedagogical feedback without depending on JavaFX.

The currently implemented learning scenarios are:

#### Level 0 — Planet Creation

The learner configures:

- planet mass;
- planet radius;
- initial orbital distance;
- initial tangential speed.

Level 0 has no learner failure condition and no adaptive hints. It introduces
the physical parameter space before imposing an orbital objective.

#### Level 1 — Stable Orbit

The learner adjusts the initial tangential speed of a planet around a star and
attempts to produce a near-circular stable orbit.

Assessment uses orbital quantities derived from the simulated state, including
bound/unbound energy, eccentricity, revolution progress and numerical-energy
integrity. A rule-based hint engine provides physics-informed guidance based on
the configured launch speed and simulated orbital state.

Scenario advancement remains owned by `ScenarioSession`. Numerical timestep,
learning observation cadence and JavaFX presentation cadence are kept
separate.

### `simulator-app`

The JavaFX application currently provides a working vertical slice for Levels
0 and 1.

The presentation architecture consists of:

- `Main` — the thin application host and persistent level navigation;
- `LevelScreen` — the contract through which each level exposes its simulation
  and controls regions;
- `SimulationViewCore` — shared animation, rendering, trajectory and generic
  simulation-control behaviour;
- `ScenarioControls` — the level-agnostic controller surface used by the
  presentation core;
- `ScenarioPlayer` — shared lifecycle and presentation-speed behaviour used by
  level-specific controllers;
- `SimulationRenderer` and `TrajectoryTrail` — Canvas rendering and bounded
  trajectory visualisation.

The host places the active simulation in the centre and a vertical control
panel on the right. Level navigation remains persistent while level-specific
screens are rebuilt on switching. The side panel uses vertically stacked
controls and readouts so labels and status information remain legible on
smaller displays.

## Building and running

    ./gradlew build
    ./gradlew :simulator-app:run

Requires JDK 21. The Gradle wrapper handles the Gradle version.

Run the core test suite alone with:

    ./gradlew :simulator-core:test

### NASA NeoWs access

Live NEO data requires a free NASA API key from
[api.nasa.gov](https://api.nasa.gov). The key is supplied through environment
or configuration and is never committed to source control. It is **not**
required to build the project or to run the ordinary test suite: development
and testing proceed against a bundled cached dataset, and the application
remains usable offline. A live NASA call is exercised only by an explicitly
opt-in integration test.

## Design decisions

The architecture is recorded as a series of Architecture Decision Records
(ADRs) in `docs/decisions/`, several of which document where an initial
position was revised in review:

| ADR     | Decision |
|---------|---|
| ADR-001 | Technology stack and multi-module structure |
| ADR-002 | Celestial body model: definition–state separation |
| ADR-003 | Newtonian gravity model |
| ADR-004 | Acceleration aggregation and arbitrary-state evaluation |
| ADR-005 | Snapshot-based numerical integration contract |
| ADR-006 | Simulation engine execution model |
| ADR-007 | External NEO data acquisition and trust boundary |
| ADR-008 | Learning Scenario Architecture and Evaluation Cadence |
| ADR-009 | Orbital State Analysis and Element Semantics |
| ADR-010 | JavaFX presentation architecture and multi-level shell |

## Documentation

- [Project structure and design decisions](docs/architecture/project-structure.md)
- [Architecture Decision Records](docs/decisions/)

## Status

**Phase 3 — Development.**

A stable intermediate JavaFX milestone has been reached:

- the computational core and NASA NeoWs data layer are implemented and tested;
- All learning scenarios implemented (L0 - L4);

The next development work focuses on the numerical integrators comparison and UI polishing.