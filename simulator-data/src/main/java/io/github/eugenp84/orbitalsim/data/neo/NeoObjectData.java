package io.github.eugenp84.orbitalsim.data.neo;

import java.util.List;
import java.util.Objects;

/**
 * @param id                    stable provider identifier; never null or blank
 * @param name                  human-readable designation; never null or blank
 * @param estimatedDiameterMinMetres minimum estimated diameter in metres; positive and finite
 * @param estimatedDiameterMaxMetres maximum estimated diameter in metres; finite and not less than the minimum
 * @param potentiallyHazardous  the provider's potentially-hazardous flag
 * @param closeApproaches       close-approach events for this object, in the  order supplied; never null, defensively copied,may be empty
 *
 * @author Eugeniu PLATITA
 */
public record NeoObjectData(
        String id,
        String name,
        double estimatedDiameterMinMetres,
        double estimatedDiameterMaxMetres,
        boolean potentiallyHazardous,
        List<CloseApproach> closeApproaches)
{

    public NeoObjectData {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(closeApproaches, "closeApproaches must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (!Double.isFinite(estimatedDiameterMinMetres) || estimatedDiameterMinMetres <= 0.0) {
            throw new IllegalArgumentException("estimatedDiameterMinMetres must be positive and finite, was: " + estimatedDiameterMinMetres);
        }
        if (!Double.isFinite(estimatedDiameterMaxMetres)
                || estimatedDiameterMaxMetres < estimatedDiameterMinMetres) {
            throw new IllegalArgumentException("estimatedDiameterMaxMetres must be finite and >= the minimum ("+ estimatedDiameterMinMetres + "), was: " + estimatedDiameterMaxMetres);
        }

        // List.copyOf rejects null elements and yields an immutable list, so the published collection cannot be mutated through the caller's reference
        closeApproaches = List.copyOf(closeApproaches);
    }

    /**
     * Midpoint of the estimated diameter range, in metres.
     * <p>
     */
    public double estimatedDiameterMidpointMetres()
    {
        return estimatedDiameterMinMetres
                + 0.5 * (estimatedDiameterMaxMetres - estimatedDiameterMinMetres);
    }
}