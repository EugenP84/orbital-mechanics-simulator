# Project Structure Reference

**Project:** Orbital Mechanics Simulator
**Author:** Eugeniu PLATITA (EugenP84)
**Date:** July 2026
**Status:** Agreed baseline — Phase 2 (Analysis and Design)

This document defines the agreed module and package structure for the
orbital-mechanics-simulator project. It serves as the architectural map
for implementation and as evidence of deliberate structural design for
the final report.

---

## Technology Stack

| Component      | Version            |
|----------------|--------------------|
| Java           | Eclipse Temurin 21 (LTS) |
| JavaFX         | 21 (LTS)           |
| Gradle         | 8.13 (wrapper)     |
| Build DSL      | Kotlin             |
| Testing        | JUnit 5 (Jupiter)  |
| JSON           | Gson 2.11.0        |
| HTTP           | java.net.http.HttpClient (built-in) |
| IDE            | IntelliJ IDEA      |
| Source Control | Git / GitHub       |

---

## Module Dependency Graph

Dependencies are strictly one-directional. `simulator-core` depends on
nothing else.

```
simulator-app
    │ depends on
    ├──> simulator-learning ──> simulator-core
    ├──> simulator-data     ──> simulator-core
    └──> simulator-core
```

---

## Full Structure

```
orbital-mechanics-simulator/
│
├── simulator-core/                          ← Physics engine + domain model
│   ├── src/main/java/io/github/eugenp84/orbitalsim/core/
│   │   ├── math/                            ← Vector3D, StateVector, constants
│   │   ├── model/                           ← CelestialBody, Star, Planet, NEO
│   │   ├── physics/                         ← Force calculation, energy computation
│   │   ├── integrator/                      ← Euler, VelocityVerlet, RungeKutta4
│   │   │                                       (Strategy pattern)
│   │   ├── simulation/                      ← SimulationEngine, simulation loop
│   │   └── event/                           ← Observer pattern interfaces
│   │
│   └── src/test/java/io/github/eugenp84/orbitalsim/core/
│       ├── integrator/                      ← Integrator accuracy unit tests
│       ├── physics/                         ← Force/energy unit tests
│       └── simulation/                      ← Engine behaviour tests
│
├── simulator-data/                          ← External data + persistence
│   ├── src/main/java/io/github/eugenp84/orbitalsim/data/
│   │   ├── nasa/                            ← NeoWs API client
│   │   │   ├── dto/                         ← Raw API response objects
│   │   │   └── mapping/                     ← DTO → domain model converters
│   │   │                                       (anti-corruption layer)
│   │   ├── persistence/                     ← Simulation state save/load (JSON)
│   │   └── library/                         ← Planet library, glossary loading
│   │
│   ├── src/main/resources/
│   │   └── planets/                         ← Planet library JSON, NEO cache
│   │
│   └── src/test/java/io/github/eugenp84/orbitalsim/data/
│
├── simulator-learning/                      ← Educational layer
│   ├── src/main/java/io/github/eugenp84/orbitalsim/learning/
│   │   ├── scenario/                        ← Levels L0–L4
│   │   │                                       (State + Template Method patterns)
│   │   └── hint/                            ← Adaptive hint engine, inference rules
│   │
│   ├── src/main/resources/
│   │   └── scenarios/                       ← Scenario configuration files
│   │
│   └── src/test/java/io/github/eugenp84/orbitalsim/learning/
│
├── simulator-app/                           ← JavaFX application (MVC)
│   ├── src/main/java/io/github/eugenp84/orbitalsim/app/
│   │   ├── controller/                      ← MVC controllers, input handling
│   │   ├── rendering/                       ← Canvas renderer, trajectory trails
│   │   ├── configuration/                   ← App configuration, DI wiring
│   │   └── Main.java                        ← Entry point
│   │
│   ├── src/main/resources/io/github/eugenp84/orbitalsim/app/
│   │   ├── fxml/                            ← FXML view definitions
│   │   ├── css/                             ← Stylesheets
│   │   └── images/                          ← Icons, textures
│   │
│   └── src/test/java/io/github/eugenp84/orbitalsim/app/
│
├── simulator-evaluation/                    ← DEFERRED — create in Phase 4/5
│   │                                           when evaluation begins
│   ├── src/test/java/
│   │   ├── accuracy/                        ← Integrator comparison tests
│   │   ├── validation/                      ← NEO vs CNEOS validation
│   │   └── benchmark/                       ← Performance benchmarks
│   └── results/                             ← CSV exports, analysis outputs
│
├── docs/
│   ├── architecture/                        ← This document, module diagrams
│   ├── diagrams/                            ← Class diagrams, sequence diagrams
│   ├── decisions/                           ← Architecture Decision Records (ADRs)
│   └── evaluation/                          ← Evaluation notes and results
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitattributes
├── .gitignore
└── README.md
```

---

## Design Decisions Embedded in This Structure

| Decision | Rationale |
|----------|-----------|
| `integrator/` not `integration/` | Avoids ambiguity with integration testing terminology |
| `dto/` + `mapping/` split in `nasa/` | Anti-corruption layer — domain model stays independent of NASA API structure |
| No `viewmodel/` package | Consistent with documented MVC architecture in proposal |
| No `i18n/` resources | Internationalisation is out of scope |
| `feedback/` and `progress/` deferred | Packages created only when actual classes demand them |
| `simulator-evaluation` deferred | Created in Phase 4/5 when evaluation activities begin |
| ADRs in `docs/decisions/` | Dated evidence trail of justified decision-making for the report |

---

## Package-to-Pattern Mapping

| Package | Design Pattern | Proposal Reference |
|---------|---------------|--------------------|
| `core/integrator/` | Strategy | Interchangeable numerical integration methods |
| `core/model/` + factory in `core/simulation/` | Factory | Flexible celestial object creation |
| `core/event/` | Observer | Decoupled simulation → UI / hint engine communication |
| `learning/scenario/` | State | Level progression logic |
| `learning/scenario/` | Template Method | Common scenario lifecycle structure |

---

## Conventions

- **Base package:** `io.github.eugenp84.orbitalsim`
- **Module prefix:** each module owns one sub-package (`core`, `data`, `learning`, `app`)
- **Tests mirror sources:** test packages replicate main package structure
- **Resources:** JSON data lives in the owning module's resources
- **Packages emerge from need:** no speculative empty packages
