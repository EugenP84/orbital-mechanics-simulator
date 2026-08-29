# ADR-007: External NEO Data Acquisition and Trust Boundary

**Status:** Accepted  
**Date:** 29.08.2026  
**Modules:** simulator-data (new), simulator-app (consumer)  
**Related:** Project Proposal §2.4 (Data Layer and NASA NEO Integration), §3.5 (Design Patterns); Objective 4 (NeoWs integration with offline fallback), Objective 6 (CNEOS validation); ADR-002 (definition–state separation), ADR-006 (engine boundary and immutable snapshots); ADR-008 (orbital conversion, forthcoming)

---

## Context

Objective 4 requires the simulator to load real tracked near-Earth objects
from NASA's NeoWs (Near Earth Object Web Service) and, per the proposal, to
provide an offline fallback so the tool remains usable without a network
connection. Objective 6 additionally requires comparing simulated
close-approach predictions against published NASA CNEOS data.

This is a different kind of component from everything built so far.
`simulator-core` is entirely deterministic: identical inputs yield identical
outputs, and every collaborator honours a total contract. An external web
service honours no such contract. It can be unreachable, time out, rate-limit,
return a server error, reject the request, or return a syntactically malformed
or schema-unexpected body. Its JSON schema is owned by NASA and can change
independently of this project. Its data is time-dependent: close-approach
records describe specific instants and windows, and the service's contents
change as new observations arrive.

The central architectural question is therefore not "how do we call NASA?" It
is: **how does an unreliable, externally-owned data representation enter a
deterministic simulation system without contaminating the core with HTTP and
API concerns, and without making the application dependent on network
availability?**

This ADR defines that boundary. It does **not** define the conversion from
orbital elements to Cartesian state vectors: that is deterministic,
provider-independent astrodynamics mathematics belonging to `simulator-core`,
and is the subject of ADR-008. This ADR concerns acquisition, trust, caching,
provenance and normalisation only.

## Decision Drivers

- Keep `simulator-core` free of HTTP, JSON, API keys and NASA-specific types,
  as it is free of JavaFX.
- Make every layer testable without a live network.
- Distinguish external failures (which may fall back to cache) from internal
  processing defects (which must surface), by cause rather than by stage.
- Surface data provenance honestly, since whether data is live or cached, and
  how old it is, is educationally and scientifically material.
- Never present cached data as satisfying a request it does not cover.
- Preserve the dependency direction: data acquisition produces values; it does
  not drive the engine.
- Do not require a NASA API key to develop or to run ordinary tests.

## Decision

### 1. A new module, `simulator-data`, owning its acquisition types

`simulator-data` encapsulates all NASA NeoWs communication and all external
data representation. It is depended upon by `simulator-app`, never depends on
`simulator-learning`, and the engine never depends on it.

```
simulator-core     (deterministic domain + engine + ADR-008 orbital math)
     ↑
simulator-data     (external acquisition, this ADR)
     ↑
simulator-app      (decides whether to build a simulation from acquired data)
```

`simulator-data` may depend on `simulator-core` for provider-independent
astrodynamics types or capabilities required by downstream mapping, including
those introduced by ADR-008. Its **acquisition result types**
(`NeoDataResult`, `NeoObjectData`, provenance metadata, `DataSource`) are
owned by `simulator-data`, not by the core. They are provider-neutral, but
they are data-acquisition domain types and do not belong in the simulation
core.

A consequence worth stating: `simulator-data` need not invoke the ADR-008
orbital converter at all. A clean flow keeps acquisition and conversion apart:

```
NASA → simulator-data → NeoObjectData → simulator-app
                                            → core orbital conversion
                                                → BodySnapshot → SimulationEngine
```

This keeps §7 ("`simulator-data` produces values; it never drives the engine")
exact: the module emits provider-neutral data, and the application composes it
with core conversion into simulation input.

### 2. NASA sits behind a provider interface

