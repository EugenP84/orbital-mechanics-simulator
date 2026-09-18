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
| `simulator-data`        | External NEO data acquisition, offline provenance-locked fixtures          |
| `simulator-learning`    | Educational scenario layer (levels, sessions, completion policies, hints)  |
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

```
orbital-mechanics-simulator/
│
├── .gitattributes
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── project-structure.txt                     ← exported IDE tree (source of this doc)
├── README.md
│
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
│
├── docs/
│   ├── architecture/
│   │   └── project-structure.md               ← this document
│   └── decisions/                             ← Architecture Decision Records (11)
│       ├── ADR-001-technology-stack-and-structure.md
│       ├── ADR-002-celestial-body-model.md
│       ├── ADR-003-newtonian-gravity-model.md
│       ├── ADR-004-acceleration-aggregation.md
│       ├── ADR-005-integration-contract.md
│       ├── ADR-006-simulation-engine-execution-model.md
│       ├── ADR-007-external-neo-data-acquisition.md
│       ├── ADR-008-learning-scenario-architecture.md
│       ├── ADR-009-orbital-state-analysis.md
│       ├── ADR-010-presentation-architecture-and-multi-level-shell.md
│       └── ADR-011-level-4-real-neo-close-approach-experiment-and-validation.md
│
├── simulator-core/                            ← Physics engine + domain model
│   ├── build.gradle.kts
│   ├── src/main/java/io/github/eugenp84/orbitalsim/core/
│   │   ├── integrator/                        ← numerical integration (Strategy)
│   │   │   ├── EulerIntegrator.java
│   │   │   ├── Integrator.java
│   │   │   ├── IntegratorValidation.java
│   │   │   ├── RungeKutta4Integrator.java
│   │   │   └── VelocityVerletIntegrator.java
│   │   ├── math/
│   │   │   ├── PhysicalConstants.java
│   │   │   ├── StateVector.java
│   │   │   └── Vector3D.java
│   │   ├── model/
│   │   │   ├── CelestialBody.java
│   │   │   ├── CelestialBodyType.java
│   │   │   └── SimulationBody.java
│   │   ├── orbital/                           ← osculating orbital-element analysis
│   │   │   ├── OrbitalStateAnalysis.java
│   │   │   └── OrbitalStateCalculator.java
│   │   ├── physics/
│   │   │   ├── AccelerationModel.java
│   │   │   ├── EnergyCalculator.java
│   │   │   ├── GravitationalBodyConfig.java
│   │   │   ├── GravityCalculator.java
│   │   │   └── NBodyAccelerationCalculator.java
│   │   └── simulation/
│   │       ├── AdvanceResult.java
│   │       ├── BodySnapshot.java
│   │       ├── ListenerErrorHandler.java
│   │       ├── SimulationEngine.java
│   │       ├── SimulationListener.java        ← Observer contract
│   │       ├── SimulationSnapshot.java
│   │       ├── StepResult.java                ← sealed: Advanced | NumericalBreakdown
│   │       └── TwoBodyInitialState.java
│   └── src/test/java/io/github/eugenp84/orbitalsim/core/
│       ├── integrator/
│       │   ├── EulerIntegratorTest.java
│       │   ├── IntegratorComparisonTest.java
│       │   ├── IntegratorConvergenceTest.java
│       │   ├── RungeKutta4IntegratorTest.java
│       │   └── VelocityVerletIntegratorTest.java
│       ├── math/
│       │   ├── StateVectorTest.java
│       │   └── Vector3DTest.java
│       ├── model/
│       │   ├── CelestialBodyTest.java
│       │   └── SimulationBodyTest.java
│       ├── orbital/
│       │   └── OrbitalStateCalculatorTest.java
│       ├── physics/
│       │   ├── EnergyCalculatorTest.java
│       │   ├── GravityCalculatorTest.java
│       │   └── NBodyAccelerationCalculatorTest.java
│       └── simulation/
│           ├── SimulationEngineTest.java
│           ├── SimulationListenerTest.java
│           └── TwoBodyInitialStateTest.java
│
├── simulator-data/                            ← External NEO data + offline fixtures
│   ├── build.gradle.kts
│   ├── src/main/java/io/github/eugenp84/orbitalsim/data/
│   │   ├── horizons/                          ← JPL Horizons fixture (L4 initial state)
│   │   │   ├── HorizonsDataFormatException.java
│   │   │   ├── HorizonsEpochState.java
│   │   │   ├── HorizonsExperimentData.java
│   │   │   ├── HorizonsExperimentLoader.java
│   │   │   └── HorizonsProvenance.java
│   │   ├── http/
│   │   │   ├── HttpResponseData.java
│   │   │   ├── HttpTransport.java
│   │   │   ├── HttpTransportException.java
│   │   │   ├── HttpTransportInterruptedException.java
│   │   │   └── JdkHttpTransport.java
│   │   ├── nasa/                              ← NeoWs feed client + DTOs + parser
│   │   │   ├── BundledNeoDataProvider.java
│   │   │   ├── CloseApproachDto.java
│   │   │   ├── EstimatedDiameterDto.java
│   │   │   ├── FeedResponseDto.java
│   │   │   ├── NasaNeoDataProvider.java
│   │   │   ├── NasaNeoWsParser.java
│   │   │   ├── NeoDataFormatException.java
│   │   │   └── NeoDto.java
│   │   └── neo/                               ← neutral NEO domain result types
│   │       ├── CloseApproach.java
│   │       ├── DataSource.java
│   │       ├── NeoDataProvider.java
│   │       ├── NeoDataResult.java
│   │       ├── NeoDataUnavailableException.java
│   │       └── NeoObjectData.java
│   ├── src/main/resources/neo/
│   │   ├── apophis-2029-horizons.json
│   │   ├── neows-feed-2029-04-13.json
│   │   └── neows-feed-2029-04-13.meta.json
│   ├── src/test/java/io/github/eugenp84/orbitalsim/data/
│   │   ├── horizons/
│   │   │   └── HorizonsExperimentLoaderTest.java
│   │   ├── http/
│   │   │   └── FakeHttpTransport.java
│   │   ├── nasa/
│   │   │   ├── NasaNeoDataProviderTest.java
│   │   │   └── NasaNeoLiveIntegrationTest.java
│   │   └── neo/
│   │       ├── BundledNeoDataProviderTest.java
│   │       └── NeoDataContractsTest.java
│   └── src/test/resources/neo/
│       ├── neows-feed-2029-04-13.json
│       ├── neows-feed-2029-04-13.meta.json
│       ├── neows-feed-corrupt.json
│       └── neows-feed-corrupt.meta.json
│
├── simulator-learning/                        ← Educational scenario layer
│   ├── build.gradle.kts
│   ├── src/main/java/io/github/eugenp84/orbitalsim/learning/scenario/
│   │   ├── Hint.java
│   │   ├── HintCategory.java
│   │   ├── HintSource.java
│   │   ├── Level0Attempt.java
│   │   ├── Level0PlanetCreationCompletionPolicy.java
│   │   ├── Level0Scenario.java
│   │   ├── Level1Attempt.java
│   │   ├── Level1Criteria.java
│   │   ├── Level1HintEngine.java
│   │   ├── Level1Scenario.java
│   │   ├── Level1StableOrbitCompletionPolicy.java
│   │   ├── Level3Attempt.java
│   │   ├── Level3CompletionPolicy.java
│   │   ├── Level3ProgressSource.java
│   │   ├── Level3Scenario.java
│   │   ├── Level4Attempt.java
│   │   ├── Level4CompletionPolicy.java
│   │   ├── Level4InitialConditions.java
│   │   ├── Level4ProgressSource.java
│   │   ├── Level4Scenario.java
│   │   ├── ScenarioAttemptNotAssessableException.java
│   │   ├── ScenarioCompletionPolicy.java      ← Strategy contract for completion
│   │   ├── ScenarioEvaluation.java
│   │   ├── ScenarioLifecycleState.java
│   │   ├── ScenarioObservation.java
│   │   ├── ScenarioObservationCadence.java
│   │   ├── ScenarioSession.java               ← sole engine-advancement boundary
│   │   ├── ScenarioTerminationReason.java
│   │   └── TimeHorizonCompletionPolicy.java
│   └── src/test/java/io/github/eugenp84/orbitalsim/learning/scenario/
│       ├── Level0ScenarioTest.java
│       ├── Level1HintEngineTest.java
│       ├── Level1ScenarioTest.java
│       ├── Level1StableOrbitCompletionPolicyTest.java
│       ├── Level3ScenarioTest.java
│       ├── Level4ScenarioTest.java
│       ├── ScenarioObservationCadenceTest.java
│       ├── ScenarioSessionTest.java
│       └── TimeHorizonCompletionPolicyTest.java
│
├── simulator-app/                             ← JavaFX presentation shell
│   ├── build.gradle.kts
│   ├── src/main/java/io/github/eugenp84/orbitalsim/app/
│   │   ├── IntegratorComparisonScreen.java
│   │   ├── Level0Screen.java
│   │   ├── Level1Screen.java
│   │   ├── Level3Screen.java
│   │   ├── Level4Screen.java
│   │   ├── LevelScreen.java                   ← shared level host/screen abstraction
│   │   ├── Main.java                          ← entry point / multi-level shell
│   │   ├── SimulationViewCore.java            ← shared presentation core (composition)
│   │   ├── composition/
│   │   │   └── Level4AttemptFactory.java      ← data↔learning boundary
│   │   ├── controller/
│   │   │   ├── AttemptCreationResult.java
│   │   │   ├── IntegratorComparisonController.java
│   │   │   ├── IntegratorComparisonState.java
│   │   │   ├── Level0Controller.java
│   │   │   ├── Level3Controller.java
│   │   │   ├── Level4Controller.java
│   │   │   ├── ScenarioControls.java          ← narrow control interface
│   │   │   ├── ScenarioPlayer.java
│   │   │   └── SimulationController.java      ← also drives Level 1
│   │   └── rendering/
│   │       ├── SimulationRenderer.java
│   │       ├── TrajectoryTrace.java
│   │       ├── TrajectoryTrail.java
│   │       └── ViewTransform.java
│   └── src/test/java/io/github/eugenp84/orbitalsim/app/
│       ├── composition/
│       │   └── Level4AttemptFactoryTest.java
│       ├── controller/
│       │   ├── IntegratorComparisonControllerTest.java
│       │   ├── Level0ControllerTest.java
│       │   ├── Level3ControllerTest.java
│       │   ├── Level4ControllerTest.java
│       │   ├── ScenarioPlayerTest.java
│       │   └── SimulationControllerTest.java
│       └── rendering/
│           └── TrajectoryTrailTest.java
│
└── simulator-evaluation/                      ← Headless evaluation experiments
    ├── build.gradle.kts
    ├── src/main/java/io/github/eugenp84/orbitalsim/evaluation/integrator/
    │   ├── IntegratorComparisonExperiment.java
    │   ├── IntegratorComparisonResult.java
    │   ├── IntegratorComparisonRun.java
    │   └── IntegratorMethod.java
    ├── src/test/java/io/github/eugenp84/orbitalsim/evaluation/
    │   ├── integrator/
    │   │   ├── IntegratorComparisonExperimentTest.java
    │   │   └── IntegratorComparisonRunTest.java
    │   └── neo/                               ← Apophis 2029 validation vs CNEOS
    │       ├── ApophisConvergenceExperiment.java
    │       ├── ApophisEncounterExperiment.java
    │       ├── ApophisEncounterExperimentTest.java
    │       ├── ApophisExternalValidationExperiment.java
    │       ├── CneosCloseApproachReference.java
    │       ├── CneosReferenceFixture.java
    │       ├── CneosReferenceLoader.java
    │       ├── CneosReferenceLoaderTest.java
    │       ├── EncounterMeasurement.java
    │       └── ExternalValidationMeasurement.java
    └── src/test/resources/neo/
        └── apophis-2029-cneos-reference.json
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

### Patterns considered but not adopted

| Pattern         | Original plan location        | Outcome |
|-----------------|-------------------------------|---------|
| State           | `learning/scenario/` (level progression) | Not adopted; scenario lifecycle handled by `ScenarioSession` + `ScenarioLifecycleState` enum without a State-object hierarchy (ADR-008/ADR-010) |
| Template Method | `learning/scenario/` (common lifecycle)  | Not adopted; shared behaviour achieved by composition, not inheritance |
| Factory         | `core/model/` + `core/simulation/`       | Not adopted as a distinct pattern; direct construction / narrow composition classes used instead |

---

## As-Built vs Planned Divergences

The earlier baseline document described a planned structure. The delivered
system diverges from it in the following deliberate, evidence-driven ways:

- **`simulator-evaluation` is built, not deferred.** It contains the
  headless integrator-comparison experiment and the Apophis 2029 external
  validation suite (against a provenance-locked JPL CNEOS reference).
- **Levels implemented are L0, L1, L3, L4 — there is no L2.** The scenario
  package holds these four levels only.
- **Data module is organised as `horizons/`, `http/`, `nasa/`, `neo/`** —
  not the planned `nasa/dto` + `nasa/mapping` + `persistence/` + `library/`
  layout. There is no persistence or planet-library package, and no
  `resources/planets/` directory. The bundled resources are the NEO fixtures
  under `resources/neo/`.
- **No `core/event/` package.** The Observer contract lives directly in
  `core/simulation/` as `SimulationListener`.
- **A `core/orbital/` package exists** (`OrbitalStateAnalysis`,
  `OrbitalStateCalculator`) — osculating orbital-element analysis, added per
  ADR-009. It was not in the original plan.
- **The app layer uses `composition/`, `controller/`, `rendering/`** — there
  is no `configuration/` package, and no FXML/CSS/image resources; the UI is
  built programmatically. Screens and the shared `SimulationViewCore` sit at
  the `app` package root.
- **Level 1 has no dedicated controller.** It is driven by the shared
  `SimulationController`; L0, L3 and L4 have their own controllers.
- **Eleven ADRs are present** (ADR-001 … ADR-011), including ADR-010
  (presentation architecture and multi-level shell) and ADR-011 (Level 4
  real-NEO close-approach experiment and validation).
- **No `gradle.properties`, `docs/diagrams/` or `docs/evaluation/`** exist in
  the current tree; the earlier baseline listed them speculatively.

---

## Conventions

- **Base package:** `io.github.eugenp84.orbitalsim`
- **Module prefix:** each module owns one sub-package
  (`core`, `data`, `learning`, `app`, `evaluation`)
- **Tests mirror sources:** test packages replicate main package structure
- **Resources:** JSON fixtures live in the owning module's resources, under
  a `neo/` folder
- **Packages emerge from need:** packages and the `simulator-evaluation`
  module were created only when concrete classes required them
