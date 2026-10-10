# ADR-001: Technology Stack and Modular Project Structure

**Status:** Accepted  
**Date:** 15.07.2026  
**Modules:** all  


---

## Context

The project proposal commits to implementing a JavaFX desktop application
containing a small-N gravitational simulation engine, progressive learning
scenarios, an adaptive hint system, and integration with the NASA NeoWs API.
Before implementation could begin, the development environment, language
version, build tooling and repository structure had to be fixed.

Two decisions had to be made together, because they constrain each other and
because both are costly to reverse once source code exists:

1. **Which versions of the core technologies to adopt**, given that the
   project runs to a fixed submission deadline and cannot absorb toolchain
   instability.
2. **Whether to organise the codebase as a single module with package
   separation, or as a multi-module Gradle build.**

## Decision Drivers

- Stability over novelty: the project is time-bounded and toolchain problems
  consume implementation time directly.
- Depth of available community support, since problems encountered will be
  solved by searching rather than by vendor support.
- Support for future extension, as identified in the proposal's Future Work.
- Enforceable architectural boundaries between simulation, data, education and
  presentation concerns.
- Reproducibility across two development machines and for the marker.

## Options Considered

### Technology versions

| Component | Options considered | Chosen | Rationale |
|---|---|---|---|
| Java | 21 LTS, 23 (non-LTS), 25 LTS | **21 LTS (Eclipse Temurin)** | Java 23 was already installed but is non-LTS and out of support. Java 25 is the current LTS but newer, with less community troubleshooting material and some libraries not yet exercised against it. Java 21 is supported to 2031, is the baseline most frameworks and tooling target, and offers no relevant feature deficit for this project. |
| JavaFX | 21 LTS, 25 LTS | **21 LTS** | Pairs with the chosen Java LTS; actively maintained. |
| Gradle | 8.13 | **8.13, via wrapper** | Compatible with Java 21; wrapper committed so both development machines and the marker build with an identical version without local installation. |
| Build DSL | Groovy, Kotlin | **Kotlin** | Better IDE autocomplete and type safety. Accepted trade-off: most JavaFX and Gradle examples available online are written in Groovy DSL and require translation. |
| JSON | Gson, Jackson | **Gson 2.11.0** | The proposal permitted either. Gson requires minimal configuration and is sufficient for the known, fixed schemas used here (planet library, glossary, NEO cache, simulation state). Jackson's additional capability would not be exercised. |
| HTTP client | `java.net.http.HttpClient`, external library (e.g. OkHttp, Apache HttpClient) | **Built-in `HttpClient`** | Available in the JDK since Java 11; sufficient for the NeoWs REST calls required by Objective 4. Avoids a dependency entirely. |
| Testing | JUnit 4, JUnit 5 (Jupiter) | **JUnit 5 (Jupiter)** | Current standard; required for the parameterised and nested test structures anticipated in integrator evaluation. |

### Project structure

**Option A — Single module with package separation.**
One Gradle module; architectural separation expressed through Java packages
(`engine`, `model`, `learning`, `data`, `ui`, `controller`).

*Considered and initially preferred.* Lower configuration overhead, no
cross-module dependency management, and sufficient to express the MVC
architecture described in the proposal. Package structure alone would have
satisfied the assessment criteria, which concern architectural reasoning
rather than build topology.

**Option B — Seven-module build.**
Separate modules for `app`, `engine`, `model`, `learning`, `data`, `tests` and
`docs`.

*Rejected.* `model` and `engine` are tightly coupled and would change
together; separating them would add a dependency edge with no benefit at this
scale. A separate `tests` module fragments tests away from the code they
exercise. `docs` is not a build module.

**Option C — Four-module build (chosen).**
`simulator-core`, `simulator-data`, `simulator-learning`, `simulator-app`,
with `simulator-evaluation` deferred until evaluation work begins.

## Decision

Adopt the technology versions tabulated above, and organise the codebase as a
four-module Gradle build with a strictly one-directional dependency graph:

```
simulator-app
    │ depends on
    ├──> simulator-learning ──> simulator-core
    ├──> simulator-data     ──> simulator-core
    └──> simulator-core
```

`simulator-core` depends on no other module.

Each module owns one sub-package of the base package
`io.github.eugenp84.orbitalsim`, by convention rather than by any mechanism
Gradle enforces.

`simulator-evaluation` is deferred to Phase 4/5, when evaluation activities
begin, rather than being created empty.

The Gradle wrapper is committed to version control so that the build is
reproducible without a local Gradle installation.

## Rationale for the Structure Decision

The multi-module structure was adopted *against* an initial recommendation for
Option A. The deciding consideration was extensibility, identified in the
proposal's Future Work section: additional celestial body kinds, additional
data sources, additional learning scenarios, and potentially alternative
presentation layers.

Module boundaries enforce the dependency direction at build time. Package
boundaries do not: within a single module, any class may import any other, and
an accidental dependency from the physics engine onto JavaFX or onto NASA
data-transfer objects would compile without complaint. Given that the
architecture's central claim is a physics core independent of presentation and
data-source concerns, having that claim mechanically enforced rather than
merely intended was judged worth the configuration cost.

The reduction from seven modules to four reflects the same principle applied
in the opposite direction: modules were retained only where a genuine
independent axis of change exists.

## Consequences

### Positive

- Dependency direction is enforced by the build; violations fail compilation
  rather than passing unnoticed.
- `simulator-core` can be tested and reasoned about without JavaFX, NASA data,
  or scenario logic present.
- A second data source, or an alternative front end, would be additive rather
  than invasive.
- The committed wrapper makes the build reproducible on both development
  machines and for the marker.
- Java 21's language features (records, compact canonical constructors) are
  available and are used in the domain model (see ADR-002).

### Negative / Accepted Trade-offs

- Four `build.gradle.kts` files must be maintained rather than one, and each
  cross-module dependency must be declared explicitly.
- Kotlin DSL requires translating Groovy-syntax examples found in online
  documentation and community answers.
- Java 21 rather than Java 25 forgoes the most recent language features; none
  were identified as relevant to this project.
- Module structure adds build complexity that does not itself earn assessment
  credit. It is justified by the architectural guarantee it provides, not by
  its own presence — consistent with the project guidance that complexity must
  arise from intellectual challenge rather than structural overhead.

## Guiding Principle

Choose the most stable option that meets the requirement; adopt structure only
where it enforces a boundary that matters; and record the trade-off when the
more complex option is chosen deliberately.
