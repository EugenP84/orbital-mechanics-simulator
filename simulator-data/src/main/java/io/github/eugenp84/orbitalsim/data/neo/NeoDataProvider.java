package io.github.eugenp84.orbitalsim.data.neo;

import java.time.LocalDate;

/**
 * Supplies near-Earth object data for a requested date range, hiding all
 * detail of how that data is obtained (ADR-007 §2).
 * <p>
 * @author Eugeniu PLATITA
 */
public interface NeoDataProvider
{
    /**
     * Retrieves NEO close-approach data for the inclusive date range
     * {@code [startDate, endDate]}.
     * <p>
     *
     * @param startDate first day of the range, inclusive; never null
     * @param endDate   last day of the range, inclusive; never null and not before {@code startDate}
     * @return the retrieved objects with explicit provenance; never null
     * @throws NullPointerException     if either date is null
     * @throws IllegalArgumentException if {@code endDate} is before {@code startDate}
     * @throws NeoDataUnavailableException if the data cannot be acquired live and no compatible cached dataset is available.
     */
    NeoDataResult findCloseApproaches(LocalDate startDate, LocalDate endDate);
}