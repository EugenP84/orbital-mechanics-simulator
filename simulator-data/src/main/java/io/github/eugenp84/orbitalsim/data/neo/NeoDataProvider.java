package io.github.eugenp84.orbitalsim.data.neo;

import java.time.LocalDate;

/**
 * Supplies near-Earth object data for a requested date range, hiding all
 * detail of how that data is obtained (ADR-007 §2).
 * <p>
 * Implementations may call a live web service, serve a cached dataset, or
 * combine the two with a fallback policy. Callers know nothing of HTTP
 * endpoints, JSON, API keys or any provider-specific representation: they
 * receive provider-neutral {@link NeoDataResult} values whose provenance is
 * always explicit.
 * <p>
 * This mirrors the strategy-boundary discipline used for {@code Integrator}
 * and {@code AccelerationModel} in {@code simulator-core}: a single small
 * interface behind which alternative implementations — including the offline
 * fallback — are interchangeable.
 */
public interface NeoDataProvider {

    /**
     * Retrieves NEO close-approach data for the inclusive date range
     * {@code [startDate, endDate]}.
     * <p>
     * The result's {@link DataSource} records whether the data was obtained
     * live or served from cache. A provider may fall back to cached data on a
     * defined external acquisition failure, but only when the cached dataset
     * covers the requested range (ADR-007 §5, §7); it must not present cached
     * data for a range it does not cover, and must not mask an internal defect
     * or a configuration failure as a cache fallback.
     *
     * @param startDate first day of the range, inclusive; never null
     * @param endDate   last day of the range, inclusive; never null and not
     *                  before {@code startDate}
     * @return the retrieved objects with explicit provenance; never null
     * @throws NullPointerException     if either date is null
     * @throws IllegalArgumentException if {@code endDate} is before
     *                                  {@code startDate}
     * @throws NeoDataUnavailableException if the data cannot be acquired live
     *         and no compatible cached dataset is available (ADR-007 §5)
     */
    NeoDataResult findCloseApproaches(LocalDate startDate, LocalDate endDate);
}