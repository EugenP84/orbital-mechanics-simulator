# ADR-007: External NEO Data Acquisition and Trust Boundary

**Status:** Accepted  
**Date:** 29.08.2026  
**Modules:** simulator-data, simulator-app (consumer)  

## Context

Objective 4 requires the simulator to load real tracked near-Earth objects
from NASA's NeoWs (Near Earth Object Web Service) and to provide an offline
fallback, so that the tool remains usable without a network connection.
Objective 6 requires comparing simulated close-approach predictions against
published NASA CNEOS data.

`simulator-core` is deterministic: identical inputs yield identical outputs,
and every collaborator has a total contract. An external web service has no
such contract. It can be unreachable, time out, rate-limit, return a server
error, reject the request, or return a malformed or unexpected body. Its JSON
schema is owned by NASA and can change independently of this project. Its data
is time-dependent, because close-approach records describe specific instants
and windows and the service's contents change as new observations arrive.

This ADR defines how an unreliable, externally owned data representation
enters a deterministic simulation system without bringing HTTP and API
concerns into the core, and without making the application dependent on
network availability.

The ADR covers acquisition, trust, caching, provenance and normalisation. The
conversion from orbital elements to Cartesian state vectors is deterministic,
provider-independent astrodynamics that belongs to `simulator-core` and is the
subject of ADR-009.

## Decision Drivers

- Keep `simulator-core` free of HTTP, JSON, API keys and NASA-specific types,
  as it is free of JavaFX.
- Make every layer testable without a live network.
- Distinguish external failures, which may fall back to cache, from internal
  processing defects, which must surface. Classify by cause.
- Report data provenance in every result, because whether data is live or
  cached, and how old it is, matters educationally and scientifically.
- Never present cached data as satisfying a request it does not cover.
- Preserve the dependency direction: data acquisition produces values and does
  not drive the engine.
- Do not require a NASA API key to develop or to run ordinary tests.

## Decision

### 1. A new module, `simulator-data`, owning its acquisition types

`simulator-data` encapsulates all NASA NeoWs communication and all external
data representation. `simulator-app` depends on it. It never depends on
`simulator-learning`, and the engine never depends on it.

```
simulator-core     (deterministic domain, engine, orbital mathematics)
     ↑
simulator-data     (external acquisition, this ADR)
     ↑
simulator-app      (decides whether to build a simulation from acquired data)
```

`simulator-data` may depend on `simulator-core` for provider-independent
astrodynamics types or capabilities required by downstream mapping, including
those introduced by ADR-009. Its acquisition result types (`NeoDataResult`,
`NeoObjectData`, provenance metadata and `DataSource`) are owned by
`simulator-data`. They are provider-neutral, but they describe data
acquisition and do not belong in the simulation core.

`simulator-data` need not invoke the orbital converter. Acquisition and
conversion stay apart:

```
NASA → simulator-data → NeoObjectData → simulator-app
                                            → core orbital conversion
                                                → BodySnapshot → SimulationEngine
```

The module emits provider-neutral data, and the application composes it with
the core conversion into simulation input (section 8).

### 2. NASA sits behind a provider interface

The rest of the application obtains NEO data through a `NeoDataProvider`
interface that exposes no HTTP details, endpoints, JSON field names, DTOs or
API keys. Its one operation, `findCloseApproaches`, takes the query parameters
and returns a `NeoDataResult`.

The NASA implementation is one strategy behind this interface, as with
`AccelerationModel` and `Integrator`. Another provider, such as a different
service or a purely local dataset, would be an alternative implementation. The
offline fallback (section 7) can be expressed the same way.

### 3. HTTP and JSON DTOs are private to the NASA adapter

The NASA-specific data transfer objects, which mirror the NeoWs JSON, are
private to the NASA adapter and never cross the module boundary. The
application never deserialises a network response directly into
`CelestialBody`, `BodySnapshot` or any core type.

NeoWs's schema is controlled by NASA and can change without notice, whereas
the simulation model is controlled by this project. Binding the two directly
would turn an external schema change into a compile-time or runtime break in
the core model. Retrieved data is validated and normalised into
application-owned, provider-neutral representations (for example a
`NeoObjectData` record) before anything downstream uses it.

### 4. Transport, parsing and normalisation are separate, injectable layers

Acquisition is decomposed into independently testable layers:

