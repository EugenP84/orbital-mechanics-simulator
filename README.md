# Orbital Mechanics Simulator

A gamified orbital mechanics and near-Earth object (NEO) simulator for
introductory astrophysics education.

Developed by Eugeniu Platita.

## Overview

The simulator combines a physics-based small-N gravitational engine,
progressive interactive learning scenarios, physics-informed adaptive
feedback, NASA NEO data integration, and numerical-method comparison.

Three numerical integration methods are implemented: Forward Euler,
Velocity Verlet, and classical fourth-order Runge-Kutta (RK4). A
dedicated comparison facility runs them from identical initial
conditions and measures energy behaviour, analytical trajectory error,
and acceleration-model evaluations.

## Technology stack

-   Java 21 LTS (Eclipse Temurin)
-   JavaFX 21
-   Gradle 8.13 (wrapper included)
-   JUnit 5 (Jupiter)
-   Gson 2.11.0
-   Java `java.net.http.HttpClient` for NeoWs access

## Modules

  ------------------------------------------------------------------------
Module                   Responsibility          Current status
  ------------------------ ----------------------- -----------------------
`simulator-core`         Domain model, Newtonian Implemented and tested
gravity, numerical      
integrators,            
orbital-state analysis  
and simulation engine

`simulator-data`         NASA NeoWs integration, Implemented and tested
provider-neutral NEO    
data, offline support,  
trusted planet reference
data and HTTP transport

`simulator-learning`     Scenario lifecycle,     L0–L4 implemented and
learning scenarios and  tested
adaptive feedback

`simulator-evaluation`   Controlled              Implemented and tested
numerical-integrator    
comparison and          
measurement

`simulator-app`          JavaFX application,     Implemented for the
rendering, controls,    currently available
navigation and          scenarios and
comparison UI           comparison facility
  ------------------------------------------------------------------------

The architecture keeps the deterministic computational core independent
of JavaFX and external services.

### `simulator-core`

The computational core contains:

-   `math` --- immutable `Vector3D` and `StateVector` value types and
    physical constants;
-   `model` --- celestial-body definitions and runtime simulation-body
    state;
-   `physics` --- Newtonian gravity, energy diagnostics and N-body
    acceleration evaluation behind `AccelerationModel`;
-   `integrator` --- the common `Integrator` contract and Forward Euler,
    Velocity Verlet and RK4 implementations;
-   `simulation` --- committed simulation state, fixed numerical
    timesteps, atomic state advancement and immutable snapshots;
-   orbital-state analysis used by learning-scenario evaluation.

The physics and numerical layers are covered by automated tests,
including known-value gravitational checks and numerical-integration
validation.

### `simulator-data`

NASA NeoWs integration is isolated from the deterministic core by a
provider boundary. The module includes provider-neutral SI-unit NEO data,
NASA-specific parsing, live HTTP access, bundled offline data, explicit
external-service failure handling, and an injectable HTTP transport seam
for deterministic tests.

The module also contains the bundled Solar-System planet catalogue used by
Level 2. The catalogue is loaded from JSON and records NASA/NSSDCA source
provenance; the application uses it as trusted reference data without
introducing a dependency from `simulator-learning` to `simulator-data`.

Ordinary development and automated testing therefore do not require live
NASA access.

### `simulator-learning`

The learning layer owns scenario lifecycle, observation cadence,
completion/failure policy and pedagogical feedback without depending on
JavaFX.

#### Learning Scenario L0 --- Planet Creation

Implemented. The learner configures planet properties and initial
orbital conditions. L0 is introductory and does not impose a physical
success/failure evaluation or use adaptive hints.

#### Learning Scenario L1 --- Stable Orbit

Implemented. The learner adjusts the initial tangential speed of a
planet around a star and attempts to establish a near-circular stable
orbit. Evaluation uses quantities derived from the simulated state, and
physics-informed rules provide adaptive guidance.

#### Learning Scenario L2 --- Planet Comparison

Implemented. L2 is an exploratory, non-assessable comparison activity.
The learner's current Level 0 planet is compared with any of the eight
Solar-System planets using mass, mean radius, mean density and surface
gravity. Density and surface gravity for the learner-created planet are
derived from its selected mass and radius; trusted reference-planet data
are loaded from the bundled NASA/NSSDCA-derived JSON catalogue.

