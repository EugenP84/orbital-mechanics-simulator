# Project Structure Reference

**Project:** Orbital Mechanics Simulator  
**Author:** Eugeniu PLATITA (EugenP84)  
**Date:** September 2026  
**Status:** As-built — reflects the current implemented multi-module source tree

This document records the **as-built** module and package structure of the
orbital-mechanics-simulator project. It is generated to match the actual
IntelliJ IDEA project tree, and supersedes the earlier planned/aspirational
baseline. Where the delivered structure diverges from the original design
plan, the divergence is recorded explicitly (see *As-Built vs Planned
Divergences* below) as honest evidence of evidence-driven design evolution.

---

## Technology Stack

| Component      | Version / Choice                    |
|----------------|-------------------------------------|
| Java           | Eclipse Temurin 21 (LTS)            |
| JavaFX         | 21 (LTS)                            |
| Gradle         | 8.13 (wrapper)                     |
| Build DSL      | Kotlin (`*.gradle.kts`)            |
| Testing        | JUnit 5 (Jupiter)                  |
| JSON           | Gson                                |
| HTTP           | `java.net.http.HttpClient` (JDK)   |
| IDE            | IntelliJ IDEA                       |
| Source Control | Git / GitHub                       |

---

## Modules

The project is a five-module Gradle build:

| Module                  | Responsibility                                                              |
|-------------------------|----------------------------------------------------------------------------|
| `simulator-core`        | Physics engine, numerical integrators, domain model, orbital analysis      |
| `simulator-data`        | External NEO data, offline fixtures, and trusted Solar-System planet data   |
| `simulator-learning`    | Educational scenario layer (L0–L4, comparison logic, policies and hints)    |
| `simulator-app`         | JavaFX presentation shell, controllers, rendering                          |
| `simulator-evaluation`  | Headless evaluation experiments (integrator comparison, Apophis validation)|

---

## Module Dependency Graph

Dependencies are strictly one-directional. `simulator-core` depends only on
the JDK.

```
simulator-app
    │ depends on
    ├──> simulator-learning ──> simulator-core
    ├──> simulator-data     ──> simulator-core
    └──> simulator-core

simulator-evaluation ──> simulator-core
    (its NEO validation test sources additionally consume
     simulator-data's Horizons fixture loader at test scope)
```

Notes on the graph:

- The `simulator-learning` and `simulator-data` modules do **not** depend on
  one another. The Level 4 data↔learning boundary is bridged in the
  application layer by `app.composition.Level4AttemptFactory`, which
  translates a validated data-layer Horizons state into the neutral learning
  input.
- No `simulator-app → simulator-evaluation` dependency is asserted here.
  `simulator-evaluation` is a self-contained headless experiment module; the
  application's own integrator-comparison UI classes live in `simulator-app`.

---

## Full Structure (as built)

The following tracked-file inventory was regenerated from `git ls-files`
after completion of the Level 2 increment. It is intentionally exact rather
than aspirational.

