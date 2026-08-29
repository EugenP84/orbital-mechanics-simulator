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

| Module | Responsibility | Status |
|---|---|---|
| `simulator-core` | Domain model, gravitational physics, numerical integrators, simulation engine | Complete |
| `simulator-data` | NASA NeoWs integration, offline cache, JSON persistence | In progress |
| `simulator-learning` | Progressive learning scenarios and adaptive hint system | Not started |
| `simulator-app` | JavaFX desktop application | Skeleton only |

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

| ADR | Decision |
|---|---|
| ADR-001 | Technology stack and multi-module structure |
| ADR-002 | Celestial body model: definition–state separation |
| ADR-003 | Newtonian gravity model |
| ADR-004 | Acceleration aggregation and arbitrary-state evaluation |
| ADR-005 | Snapshot-based numerical integration contract |
| ADR-006 | Simulation engine execution model |
| ADR-007 | External NEO data acquisition and trust boundary |

## Documentation

- [Project structure and design decisions](docs/architecture/project-structure.md)
- [Architecture Decision Records](docs/decisions/)

## Status

**Phase 3 — Development.** `simulator-core` is complete and tested; work is
underway on `simulator-data` (NASA NeoWs integration with offline fallback).