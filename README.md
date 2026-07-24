# Orbital Mechanics Simulator

A gamified orbital mechanics and near-Earth object simulator for
introductory astrophysics education.

Developed by Eugeniu Platita as an MSc Computer Science project.

## Technology stack

- Java 21 LTS (Eclipse Temurin)
- JavaFX 21
- Gradle 8.13 (wrapper included)
- JUnit 5 (Jupiter)
- Gson 2.11.0

## Modules

| Module | Responsibility |
|---|---|
| `simulator-core` | Domain model, physics engine, numerical integrators |
| `simulator-data` | NASA NeoWs integration and JSON persistence |
| `simulator-learning` | Learning scenarios and adaptive hint system |
| `simulator-app` | JavaFX desktop application |

## Building and running

    ./gradlew build
    ./gradlew :simulator-app:run

Requires JDK 21. The Gradle wrapper handles the Gradle version.

## Documentation

- [Project structure and design decisions](docs/architecture/project-structure.md)

## Status

Phase 2 — Analysis and Design.