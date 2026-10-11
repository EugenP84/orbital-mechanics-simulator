# ADR-011: Level 4 Real NEO Close-Approach Experiment and Validation

**Status:** Accepted  
**Date:** 17.09.2026    
**Modules:** `simulator-data`, `simulator-learning`, `simulator-app`, `simulator-evaluation`; existing facilities in `simulator-core` reused unchanged unless calibration demonstrates a genuine requirement  

---

## Context

The project proposal requires a progressive NEO learning scenario using real NASA data and requires the implemented numerical model to be evaluated against published NASA CNEOS close-approach information.

The existing system already provides two important foundations.

First, `simulator-data` implements provider-neutral NeoWs close-approach acquisition with explicit live/cached provenance and offline fallback. Its `NeoDataProvider` contract is specifically concerned with retrieving close approaches over a date range. `NeoObjectData` deliberately represents only the NEO information needed downstream and does not represent an initial Cartesian simulation state.

Second, `simulator-core` already supports asymmetric restricted-body interactions. `GravitationalBodyConfig.contributesGravity` is scenario policy: a non-contributing body still receives acceleration from every contributing source but does not exert gravity on the other bodies. `NBodyAccelerationCalculator` applies exactly this rule.

The L4 NEO scenario introduces a different problem from NeoWs discovery. A physically meaningful numerical propagation requires position and velocity initial conditions for the relevant bodies at a common epoch and in a common coordinate system. Close-approach metadata alone cannot supply those initial conditions.

NASA/JPL Horizons is an appropriate authoritative source for Cartesian ephemeris vectors and provides explicit control over quantities including coordinate centre, reference plane/system, output units and time representation. It is a separate JPL service from NeoWs.

The canonical experiment will use asteroid **99942 Apophis** and its documented close approach to Earth on **13 April 2029**. JPL's SBDB Close-Approach Data API independently publishes the encounter epoch, nominal distance and relative velocity and therefore provides suitable external reference quantities for Objective 6.

L4 must remain an educational simulator rather than attempting to reproduce the full JPL orbit-determination and ephemeris system. The design must therefore make the simplified physical model explicit, preserve reproducibility, prevent coordinate-system mistakes from silently contaminating the experiment, and keep external-service concerns outside the deterministic simulation core.

---

## Decision Drivers

- Satisfy the approved project's real-NEO scenario and CNEOS-validation objectives.
- Use scientifically meaningful initial conditions rather than deriving a trajectory from NeoWs close-approach metadata.
- Guarantee that Earth and Apophis initial states share the same epoch, time scale, origin, coordinate convention and units.
- Make the canonical assessment scenario deterministic and runnable without network access.
- Avoid making Gradle builds or ordinary tests depend on an external scientific service.
- Preserve the existing NeoWs acquisition boundary rather than expanding an interface beyond its current responsibility.
- Reuse the existing restricted-body gravitational model rather than introduce parallel NEO-specific physics.
- Keep NASA/JPL representations, provenance and acquisition concerns outside `simulator-core` and `simulator-learning`.
- Independently compute the simulated close approach before comparing it with the published CNEOS/JPL result.
- Select numerical timestep, observation horizon and any closest-approach refinement from empirical convergence evidence rather than arbitrary constants.
- Avoid functionality not required by the approved proposal.

---

# Decision

## 1. L4 is a real-data, observation-based close-approach experiment

Level 4 will represent a historical/future-prediction case study of the **99942 Apophis Earth close approach of 13 April 2029**.

Its educational question is:

> How closely can the simulator's deliberately simplified Newtonian model reproduce the characteristics of a documented real near-Earth-object close approach?

L4 is therefore an **observation scenario**, not a trajectory-design challenge.

The learner does not manipulate Apophis until a desired outcome is achieved and does not "pass" by avoiding Earth. The scenario propagates a defined real-world initial condition and exposes the resulting encounter measurements.

Normal completion means that the configured observation horizon has been reached.