```
HTTP transport      send request, return raw response (status + body)
      ↓
JSON parsing        raw body → NASA DTOs
      ↓
normalisation       DTOs → provider-neutral NeoObjectData, with validation
      ↓
provider result     NeoDataResult (data + provenance)
```

The HTTP transport is injectable. The NASA provider does not call a concrete
`HttpClient.send(...)` inside logic that also parses and validates. A small
transport abstraction, or at minimum an injected HTTP collaborator, lets tests
supply 200, 400, 401, 403, 429, 500, timeout and malformed responses without a
network. Parsing and normalisation operate on strings and resources, so a
malformed response is exercised with a fixture.

These are layers the design guarantees and need not be separate classes. A
distinct parser or normaliser class is introduced when its responsibility
separates from the provider, as `IntegratorValidation` was extracted once
duplication existed.

### 5. Failure taxonomy: classify by cause, not by processing stage

Fallback to cached data is permitted for failures attributable to the external
service or its representation. It is never permitted for defects in this
project's own code. What decides is the cause of the failure and not the
processing stage that produced the exception. There are three categories.

Category (a), transport and service failures, is fallback-eligible:

- network unreachable
- request timeout
- server or service error (5xx)
- rate limiting (429)

Category (b), external representation, schema and data failures, is
fallback-eligible:

- a syntactically malformed body (truncated or invalid JSON)
- a body that does not match the expected NeoWs structure at all (for example
  an HTML error page returned with status 200)
- a structurally recognisable body in which a required field is missing,
  renamed, null, or carries an incompatible shape or value after an upstream
  schema or data-quality change

A response can be valid JSON with the expected top level and still fail
normalisation because NASA changed a field. The normaliser is right to reject
it. The failure originates in the external data, so it is an acquisition
failure and is fallback-eligible.

Category (c), internal programming defects, must surface and never falls back:

- an unexpected `NullPointerException`, a broken invariant, an assertion
  failure, or an algorithmic error in this project's own parsing,
  normalisation or mapping logic

Masking category (c) behind a cache fallback would hide errors that testing
exists to catch. A rule that all parsing errors fall back would conceal this
project's defects. A rule that no parsing errors fall back would make offline
mode fragile against NASA changing its output. Classifying by cause avoids
both.

The adapter assigns failures to categories (for example distinct exception or
result types), so that fallback eligibility follows from the category and not
from where the failure surfaced. The category types are not designed in this
ADR.

### 6. HTTP status codes are classified by cause, not by a blanket rule

Non-2xx statuses are not all treated as fallback conditions.

- 429 (rate limiting) is an external acquisition failure and is
  fallback-eligible (category (a)).
- 5xx (server error) is an external service failure and is fallback-eligible
  (category (a)).
- 401 and 403 (authentication and authorisation) are caused by a missing or
  invalid API key, which is a configuration problem in this deployment. They
  surface, because falling back would hide the misconfiguration the operator
  needs to correct.
- 400 (bad request) indicates that the request sent by this application is
  invalid. It surfaces. The cause may be invalid caller input or
  configuration, or a defect in request construction. In neither case is the
  external service unavailable, so the request is never answered from cache.
- Any other status is classified explicitly by the adapter according to its
  cause. There is no default that maps non-2xx to cached data.

### 7. Offline fallback is explicit, query-compatible, and shares the live path

A bundled or persisted cached dataset provides deterministic offline fallback
under three rules.

First, provenance is explicit. An acquisition result carries its source and
the age of the underlying data, so every consumer knows where the data came
from and when it was obtained:

```java
public record NeoDataResult(
        List<NeoObjectData> objects,
        DataSource source,          // LIVE or CACHED
        Instant retrievedAt) { }
```

`retrievedAt` is the time at which the represented dataset was obtained from
its authoritative source. For live data this is the time of the successful
NASA call. For cached data it is the original cache-capture timestamp and not
the time at which the cache is loaded from disk. Otherwise a six-month-old
cache read today would report `retrievedAt = now()` and appear fresh. The
field may be named `capturedAt` in the implementation.