The rest of the application obtains NEO data through a provider abstraction
that knows nothing of HTTP, endpoints, JSON field names, DTOs or API keys:

```java
public interface NeoDataProvider {
    NeoDataResult findCloseApproaches(/* query parameters */);
}
```

The concrete NASA implementation is one strategy behind this interface, in the
same spirit as `AccelerationModel` and `Integrator`. A future provider — a
different service, or a purely local dataset — would be an alternative
implementation, and the offline fallback (section 6) is itself expressible
this way.

### 3. HTTP and JSON DTOs are private to the NASA adapter

NASA-specific data transfer objects — the classes mirroring NeoWs JSON — are
private to the NASA adapter and never cross the module boundary. The
application never deserialises a network response directly into
`CelestialBody`, `BodySnapshot` or any core type.

The reason is trust and ownership: NeoWs's schema is controlled by NASA and
can change without notice, whereas the simulation model is controlled by this
project. Binding them directly would make an external schema change a
compile-or-runtime break in the core model. Retrieved data is instead
validated and normalised into application-owned, provider-neutral
representations (for example a `NeoObjectData` record) before anything
downstream sees it.

### 4. Transport, parsing and normalisation are separate, injectable layers

Acquisition is decomposed into independently testable layers:

```
HTTP transport      — send request, return raw response (status + body)
      ↓
JSON parsing        — raw body → NASA DTOs
      ↓
normalisation       — DTOs → provider-neutral NeoObjectData, with validation
      ↓
provider result     — NeoDataResult (data + provenance)
```

The HTTP transport is injectable: the NASA provider does not bury a concrete
`HttpClient.send(...)` inside logic that also parses and validates. A small
transport abstraction (or, at minimum, an injected HTTP collaborator) lets
tests supply 200, 400, 401, 403, 429, 500, timeout and malformed responses
without a network. Parsing and normalisation operate on strings and resources,
so a malformed response is exercised with a fixture, not a live request.

The class count is kept minimal: these are the *layers* the design guarantees,
not a mandated set of classes. A distinct parser or normaliser class is
introduced when its responsibility genuinely separates from the provider, in
the same way `IntegratorValidation` was extracted only once duplication was
real — not posited in advance.

### 5. Failure taxonomy: classify by cause, not by processing stage

Fallback to cached data is permitted for failures attributable to the external
service or its representation, and is never permitted for defects in this
project's own code. The dividing line is **the cause of the failure, not which
processing stage produced the exception.** Three categories:

**(a) Transport / service failures — fallback-eligible.**
- network unreachable;
- request timeout;
- server/service error (5xx);
- rate limiting (429).

**(b) External representation / schema / data failures — fallback-eligible.**
- a syntactically malformed body (truncated or invalid JSON);
- a body that does not match the expected NeoWs structure at all (for example
  an HTML error page returned with status 200);
- a structurally-recognisable body in which a **required field is missing,
  renamed, null, or carries an incompatible shape or value** following an
  upstream schema or data-quality change.

Category (b) is the correction that matters most here. A response can be valid
JSON with the expected top level and still fail normalisation because NASA
changed a field. The normaliser rejecting it is *correct behaviour*, not a
bug: the failure originates in the external data, so it is an acquisition
failure and is fallback-eligible. Anchoring the boundary at "successful
structural recognition" would misclassify this real external failure as an
internal defect.

**(c) Internal programming defects — must surface, never fall back.**
- an unexpected `NullPointerException`, a broken invariant, an assertion
  failure, or an algorithmic error in this project's own parsing,
  normalisation or mapping logic.

Masking category (c) behind a cache fallback would hide exactly the kind of
error testing exists to catch. "All parsing errors fall back" conceals our
defects; "no parsing errors fall back" makes offline mode fragile against NASA
changing its output. Classifying by cause draws the line correctly between the
two.

