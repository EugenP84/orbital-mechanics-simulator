package io.github.eugenp84.orbitalsim.data.neo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The result of a NEO data acquisition: the objects retrieved, together with explicit provenance (ADR-007 §7).
 * <p>
 *
 * @param objects     the retrieved objects, in provider order; never null, defensively copied, may be empty
 * @param source      whether the data is live or cached; never null
 * @param retrievedAt the source-capture time of the dataset (see above); never null
 *
 * @author Eugeniu PLATITA
 */
public record NeoDataResult(
        List<NeoObjectData> objects,
        DataSource source,
        Instant retrievedAt)
{

    public NeoDataResult
    {
        Objects.requireNonNull(objects, "objects must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(retrievedAt, "retrievedAt must not be null");

        objects = List.copyOf(objects);
    }

    /** Whether this result was served from a cached dataset. */
    public boolean isCached()
    {
        return source == DataSource.CACHED;
    }
}