There is no learner-facing hard failure condition. Numerical breakdown or another inability to obtain a trustworthy simulation result remains a technical failure under the existing `ScenarioSession` semantics.


---

## 2. The physical model is Sun–Earth–restricted-Apophis

The first L4 implementation contains three bodies:

1. Sun;
2. Earth;
3. 99942 Apophis.

The Sun and Earth are gravitational sources. Apophis is a restricted test body: it is accelerated by the contributing bodies but does not gravitationally perturb them.

Accordingly, the model is approximately:

\[
\ddot{\mathbf r}_E =
-GM_S
\frac{\mathbf r_E-\mathbf r_S}
{\|\mathbf r_E-\mathbf r_S\|^3},
\]

while Apophis experiences

\[
\ddot{\mathbf r}_A =
-GM_S
\frac{\mathbf r_A-\mathbf r_S}
{\|\mathbf r_A-\mathbf r_S\|^3}
-
GM_E
\frac{\mathbf r_A-\mathbf r_E}
{\|\mathbf r_A-\mathbf r_E\|^3}.
\]

The implementation will express this through the existing `NBodyAccelerationCalculator` configuration:

```text id="0dx3jf"
Sun      → contributesGravity = true
Earth    → contributesGravity = true
Apophis  → contributesGravity = false
```

The asteroid may retain an assigned positive representative physical mass because the existing restricted-body abstraction requires a valid mass while suppressing its influence on the primaries.

No NEO-specific gravitational algorithm is introduced.

This model is deliberately simplified. It does not claim to reproduce JPL's high-precision propagation model, which may account for additional Solar-System bodies and higher-fidelity dynamical effects. Differences from the published close approach are therefore measurements to be investigated rather than automatically treated as software defects.

Total mechanical-energy conservation of the represented three-body system will **not** be used to evaluate L4 numerical accuracy. Because the NEO receives gravitational acceleration without reciprocal back-reaction on the primaries, the represented system is not a closed fully coupled Newtonian system for this purpose. L4 uses trajectory-based validation instead, as already established by ADR-004.

---

## 3. Production L4 initial conditions use authoritative Cartesian state vectors

The canonical L4 simulation will be initialised from authoritative Cartesian position and velocity vectors rather than requiring an orbital-elements-to-state conversion.

Earth and Apophis states must be obtained at:

- exactly the same epoch;
- the same time scale;
- the same coordinate centre/origin;
- the same reference system;
- the same reference plane;
- compatible units;
- the same vector-correction convention.

JPL Horizons will be used as the authoritative acquisition source.

The exact Horizons query configuration will be recorded with the fixture. No undocumented Horizons defaults will be relied upon for scientifically material parameters.

A single consistent Cartesian orientation will be selected for both bodies. The physical encounter calculation does not depend on an arbitrary rigid rotation of the complete Newtonian system, but mixing reference planes or coordinate systems between bodies would invalidate relative geometry. Therefore consistency is an explicit fixture invariant.

Source vectors will be normalised into the simulator's canonical SI representation:

- position → metres;
- velocity → metres per second.

The resulting values can then be represented directly by the existing immutable `StateVector`.

---

## 4. A verified version-controlled fixture is the primary L4 initial-condition source

Live Horizons retrieval is **not** part of the L4 runtime critical path.

The canonical Earth–Apophis state vectors will be obtained in one controlled acquisition from JPL Horizons, verified, and preserved in a version-controlled fixture.

The fixture must record sufficient provenance to reproduce and audit the acquisition, including at least:

- authoritative source: JPL Horizons;
- target identities;
- exact epoch;
- time scale;
- coordinate centre/origin;
- reference system;
- reference plane;
- vector-correction convention;
- original source units;
- the Cartesian position and velocity components;
- the Horizons query parameters or equivalent reproducible query specification.

Earth and Apophis must be represented as members of the same experiment dataset rather than as unrelated independently described fixtures. This makes their common coordinate and epoch assumptions explicit.

The fixture loader will validate its structural and numerical invariants before constructing simulation input.

