# Orbital Mechanics Simulator

A gamified orbital mechanics and near-Earth object (NEO) simulator for
introductory astrophysics education.

Developed by Eugeniu Platita.

## Overview

The simulator combines a small-N Newtonian gravitational engine, five
interactive learning scenarios (L0–L4), rule-based adaptive hints, NASA NEO
data and a comparison of numerical integration methods.

Three integration methods are implemented: Forward Euler, Velocity Verlet
and classical fourth-order Runge-Kutta (RK4). The comparison screen runs
each method from identical initial conditions and measures energy
deviation, trajectory error against an analytical orbit, and the number of
acceleration-model evaluations.

## Technology stack

- Java 21 LTS (Eclipse Temurin)
- JavaFX 21
- Gradle 8.13 (wrapper included)
- JUnit 5 (Jupiter)
- Gson 2.11.0
- `java.net.http.HttpClient` for NeoWs access

## Modules

| Module | Responsibility | Status |
| --- | --- | --- |
| `simulator-core` | Domain model, Newtonian gravity, numerical integrators, orbital-state analysis and simulation engine | Implemented and tested |
| `simulator-data` | NASA NeoWs integration, provider-neutral NEO data, offline support, JPL Horizons initial states, planet reference data and HTTP transport | Implemented and tested |
| `simulator-learning` | Scenario lifecycle, learning scenarios L0–L4 and adaptive hints | Implemented and tested |
| `simulator-evaluation` | Numerical-integrator comparison and measurement | Implemented and tested |
| `simulator-app` | JavaFX application: rendering, controls, navigation and comparison screen | Implemented and tested |

The computational core does not depend on JavaFX or on external services.

### `simulator-core`

The core contains six packages:

- `math`: immutable `Vector3D` and `StateVector` value types and physical
  constants;
- `model`: celestial-body definitions and runtime simulation-body state;
- `physics`: Newtonian gravity, energy diagnostics and N-body acceleration
  evaluation behind `AccelerationModel`;
- `integrator`: the common `Integrator` contract and the Forward Euler,
  Velocity Verlet and RK4 implementations;
- `simulation`: committed simulation state, fixed numerical timesteps,
  atomic state advancement and immutable snapshots;
- `orbital`: orbital-state analysis (specific orbital energy, eccentricity
  and semi-major axis) used to evaluate the learning scenarios.

The physics and numerical layers are covered by automated tests, including
known-value gravitational checks and convergence-order tests for each
integrator.

### `simulator-data`

NASA NeoWs access sits behind a provider interface (`NeoDataProvider`). The
module contains the NASA-specific JSON parsing, the live HTTP client, a
bundled NeoWs response for offline use, explicit handling of
external-service failures and an injectable HTTP transport, so that tests
run without a network. NEO data leave the module as provider-neutral values
in SI units.

The module also holds the JPL Horizons state vectors of Earth and asteroid
99942 Apophis used to initialise L4, and the Solar-System planet catalogue
used by L2. The catalogue is loaded from JSON and records its NASA/NSSDCA
source.

`simulator-learning` does not depend on this module. The application passes
each scenario the data it needs.

### `simulator-learning`

The learning layer owns the scenario lifecycle, the observation cadence,
the completion and failure policies and the adaptive hints. It has no
JavaFX dependency.

#### L0: Planet Creation

The learner sets a planet's mass, radius, orbital distance and tangential
speed and watches the resulting motion. L0 is introductory: it has no
physical success or failure evaluation.

#### L1: Stable Orbit

The learner adjusts the initial tangential speed of a planet around a star
and tries to establish a near-circular stable orbit. The attempt is
evaluated from the simulated state: the orbit must stay bound, keep its
eccentricity within the scenario limit and complete the required number of
revolutions. Physics-informed rules compare the chosen speed with the
circular speed and guide the next attempt.

#### L2: Planet Comparison

L2 is an exploratory comparison with no simulation, pass/fail criterion. The planet created in L0 is compared with any of the eight
Solar-System planets by mass, mean radius, mean density and surface
gravity. Density and surface gravity of the learner's planet are calculated
from its mass and radius; the reference values come from the bundled
NASA/NSSDCA catalogue.