L2 deliberately has no simulation lifecycle, numerical integrator,
pass/fail criterion or adaptive hints. Level 0 to Level 2 continuity is
kept as minimal in-memory application state rather than introducing a
persistence subsystem.

#### Learning Scenario L3 --- Gravitational Perturbation

Implemented. L3 provides an interactive gravitational-perturbation
scenario using the common simulation and learning infrastructure.

#### Learning Scenario L4 --- Near-Earth Object

Implemented. L4 provides the NEO-focused scenario while keeping
simulation-model and external-data concerns separated.

### `simulator-evaluation`

The evaluation module provides a JavaFX-independent numerical-integrator
comparison experiment. The canonical in-application comparison uses:

-   a fully coupled Sun-Earth-like two-body system;
-   identical centre-of-mass initial conditions for every method;
-   a fixed timestep of 21,600 seconds (6 hours);
-   1,461 committed steps (365.25 simulated days);
-   a fresh run for each integrator.

It measures maximum relative total-mechanical-energy deviation, maximum
relative-trajectory error against an analytical circular-orbit
reference, and actual acceleration-model evaluations.

The same comparison logic supports incremental execution for JavaFX
presentation and headless execution for controlled evaluation.

### `simulator-app`

The JavaFX application provides the presentation layer for all five
learning scenarios and the numerical-integrator comparison. Its
architecture includes the `Main` application host and persistent
navigation, the `LevelScreen` screen contract, shared rendering and
trajectory visualisation for simulation-based levels, level-specific
controls/controllers, the non-simulation `Level2Screen`, and a dedicated
integrator-comparison screen.

`Main` also owns the deliberately small in-memory hand-off of the current
Level 0 learner-planet profile to Level 2.

The five planned learning scenarios are conceptually separate from the
numerical-integrator comparison; the comparison is not an L5 scenario.
Presentation speed is also separate from numerical timestep.

## Building and running

From the project root:

    ./gradlew build
    ./gradlew :simulator-app:run

On Windows:

    .\gradlew.bat build
    .\gradlew.bat :simulator-app:run

JDK 21 is required. The Gradle wrapper supplies the configured Gradle
version.

Complete test suite:

    ./gradlew test

Core tests only:

    ./gradlew :simulator-core:test

Evaluation tests only:

    ./gradlew :simulator-evaluation:test

## NASA NeoWs access

Live NeoWs access requires a NASA API key, which must not be committed
to the repository. Ordinary builds and automated tests do not require
live NASA access because the data layer supports deterministic offline
operation.

## Design decisions

Architecture and significant design choices are documented as
Architecture Decision Records (ADRs) under `docs/decisions/`. Eleven ADRs
(ADR-001 through ADR-011) are currently tracked in the repository.

  -----------------------------------------------------------------------
ADR                                 Decision area
  ----------------------------------- -----------------------------------
ADR-001                             Technology stack and multi-module
structure

ADR-002                             Celestial-body definition/state
separation

ADR-003                             Newtonian gravity model

ADR-004                             Acceleration aggregation and
restricted-body policy

ADR-005                             Snapshot-based numerical
integration

ADR-006                             Simulation-engine execution model

ADR-007                             External NEO data acquisition and
trust boundary

ADR-008                             Learning-scenario architecture and
evaluation cadence

ADR-009                             Orbital-state analysis and element
semantics

ADR-010                             JavaFX presentation architecture
and multi-level shell

ADR-011                             L4 NEO simulation and validation
design
  -----------------------------------------------------------------------

## Documentation

-   [Project structure and design
    decisions](docs/architecture/project-structure.md)
-   [Architecture Decision Records](docs/decisions/)
-   [Integrator-comparison evaluation evidence](docs/evaluation/integrator-comparison/)
-   [NEO validation evidence](docs/evaluation/neo-validation/)

## Current status

The project is in the verification and evaluation stage.

Implemented and substantially validated:

-   deterministic Newtonian simulation core;
-   Forward Euler, Velocity Verlet and RK4 integrators;
-   orbital-state analysis;
-   NASA NeoWs data layer with offline support;
-   Learning Scenarios L0–L4, including L2 planet comparison;
-   physics-informed adaptive feedback where required by the scenario
    design;
-   JavaFX multi-scenario application;
-   headless and interactive numerical-integrator comparison.

Remaining significant work:

-   final JavaDocs refinement and limited UI polishing;