Basic physical sanity checks will additionally be tested, including plausible heliocentric Earth distance and speed. Such magnitude checks are safeguards only; they are not considered proof of coordinate-frame consistency because a rotation can preserve both distance and speed.

The provenance metadata and controlled acquisition procedure are the primary safeguards against silent frame/origin mismatch.

---

## 5. Horizons is not added to `NeoDataProvider`

`NeoDataProvider` remains responsible for provider-neutral date-range close-approach acquisition and its live/cache policy.

Horizons initial-state acquisition is a distinct responsibility and will not be added to that interface.

The canonical fixture is deterministic project data rather than a runtime external-service strategy. Therefore no speculative `HorizonsProvider`, `EphemerisProvider` or generic live ephemeris abstraction will be introduced merely for L4.

A dedicated provider abstraction may be introduced later only if a demonstrated second runtime source or use case creates a genuine interchangeable-provider requirement.

The fixture and its loader belong to `simulator-data`, because they represent externally sourced scientific data, provenance and normalisation. NASA/JPL-specific metadata does not enter `simulator-core`.

The intended dependency flow is:

```text id="kerrml"
JPL Horizons
     │
     │ controlled acquisition
     ▼
version-controlled L4 fixture
     │
     ▼
simulator-data
validation + SI normalisation
     │
     ▼
provider-neutral simulation inputs
     │
     ▼
simulator-app composition
     │
     ├── simulator-learning L4 semantics
     └── simulator-core simulation
```

The exact fixture-loader class names and record shapes will emerge during implementation rather than being fixed speculatively in this ADR.

---

## 6. NeoWs remains the project's live NEO acquisition capability

The Horizons fixture does not replace the existing NeoWs implementation.

`NeoDataProvider` already provides real NEO close-approach acquisition with explicit provenance and a defined offline fallback policy. The existing result model records whether data was obtained live or from cache, and cached data retains its original source-capture time.

That functionality remains the implementation of the proposal's NeoWs/live-data and offline-fallback requirement.

L4 therefore deliberately separates three roles:

```text id="61k4yk"
NeoWs
    → real NEO discovery / close-approach metadata
    → existing live + offline fallback capability

Horizons
    → authoritative Cartesian initial conditions
    → controlled, versioned fixture for deterministic propagation

JPL/CNEOS close-approach data
    → independent external validation target
```

These roles must not be collapsed into a single data abstraction merely because all originate from NASA/JPL services.

---

## 7. L4 reuses `ScenarioSession` and a stateful completion/progress policy

The existing learning architecture remains applicable.

`ScenarioSession` continues to own lifecycle, advancement and bounded observation cadence. It evaluates a scenario-specific completion policy once per committed observation and already separates policy completion from numerical breakdown.

L4 will therefore use the existing composition model rather than introduce a new scenario execution mechanism.

A Level-4-specific policy will:

1. identify Earth and Apophis by stable body IDs;
2. compute their relative state at each learning observation;
3. accumulate the closest approach observed so far;
4. retain the simulated time associated with that minimum;
5. retain the Earth-relative speed associated with that minimum;
6. return `COMPLETE` when the calibrated observation horizon is reached;
7. otherwise return `CONTINUE`.

The policy will expose its accumulated read-only measurements through a narrow L4 progress capability, following the same separation used by `Level3ProgressSource`.

The exact public type names will be chosen during implementation, but the presentation layer must not receive mutable access to the completion policy.

---

## 8. L4 measurements are Earth-relative

At each observation, the Earth–Apophis separation is

\[
d(t)=
\|\mathbf r_A(t)-\mathbf r_E(t)\|.
\]

The accumulated sampled minimum is

\[
d_{\min} =
\min d(t).
\]

The relative velocity is

\[
\mathbf v_{\mathrm{rel}} =
\mathbf v_A-\mathbf v_E,
\]

with magnitude

\[
v_{\mathrm{rel}} =
\|\mathbf v_A-\mathbf v_E\|.
\]

When a new minimum separation is observed, the policy records:

- minimum Earth–Apophis distance;
- elapsed simulated time at that minimum;
- relative speed at that minimum.

The current Earth–Apophis distance may additionally be exposed for live presentation.

These quantities are independent of the absolute translation of the selected common coordinate system because they are calculated from relative position and velocity.

---

## 9. Sampled minimum and continuous closest approach are distinguished

A discrete numerical simulation does not in general evaluate the exact continuous-time minimum separation.

Therefore the initial L4 implementation will explicitly treat its policy result as the **minimum observed at the configured observation cadence**, not automatically as the mathematically exact closest approach.

Before the production numerical configuration is frozen, timestep/cadence convergence experiments will be performed using successively finer steps, for example:

\[
\Delta t,\quad \Delta t/2,\quad \Delta t/4.
\]

The experiment will examine convergence of at least:

- minimum Earth–Apophis distance;
- simulated time of minimum separation;
- relative speed at minimum separation.

If the discrete result converges sufficiently for the project's educational and evaluation objectives, no additional interpolation mechanism will be introduced.

If convergence shows that sampling error materially limits the CNEOS comparison, a local closest-approach refinement technique may be introduced and documented separately.

The need for such refinement is therefore evidence-driven rather than assumed.

---

## 10. Initial epoch, timestep, observation cadence and horizon are calibration decisions

This ADR intentionally does not prescribe the final:

- initial epoch before the encounter;
- numerical timestep;
- learning observation interval;
- simulation horizon.

The initial state must precede the encounter sufficiently to constitute genuine numerical propagation rather than starting at the published answer, while avoiding an unnecessarily long interval over which omissions from the simplified force model accumulate.

Candidate configurations will therefore be evaluated empirically.

Velocity Verlet remains the default candidate integrator because assessed scenarios already use it and the project has an established implementation and validation basis for it. The production L4 integrator and timestep will be frozen only after the relevant convergence experiment.

The final selected parameters and empirical justification will be recorded in the implementation documentation and evaluation evidence rather than asserted in advance by this ADR.

---

## 11. CNEOS/JPL validation is independent of scenario progression

The authoritative close-approach result is an evaluation reference, not an input to the simulated trajectory or its accumulated minimum.

Published JPL close-approach data provides, among other fields:

- close-approach epoch;
- nominal approach distance;
- relative velocity at close approach.

For the canonical Apophis experiment, the simulator will independently produce corresponding quantities and evaluation will calculate discrepancies such as:

\[
\Delta d =
|d_{\mathrm{sim}}-d_{\mathrm{JPL}}|,
\]

\[
\Delta t =
|t_{\mathrm{sim}}-t_{\mathrm{JPL}}|,
\]

and, where directly comparable,

\[
\Delta v =
|v_{\mathrm{sim}}-v_{\mathrm{JPL}}|.
\]

Relative error will additionally be reported where it is meaningful.

The published CNEOS/JPL result must not be used:

- to modify the propagated trajectory;
- to stop the simulation at the known closest-approach instant;
- to select the sampled minimum;
- as a completion criterion;
- or to tune constants until the simulator reproduces the reference value.

This preserves the independence of the validation.

Observed discrepancies will be interpreted in light of numerical discretisation, initial-condition epoch, observation cadence and the deliberately simplified physical model.

---

## 12. JavaFX is implemented only after the headless experiment is validated

The L4 physical experiment, progress measurement and convergence behaviour will be established before implementing the final screen.

The application may reuse the existing multi-body rendering, body highlighting and multi-trail capability developed for L3.

A likely presentation is:

```text id="k8tai5"
Sun      → star
Earth    → planet / reference body
Apophis  → highlighted asteroid

Apophis trajectory → primary encounter trail
Earth trajectory   → optional reference trail
```

However, L3's fixed 3 AU viewport will not automatically be reused.

The NEO experiment contains both heliocentric orbital-scale motion and a much smaller Earth-encounter scale. The final L4 view will therefore be selected after examining the validated trajectory.