Enforcing this distinction in practice is an implementation responsibility:
the adapter assigns failures to dedicated categories (for example distinct
exception or result types) so that fallback eligibility follows from the
category rather than from where the failure surfaced. Those categories are not
designed in this ADR.

### 6. HTTP status codes are classified by cause, not by a blanket rule

Non-2xx statuses are not uniformly treated as fallback conditions.

- **429 (rate limiting)** — an external acquisition failure; fallback-eligible
  (category (a)).
- **5xx (server error)** — an external service failure; fallback-eligible
  (category (a)).
- **401 / 403 (authentication / authorisation)** — caused by a missing or
  invalid API key, i.e. a configuration problem in *this* deployment; these
  **surface**, because silently falling back would hide the misconfiguration
  the operator needs to correct.
- **400 (bad request)** — indicates that the request sent by this application
  is invalid. It **surfaces** rather than falling back. Depending on cause it
  may represent invalid caller input or configuration, or a defect in request
  construction; in neither case is it external service unavailability, so it is
  never answered from cache.
- **Any other status** is classified explicitly by the adapter according to
  its cause. There is no "non-2xx means cached" default.

### 7. Offline fallback is explicit, query-compatible, and shares the live path

A bundled or persisted cached dataset provides deterministic offline fallback,
governed by three rules.

**Provenance is explicit, never silent.** An acquisition result carries its
source and the age of the underlying data, so no consumer receives data
without knowing where it came from and when it was obtained:

```java
public record NeoDataResult(
        List<NeoObjectData> objects,
        DataSource source,          // LIVE or CACHED
        Instant retrievedAt) { }
```

`retrievedAt` denotes **the time at which the represented dataset was obtained
from its authoritative source.** For live data this is the time of the
successful NASA call. For cached data it is the original cache-capture
timestamp — **not** the time at which the cache is subsequently loaded from
disk. Otherwise a six-month-old cache read today would report `retrievedAt =
now()` and appear falsely fresh, defeating the purpose of surfacing
provenance. (Implementation may prefer the name `capturedAt` for clarity; the
requirement is the semantics, not the name.)

**Cached data may satisfy a request only if it covers that request.** A cached
response captured for one time window or query must not be returned for a
different request merely because the network failed; doing so would present
scientifically misleading data while still honestly reporting `CACHED`. Cache
metadata therefore records sufficient **query context** — in addition to the
capture time — to establish that the cached dataset's scope covers the request
being served. If it does not cover the request, the cache does not satisfy it,
and the acquisition failure is reported as such rather than answered with
unrelated stale data. For the scope of this project this may be kept simple —
a bundled snapshot corresponding to a predefined NEO scenario, whose scope
trivially covers the scenario's own request — but the architectural contract
prohibits returning unrelated cached data regardless of how the cache is
realised.

**Cached data flows through the same parser as live data.** There is one
parsing/normalisation path, fed by either an HTTP body or a cached JSON
resource:

```
        ┌── live HTTP JSON
common ─┤
parser  └── cached JSON resource
```

A single path avoids two divergent implementations, and makes the bundled
snapshot serviceable as a deterministic test fixture for the very same code
that handles live data.

### 8. `simulator-data` produces values; it never drives the engine

Acquisition yields data. Whether to build, reset or populate a
`SimulationEngine` from that data is the application layer's decision, not the
data module's. `simulator-data` has no reference to the engine and issues no
engine commands. This preserves the dependency direction maintained throughout
the project: values flow up to the application, which composes them (via the
core orbital conversion of ADR-008) into simulation input.

### 9. The API key is configuration, and its absence is a supported state

The NASA API key is supplied through environment or configuration, never
committed to source control, and is unnecessary for ordinary unit tests. It is
required only for the application's live-data mode and for a single explicitly
opt-in live integration test.

This yields four honestly-distinguished runtime states:

```
no key / offline                          → cached dataset, provenance CACHED
                                            (only if the cache covers the request, §7;
                                             otherwise acquisition failure surfaces)
key present + NASA reachable              → provenance LIVE
external acquisition failure (§5 a/b)     → cached dataset, provenance CACHED
                                            (only if the cache covers the request, §7;
                                             otherwise acquisition failure surfaces)
internal defect / misconfiguration (§5c, §6) → failure surfaces; no cache masking
```

Development proceeds cache-first: the live path is structurally complete but
not exercised by routine tests. The architecture behaves correctly whether or
not a key exists.

### 10. Ordinary tests are deterministic and offline; live tests are opt-in

No routine Gradle test depends on the network. Parsing, normalisation, failure
classification and fallback are all tested against bundled JSON fixtures and an
injected transport that emulates each response class. A genuine NASA
integration test exists separately, is explicitly opt-in (a system property or
tag), requires a configured key, and is never part of the default test run.
This is treated as an architectural rule, not a convenience: the determinism
that `simulator-core` guarantees by construction is preserved in
`simulator-data` by isolating the one non-deterministic collaborator behind an
injection seam.

## Consequences

### Positive

- The core remains free of HTTP, JSON and API concerns; an external schema
  change cannot reach the simulation model without passing through
  normalisation, and cannot masquerade as an internal defect.
- Every failure class — 200-with-wrong-body, 400, 401, 403, 429, 5xx, timeout,
  malformed body, missing field — is testable offline with a fixture and an
  injected transport, and each maps to a defined fallback-or-surface outcome.
- Cause-based classification means offline fallback answers external failure
  while configuration and programming defects surface, and neither is hidden by
  the other.
- Provenance is a first-class part of every result, with `retrievedAt` fixed
  to source-capture time, so cached data cannot appear falsely fresh.
- Cached data cannot silently answer a request it does not cover, so `CACHED`
  never means "scientifically unrelated."
- A single parsing path serves live and cached data, reducing code and making
  the bundled snapshot double as a test fixture.
- Acquisition result types are owned by `simulator-data`, keeping the core free
  of acquisition concepts.
- Development is unblocked by the absence of an API key.
- The dependency direction is preserved; the engine remains ignorant of data
  acquisition, and conversion is composed above the data module.

### Negative / Accepted Trade-offs

- A provider interface, an injectable transport and provider-neutral types are
  more indirection than a direct `HttpClient`-to-model call. Accepted: the
  indirection is exactly what buys offline testability and schema isolation,
  and it mirrors the boundary discipline used for `AccelerationModel` and
  `Integrator`.
- Normalising DTOs into provider-neutral types duplicates shape between the
  NASA DTO and `NeoObjectData`. Accepted: it is the seam that stops an external
  schema from owning the internal model.
- Classifying failures by cause — distinguishing a schema/data failure from a
  mapper defect, and 401/403/400 from 429/5xx — requires deliberate categories
  rather than a blanket "non-2xx / any-parse-error falls back" rule. Accepted:
  the distinction is the point, and getting it wrong in either direction
  (hiding bugs and misconfiguration, or a fragile offline mode) is worse than
  the care required.
- Cache metadata must record query context and capture time, not merely a blob
  of JSON. Accepted: without it, `CACHED` cannot be trusted to be relevant or
  correctly dated. For this project the metadata can be minimal.
- A bundled cached dataset must be maintained and its capture date recorded.
  Accepted: it is required for offline operation regardless, and its staleness
  is surfaced rather than hidden.

## Guiding Principle

An unreliable external representation enters the system through one guarded
boundary: transport, parsing and normalisation are separable and injectable;
external DTOs never cross into the model; fallback answers external failure —
transport, service, or the external data itself — but never hides our own
defects or misconfiguration; cached data is used only when it covers the
request and is always dated to its capture, not its loading; every result
declares whether it is live or cached; and acquired data is a value the
application composes into the engine, never a command the data module issues to
it.
