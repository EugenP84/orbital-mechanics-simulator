package io.github.eugenp84.orbitalsim.data.neo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The result of a NEO data acquisition: the objects retrieved, together with
 * explicit provenance (ADR-007 §7).
 * <p>
 * No consumer receives NEO data without knowing where it came from and when
 * the underlying dataset was obtained. This is required for honest reporting
 * of live versus cached data and for the CNEOS validation of Objective 6.
 * <p>
 * {@code retrievedAt} denotes <strong>the time at which the represented
 * dataset was obtained from its authoritative source</strong> — for
 * {@link DataSource#LIVE} data, the time of the successful service call; for
 * {@link DataSource#CACHED} data, the original capture time of the cache,
 * <em>not</em> the time it was loaded from disk (ADR-007 §7). A six-month-old
 * cache therefore reports its true age rather than appearing freshly
 * retrieved.
 *
 * @param objects     the retrieved objects, in provider order; never null,
 *                    defensively copied, may be empty
 * @param source      whether the data is live or cached; never null
 * @param retrievedAt the source-capture time of the dataset (see above);
 *                    never null
 */
public record NeoDataResult(
        List<NeoObjectData> objects,
        DataSource source,
        Instant retrievedAt) {

    public NeoDataResult {
        Objects.requireNonNull(objects, "objects must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(retrievedAt, "retrievedAt must not be null");

        objects = List.copyOf(objects);
    }

    /** Whether this result was served from a cached dataset. */
    public boolean isCached() {
        return source == DataSource.CACHED;
    }
}