No nonlinear scaling, automatic camera system, multiple synchronized views or other visualization abstraction will be introduced unless the validated L4 trajectory demonstrates that such functionality is necessary for the educational objective.

---

## 13. Testing and calibration strategy

L4 implementation will proceed from deterministic data and headless physics outward.

### Fixture/data tests

Tests will verify:

- required provenance is present;
- Earth and Apophis share the required epoch and coordinate metadata;
- numeric vector components are finite;
- source-unit conversion to SI is exact within the representation used;
- physically implausible or structurally inconsistent fixtures are rejected;
- Earth heliocentric distance and speed are within broad physically plausible bounds;
- the fixture can be loaded without network access.

### Scenario/policy tests

Tests will verify:

- Sun, Earth and Apophis have distinct stable IDs;
- Sun and Earth contribute gravity;
- Apophis is non-contributing;
- the initial policy measurement is meaningful;
- the running minimum never increases;
- the recorded time and relative speed correspond to the snapshot at which a new sampled minimum is accepted;
- completion occurs at the configured observation horizon;
- numerical breakdown remains handled by `ScenarioSession`;
- fresh attempts do not share accumulated progress state.

### Numerical calibration

A separate development/evaluation harness will investigate:

- candidate initial epochs;
- timestep convergence;
- observation-cadence effects;
- encounter-distance convergence;
- encounter-time convergence;
- relative-speed convergence.

Calibration code that has no production responsibility may be removed after its methodology and results have been preserved as project evidence, following the approach used for L3.

### External validation

The selected production configuration will be run independently and compared with the authoritative JPL/CNEOS close-approach reference.

The comparison will report discrepancies rather than merely pass/fail against a hidden tolerance.

---

## 14. Implementation sequence

L4 will be implemented in the following order:

1. Define and record the exact Horizons acquisition specification.
2. Obtain Earth and Apophis vectors through a controlled Horizons query.
3. Preserve the raw/reproducible query information and create the version-controlled L4 fixture.
4. Implement fixture parsing, validation and SI normalisation in `simulator-data`.
5. Build a headless Sun–Earth–restricted-Apophis experiment using the existing core physics.
6. Implement the L4 completion/progress policy and attempt composition.
7. Implement tests for data invariants, restricted interaction configuration and accumulated close-approach measurements.
8. Run initial-epoch, timestep and observation-cadence calibration/convergence experiments.
9. Compare the resulting encounter independently with JPL/CNEOS data.
10. Freeze the production numerical parameters based on the evidence.
11. Implement the application controller.
12. Implement and validate the JavaFX L4 screen.
13. Run regression tests across the existing scenarios and modules.
14. Preserve calibration and validation evidence for the final project report.

---

# Alternatives Considered

## Alternative A — Live Horizons client as a required runtime provider

**Rejected for the canonical L4 experiment.**

Horizons can be accessed programmatically, but requiring live acquisition would add another HTTP integration, parser, failure policy and external runtime dependency.

It would also make deterministic reproduction of the assessed experiment unnecessarily dependent on external availability and API stability.

A live Horizons client may be considered as a future enhancement, but it is not required by the project objectives and is not on the L4 critical path.

---

## Alternative B — Fetch Horizons during the Gradle build or tests

**Rejected.**

Compilation and ordinary automated testing must remain independent of an external scientific service.

External API availability, network state or a provider-side format change must not determine whether the project builds.

The canonical fixture is version controlled instead.

---

## Alternative C — Extend `NeoDataProvider` with ephemeris/state-vector retrieval

**Rejected.**

The existing interface has a coherent responsibility: provider-neutral close-approach retrieval over a date range, including live/cache provenance.

Ephemeris state acquisition is a different operation with different query semantics and scientific metadata.

Adding it to `NeoDataProvider` would weaken interface cohesion and conflate NeoWs discovery data with Horizons initial conditions.

---

## Alternative D — Introduce a generic `EphemerisProvider` immediately

**Deferred.**

Only one deterministic initial-condition source is required for the canonical L4 experiment.

