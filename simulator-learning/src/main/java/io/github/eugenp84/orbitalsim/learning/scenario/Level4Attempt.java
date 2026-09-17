package io.github.eugenp84.orbitalsim.learning.scenario;

import java.util.Objects;

/**
 * A ready-to-run Level 4 attempt for the canonical Apophis 2029 close-approach experiment.
 *
 * @param session       scenario session driving the experiment
 * @param progressSource read-only sampled encounter measurements
 * @param sunId         Sun body id
 * @param earthId       Earth body id
 * @param apophisId     Apophis body id
 * @param initialJdTdb  authoritative initial epoch in TDB
 *
 * @author Eugeniu PLATITA
 */
public record Level4Attempt(
        ScenarioSession session,
        Level4ProgressSource progressSource,
        String sunId,
        String earthId,
        String apophisId,
        double initialJdTdb)
{
    /** Validates the assembled attempt. */
    public Level4Attempt
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(progressSource, "progressSource must not be null");
        requireIdentifier(apophisId, "apophisId");
        requireIdentifier(earthId, "earthId");
        requireIdentifier(sunId, "sunId");
        if (sunId.equals(earthId) || sunId.equals(apophisId) || earthId.equals(apophisId))
        {
            throw new IllegalArgumentException("sunId, earthId and apophisId must be distinct");
        }
        if (!Double.isFinite(initialJdTdb) || initialJdTdb <= 0.0)
        {
            throw new IllegalArgumentException("initialJdTdb must be positive and finite");
        }
    }

    private static void requireIdentifier(final String value, final String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