The L0 planet is held in memory by the application and is not saved between
sessions.

#### L3: Gravitational Perturbation

Three mutually gravitating bodies are simulated: a Sun-like star, an
Earth-like target planet starting near 1 AU and a perturbing planet. The
learner sets the perturber's mass (0.3–3.0 Jupiter masses) and its initial
distance from the star (1.3–2.0 AU), then runs a six-year experiment with
Velocity Verlet at a six-hour timestep.

The scenario reports the maximum eccentricity reached by the target planet.
An attempt fails only if the target touches the star or the perturber.
Reaching the six-year horizon completes the experiment and says nothing
about long-term stability.

#### L4: Near-Earth Object

L4 simulates the close approach of asteroid 99942 Apophis to Earth on
13 April 2029. Earth and the asteroid start from JPL Horizons state vectors
taken one day before the encounter. The Sun and Earth act on the asteroid,
and the asteroid's own gravity is neglected. The run uses Velocity Verlet
with a 112.5-second timestep.

The scenario tracks the Earth–asteroid distance and reports the minimum
distance, the time at which it occurred and the relative speed at that
point. The close-approach record from NASA NeoWs is shown with its source
(live or cached). L4 is an observation with no pass/fail assessment, and
its result is an educational approximation, not an operational prediction.

### `simulator-evaluation`

The evaluation module provides the numerical-integrator comparison
experiment, independent of JavaFX. The in-application comparison uses:

- a fully coupled Sun-Earth-like two-body system;
- identical centre-of-mass initial conditions for every method;
- a fixed timestep of 21,600 seconds (6 hours);
- 1,461 committed steps (365.25 simulated days);
- a fresh run for each integrator.

It measures the maximum relative deviation of total mechanical energy, the
maximum error of the relative position against an analytical circular-orbit
reference, and the actual number of acceleration-model evaluations.

The same comparison logic runs incrementally for the JavaFX screen and
headlessly for the formal evaluation. The module's test sources also
contain the Apophis convergence and close-approach validation experiments,
which compare the simulated encounter with a preserved NASA/JPL reference
record.

### `simulator-app`

The JavaFX application presents the five learning scenarios and the
integrator comparison. `Main` hosts the window and the persistent
navigation, and each screen implements the `LevelScreen` contract. The
simulation-based levels share rendering, trajectory trails and run
controls, and each level adds its own controls and controller.
`Level2Screen` has no simulation. `Main` also passes the current L0 planet
to L2.

The time-rate control changes how much simulated time is requested per
second of display. The integration timestep stays fixed.

## Building and running

JDK 21 is required. The Gradle wrapper supplies the configured Gradle
version.

From the project root:

    ./gradlew build
    ./gradlew :simulator-app:run

On Windows:

    .\gradlew.bat build
    .\gradlew.bat :simulator-app:run

Complete test suite:

    ./gradlew test

Core tests only:

    ./gradlew :simulator-core:test

Evaluation tests only:

    ./gradlew :simulator-evaluation:test

## NASA NeoWs access

Live NeoWs access requires a NASA API key. The key is supplied outside the
repository and must not be committed. Builds and automated tests need
neither a key nor a network connection: they use bundled data and an
injected HTTP transport. One live integration test exists and runs only
when explicitly enabled.

If a live request fails for an external reason (service unreachable, rate
limiting or a malformed response), the application falls back to the
bundled NeoWs response for 13 April 2029 and labels the result as cached,
with its capture time. Authentication and configuration errors are reported
and do not trigger the fallback.

## Design decisions

Architecture and significant design choices are recorded as Architecture
Decision Records (ADRs) under `docs/decisions/`.

## Documentation

- [Project structure and design decisions](docs/architecture/project-structure.md)
- [Architecture Decision Records](docs/decisions/)
- [Integrator-comparison evaluation evidence](docs/evaluation/integrator-comparison/)
- [NEO validation evidence](docs/evaluation/neo-validation/)

## Status

Implemented and tested.