An interchangeable provider abstraction would therefore currently have only one real implementation and no demonstrated variation point.

It will be introduced only if later requirements create a genuine second provider or runtime acquisition use case.

---

## Alternative E — Construct Apophis from Keplerian orbital elements

**Deferred.**

Converting osculating elements to Cartesian state is valid astrodynamics, but it is not necessary to satisfy the approved L4 or CNEOS-validation objectives.

Using authoritative Cartesian states removes orbital-element conversion error from the primary propagation experiment and reduces implementation scope.

Elements-to-state conversion remains a possible future enhancement.

---

## Alternative F — Initialise Earth from an idealised circular orbit

**Rejected.**

A simplified analytical Earth orbit combined with an authoritative Apophis state would introduce an unnecessary initial-condition discrepancy into an experiment intended to evaluate close-approach propagation.

Earth and Apophis will instead use mutually consistent authoritative states.

---

## Alternative G — Fully coupled Sun–Earth–Apophis dynamics

**Rejected for the production L4 model.**

Apophis' gravitational influence on the primaries is immaterial for the educational encounter experiment, while the existing restricted-body abstraction explicitly supports suppressing that back-reaction.

The restricted configuration also matches the trajectory-validation route already established by ADR-004.

---

## Alternative H — Full Solar-System propagation

**Rejected.**

Adding the Moon, other planets, major asteroids, relativistic corrections, non-gravitational effects or uncertainty propagation would move the project toward professional ephemeris reproduction.

Those capabilities are unnecessary for demonstrating the approved MSc objectives.

Their omission is an explicit model limitation to be discussed when interpreting the CNEOS discrepancy.

---

# Consequences

## Positive

- L4 is directly traceable to the approved NEO and CNEOS-validation objectives.
- The canonical experiment is deterministic and examiner-runnable without network access.
- Initial-condition provenance is explicit and auditable.
- Coordinate-frame/origin mismatch is treated as a scientific invariant rather than an incidental parsing detail.
- Existing NeoWs functionality remains cohesive and unchanged.
- Existing `StateVector`, `NBodyAccelerationCalculator`, restricted-body policy, `SimulationEngine` and `ScenarioSession` are reused.
- No new NEO-specific physics engine is required.
- Validation remains independent of the reference result.
- Numerical parameters are selected empirically rather than justified retrospectively.
- The difference between an educational physical model and a professional JPL ephemeris becomes useful critical-evaluation evidence.

## Negative / Accepted Trade-offs

- The canonical scenario does not demonstrate live Horizons integration.
- The fixture represents a controlled snapshot of authoritative data rather than automatically incorporating future orbit-solution updates.
- The restricted Sun–Earth–Apophis model omits real perturbations represented in higher-fidelity ephemerides.
- The initial sampled closest approach is cadence-dependent until convergence/refinement is established.
- The canonical experiment is intentionally specific to Apophis rather than providing a generic arbitrary-NEO propagation framework.

These are accepted because reproducibility, scientific auditability and completion of the approved project objectives are more important than breadth of external integration.

---

# Explicitly Out of Scope

Unless later empirical evidence demonstrates that one is necessary to satisfy an approved objective, L4 will not include:

- mandatory live Horizons acquisition;
- Horizons access during the Gradle build;
- a generic ephemeris-provider framework;
- arbitrary NEO propagation;
- full Solar-System propagation;
- Moon/planet perturbation parity with JPL;
- relativistic corrections;
- non-gravitational asteroid forces;
- covariance or uncertainty propagation;
- impact-probability calculation;
- orbital-elements-to-Cartesian conversion;
- L4 adaptive hints;
- CNEOS values as trajectory inputs or completion criteria;
- tuning the physical model merely to reproduce the published CNEOS answer;
- claims of JPL/Horizons-equivalent precision.

---

# Guiding Principle

Use authoritative, mutually consistent initial conditions; propagate them through the smallest physically defensible model already supported by the simulator; measure the encounter independently; and treat the difference from JPL/CNEOS as evidence to analyse rather than an answer to engineer toward.**
