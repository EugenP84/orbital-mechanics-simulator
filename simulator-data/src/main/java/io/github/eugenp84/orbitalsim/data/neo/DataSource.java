package io.github.eugenp84.orbitalsim.data.neo;

/**
 * The provenance of a {@link NeoDataResult}: whether its data was obtained from the live authoritative service or served from a cached dataset.
 * <p>
 * @author Eugeniu PLATITA
 */
public enum DataSource
{

    /** Obtained from a successful live call to the authoritative service. */
    LIVE,

    /** Served from a cached dataset, typically as an offline fallback. */
    CACHED
}