```text
orbital-mechanics-simulator/
├── docs/                              # Project architecture, design decisions and evaluation evidence
│   ├── architecture/                  # As-built architecture documentation
│   ├── decisions/                     # Architecture Decision Records (ADR-001 … ADR-011)
│   └── evaluation/                    # Reproducible experimental evidence and results
│       ├── integrator-comparison/     # Numerical-integrator comparison evidence
│       └── neo-validation/            # Apophis convergence and CNEOS validation evidence
│
├── simulator-core/                    # Deterministic physics and simulation engine
│   └── src/
│       ├── main/java/.../core/
│       │   ├── integrator/            # Euler, Velocity Verlet and RK4 integration strategies
│       │   ├── math/                  # Vectors, state vectors and physical constants
│       │   ├── model/                 # Celestial-body and simulation-body domain models
│       │   ├── orbital/               # Orbital-state calculation and analysis
│       │   ├── physics/               # Gravity, acceleration and energy calculations
│       │   └── simulation/            # Simulation execution, snapshots and listeners
│       └── test/java/.../core/        # Unit and integration tests for the core engine
│           ├── integrator/            # Integrator correctness and convergence tests
│           ├── math/                  # Mathematical primitive tests
│           ├── model/                 # Domain-model validation tests
│           ├── orbital/               # Orbital-analysis tests
│           ├── physics/               # Gravity, acceleration and energy tests
│           └── simulation/            # Simulation-engine and multi-body tests
│
├── simulator-data/                    # External scientific data and trusted reference datasets
│   └── src/
│       ├── main/
│       │   ├── java/.../data/
│       │   │   ├── horizons/          # JPL Horizons state-vector loading and provenance
│       │   │   ├── http/              # HTTP transport abstraction and JDK implementation
│       │   │   ├── nasa/              # NASA NeoWs API access and JSON parsing
│       │   │   ├── neo/               # Provider-neutral NEO data model and contracts
│       │   │   └── planet/            # Solar-System planet reference-data catalogue
│       │   └── resources/
│       │       ├── neo/               # Bundled Apophis/Horizons and NeoWs fixtures
│       │       └── planets/           # NASA-derived Solar-System planet dataset
│       └── test/
│           ├── java/.../data/         # Data loading, parsing and provider tests
│           └── resources/neo/         # Deterministic NEO test fixtures
│
├── simulator-learning/                # Educational scenarios and learning-domain logic
│   └── src/
│       ├── main/java/.../learning/
│       │   └── scenario/              # L0–L4 scenarios, lifecycle, policies and hints
│       └── test/java/.../learning/
│           └── scenario/              # Scenario, policy and learning-logic tests
│
├── simulator-app/                     # JavaFX presentation and application composition
│   └── src/
│       ├── main/java/.../app/
│       │   ├── composition/           # Bridges independent data and learning modules
│       │   ├── controller/            # UI-facing scenario and simulation controllers
│       │   └── rendering/             # Canvas rendering, transforms and trajectory traces
│       └── test/java/.../app/
│           ├── composition/           # Application-composition tests
│           ├── controller/            # Controller behaviour tests
│           └── rendering/             # Rendering-support and trajectory tests
│
├── simulator-evaluation/              # Headless scientific evaluation experiments
│   └── src/
│       ├── main/java/.../evaluation/
│       │   └── integrator/            # Formal numerical-integrator comparison experiment
│       └── test/
│           ├── java/.../evaluation/
│           │   ├── integrator/        # Integrator evidence generation and validation
│           │   └── neo/               # Apophis convergence and CNEOS validation experiments
│           └── resources/neo/         # Provenance-locked CNEOS reference fixture
│
├── gradle/
│   └── wrapper/                       # Gradle Wrapper configuration and executable JAR
├── build.gradle.kts                   # Root Gradle build configuration
├── settings.gradle.kts                # Multi-module Gradle project definition
├── gradlew                            # Unix Gradle Wrapper launcher
├── gradlew.bat                        # Windows Gradle Wrapper launcher
└── README.md                          # Project overview, build instructions and status
```

---

## Package-to-Pattern Mapping (as built)

Only patterns actually present in the delivered code are listed. Patterns
considered during design but **not** adopted are recorded separately below,
to keep this document an honest architectural record.

| Package / Element                                   | Pattern (as built)          | Notes |
|-----------------------------------------------------|-----------------------------|-------|
| `core/integrator/` (`Integrator` + implementations) | Strategy                    | Interchangeable numerical integration methods, selected per run |
| `learning/scenario/` (`ScenarioCompletionPolicy` + implementations) | Strategy    | Per-level completion/assessment rules (`TimeHorizon`, `Level1StableOrbit`, `Level3`, `Level4`, `Level0PlanetCreation`) |
| `core/simulation/SimulationListener` (+ `ListenerErrorHandler`) | Observer         | Decoupled engine → presentation/observation notification |
| `app/SimulationViewCore`, `LevelScreen`, `*Screen`  | Composition over inheritance| Shared presentation core reused across levels (ADR-010) |
| `app/composition/Level4AttemptFactory`              | Composition boundary        | Bridges data and learning modules without a direct edge between them |
| `app/Main` + `Level0Screen` + `Level2Screen`        | Application composition     | Maintains the minimal in-memory L0→L2 learner-planet hand-off while keeping learning and data modules independent |

