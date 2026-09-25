# Apophis / CNEOS Validation — Evaluation Evidence

This directory preserves numerical-convergence and external-validation evidence
for the simulator's propagation of the 2029 close approach of asteroid
99942 Apophis.

## Purpose

The evaluation addresses two separate questions:

1. whether the simulated encounter measurement converges as the numerical
   integration timestep is reduced; and
2. how the resulting simulated encounter compares with an independently
   preserved NASA/JPL close-approach reference.

These questions are evaluated separately so that numerical convergence is not
confused with agreement with the external reference.

## Numerical convergence

`convergence-results.csv` contains the output of
`ApophisConvergenceExperiment`.

The experiment evaluates three candidate Horizons initial epochs and four
Velocity Verlet timesteps:

- 3600 seconds;
- 1800 seconds;
- 900 seconds;
- 450 seconds.

The experiment measures the sampled minimum Earth-Apophis separation,
the epoch at which that sample occurs, relative speed, and completed
integration steps.

This convergence experiment does not use the CNEOS reference to select an
outcome or apply an external pass/fail criterion.

## External CNEOS validation

`cneos-validation-results.csv` contains the output of
`ApophisExternalValidationExperiment`.

The external comparison uses the one-day-before initial state and evaluates
the following timestep-refinement sequence:

- 450 seconds;
- 225 seconds;
- 112.5 seconds;
- 56.25 seconds.

For each run, the simulator result is compared with a preserved NASA/JPL SBDB
Close Approach Data API reference.

The reported signed discrepancies are defined as:

- distance difference = simulated sampled minimum distance minus JPL nominal
  close-approach distance;
- epoch difference = simulated sampled-minimum epoch minus JPL close-approach
  epoch;
- relative-speed difference = simulated relative speed minus JPL published
  relative speed.

No pass/fail threshold is applied by this experiment. The discrepancies are
retained as quantitative evaluation evidence.

## Phase-offset check

A separate run uses a 51.2-second timestep selected so that the published
CNEOS encounter epoch lies halfway between adjacent committed simulation
samples.

This check reduces the risk of interpreting agreement obtained when a
committed numerical state happens to coincide exactly with the external
reference epoch.

It is recorded separately from the ordinary timestep-refinement sequence.

## Sampling limitation

The simulator reports the smallest Earth-Apophis separation observed at the
initial state or at a committed integration-step boundary.

The result is therefore a sampled minimum rather than an interpolated
continuous-time closest approach.

This distinction must be retained when interpreting the external comparison.

## External reference

The preserved reference fixture records:

- source: NASA/JPL SBDB Close Approach Data API;
- API version: 1.5;
- designation: 99942 Apophis;
- orbit solution identifier: 206;
- approach body: Earth;
- time scale: TDB;
- nominal close-approach distance and published 3-sigma distance interval;
- relative velocity;
- close-approach epoch and time uncertainty;
- the API query used to obtain the reference.

The fixture is version-controlled under the evaluation module's test resources
so that the evaluation does not depend on a future live API response.

## Reproduction

From the project root on Windows, run:

    .\gradlew.bat :simulator-evaluation:test --tests "*ApophisConvergenceExperiment" --info

and:

    .\gradlew.bat :simulator-evaluation:test --tests "*ApophisExternalValidationExperiment" --info

The CSV files in this directory preserve the outputs produced by those Java
experiments.