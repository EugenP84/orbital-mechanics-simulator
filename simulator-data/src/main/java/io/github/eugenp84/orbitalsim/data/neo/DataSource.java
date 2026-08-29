package io.github.eugenp84.orbitalsim.data.neo;

/**
 * The provenance of a {@link NeoDataResult}: whether its data was obtained
 * from the live authoritative service or served from a cached dataset
 * (ADR-007 §7).
 * <p>
 * Provenance is always explicit. No consumer receives NEO data without knowing
 * whether it is live or cached, because cached close-approach data may be stale
 * and the distinction is scientifically and educationally material.
 */
public enum DataSource
{

    /** Obtained from a successful live call to the authoritative service. */
    LIVE,

    /** Served from a cached dataset, typically as an offline fallback. */
    CACHED
}