Second, cached data may satisfy a request only if it covers that request. A
cached response captured for one time window or query must not be returned for
a different request because the network failed. That would present
scientifically misleading data under a correct `CACHED` label. Cache metadata
records enough query context, in addition to the capture time, to establish
that the cached dataset's scope covers the request being served. If it does
not, the acquisition failure is reported. For this project the cache can be
simple: a bundled snapshot corresponding to a predefined NEO scenario, whose
scope covers the scenario's own request. The contract prohibits returning
unrelated cached data however the cache is implemented.

Third, cached data flows through the same parser as live data. There is one
parsing and normalisation path, fed by either an HTTP body or a cached JSON
resource:

```
        ┌── live HTTP JSON
common ─┤
parser  └── cached JSON resource
```

A single path avoids two divergent implementations, and the bundled snapshot
can serve as a deterministic test fixture for the code that handles live data.

### 8. `simulator-data` produces values and never drives the engine

Whether to build, reset or populate a `SimulationEngine` from acquired data is
decided by the application layer. `simulator-data` has no reference to the
engine and issues no engine commands. Values flow up to the application, which
composes them into simulation input through the core orbital conversion
(ADR-009).

### 9. The API key is configuration, and its absence is a supported state

The NASA API key is supplied through environment or configuration, is never
committed to source control, and is not needed for ordinary unit tests. It is
required only for the application's live-data mode and for a single opt-in
live integration test.

Four runtime states result:

| Runtime state | Outcome |
|---|---|
| No key, or offline | Cached dataset with provenance `CACHED`, if the cache covers the request (section 7). Otherwise the acquisition failure surfaces. |
| Key present and NASA reachable | Live data with provenance `LIVE`. |
| External acquisition failure (section 5, categories a and b) | Cached dataset with provenance `CACHED`, if the cache covers the request (section 7). Otherwise the acquisition failure surfaces. |
| Internal defect or misconfiguration (section 5, category c; section 6) | The failure surfaces. There is no cache fallback. |

The architecture behaves correctly whether or not a key exists. Routine tests
do not exercise the live path.

### 10. Ordinary tests are deterministic and offline, and live tests are opt-in

No routine Gradle test depends on the network. Parsing, normalisation, failure
classification and fallback are all tested against bundled JSON fixtures and
an injected transport that emulates each response class. A live NASA
integration test exists separately. It is opt-in (a system property or tag),
requires a configured key and is never part of the default test run. This is
an architectural rule: `simulator-data` keeps the determinism of
`simulator-core` by isolating its one non-deterministic collaborator behind an
injection point.

## Consequences

### Positive

- The core stays free of HTTP, JSON and API concerns. An external schema
  change cannot reach the simulation model without passing through
  normalisation, and cannot be mistaken for an internal defect.
- Every failure class (200 with a wrong body, 400, 401, 403, 429, 5xx,
  timeout, malformed body, missing field) is testable offline with a fixture
  and an injected transport, and each maps to a defined outcome: fallback or
  surface.
- With cause-based classification, offline fallback handles external failures
  while configuration and programming defects surface.
- Every result carries its provenance, with `retrievedAt` fixed to
  source-capture time, so cached data cannot appear fresh.
- Cached data cannot answer a request it does not cover.
- A single parsing path serves live and cached data, which reduces code and
  lets the bundled snapshot double as a test fixture.
- Acquisition result types are owned by `simulator-data`, so the core holds no
  acquisition concepts.
- Development and testing do not need an API key.
- The dependency direction is preserved. The engine has no knowledge of data
  acquisition, and conversion is composed above the data module.

### Negative / Accepted Trade-offs

- A provider interface, an injectable transport and provider-neutral types add
  more indirection than a direct `HttpClient`-to-model call. The indirection
  provides offline testability and schema isolation, and it follows the same
  approach as `AccelerationModel` and `Integrator`.
- Normalising DTOs into provider-neutral types duplicates structure between
  the NASA DTO and `NeoObjectData`. The duplication keeps an external schema
  from defining the internal model.
- Classifying failures by cause requires explicit categories: a schema or data
  failure must be distinguished from a mapper defect, and 401, 403 and 400
  from 429 and 5xx. A blanket rule would be simpler but would either hide bugs
  and misconfiguration or make offline mode fragile.
- Cache metadata must record query context and capture time in addition to the
  JSON. Without them, `CACHED` data cannot be trusted to be relevant or
  correctly dated. For this project the metadata can be minimal.
- A bundled cached dataset must be maintained and its capture date recorded.
  Offline operation requires it, and its age is reported to the consumer.