### Patterns considered but not adopted

| Pattern         | Original plan location        | Outcome |
|-----------------|-------------------------------|---------|
| State           | `learning/scenario/` (level progression) | Not adopted; scenario lifecycle handled by `ScenarioSession` + `ScenarioLifecycleState` enum without a State-object hierarchy (ADR-008/ADR-010) |
| Template Method | `learning/scenario/` (common lifecycle)  | Not adopted; shared behaviour achieved by composition, not inheritance |
| Factory         | `core/model/` + `core/simulation/`       | Not adopted as a distinct pattern; direct construction / narrow composition classes used instead |

---

## As-Built vs Planned Divergences

The earlier baseline described a planned structure. The delivered system
diverges from it in the following deliberate, evidence-driven ways:

- **`simulator-evaluation` is built, not deferred.** It contains the
  headless integrator-comparison experiment and the Apophis 2029 external
  validation suite against a provenance-locked JPL CNEOS reference.
- **All five learning scenarios L0–L4 are implemented.** L2 is intentionally
  different from the simulation-based levels: it is an exploratory,
  non-assessable planet-comparison activity rather than another numerical
  simulation.
- **L2 introduces a small `data/planet/` package and
  `resources/planets/solar-system-planets.json`.** This is a bounded trusted
  reference catalogue, not the larger general-purpose planet-library or
  persistence subsystem envisaged in the early plan.
- **L0→L2 continuity is in memory.** `Main` retains the current learner-planet
  profile published by `Level0Screen` and supplies it when `Level2Screen` is
  created. No general save/resume or persistence infrastructure is introduced.
- **The data module remains organised around `horizons/`, `http/`, `nasa/`,
  `neo/` and the new bounded `planet/` package.** There is no generic
  persistence package.
- **No `core/event/` package exists.** The Observer contract lives directly
  in `core/simulation/` as `SimulationListener`.
- **A `core/orbital/` package exists** (`OrbitalStateAnalysis`,
  `OrbitalStateCalculator`) for osculating orbital-state analysis, added per
  ADR-009 although it was not in the original baseline.
- **The app layer uses `composition/`, `controller/` and `rendering/`;** there
  is no FXML/CSS resource architecture. Screens and the shared
  `SimulationViewCore` sit at the `app` package root.
- **Level 1 has no dedicated controller.** It is driven by the shared
  `SimulationController`; L0, L3 and L4 have dedicated controllers, while L2
  is sufficiently small to keep its deterministic comparison logic in the
  learning layer and its interaction logic in `Level2Screen`.
- **Eleven ADRs are currently tracked** (ADR-001 … ADR-011). The numerical
  integrator comparison is implemented and evidenced, but there is no
  tracked ADR-012 in the current repository.
- **`docs/evaluation/` now exists** and contains the committed raw
  integrator-comparison and NEO-validation evidence.

---

## Conventions

- **Base package:** `io.github.eugenp84.orbitalsim`
- **Module prefix:** each module owns one sub-package
  (`core`, `data`, `learning`, `app`, `evaluation`)
- **Tests mirror sources:** test packages replicate main package structure
- **Resources:** JSON fixtures/reference data live in the owning module's
  resources; NEO data use `neo/` and Level 2 reference planets use `planets/`
- **Packages emerge from need:** packages and the `simulator-evaluation`
  module were created only when concrete classes required them
